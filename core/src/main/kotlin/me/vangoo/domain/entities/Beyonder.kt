package me.vangoo.domain.entities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.abilities.CastAuthorization
import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.abilities.CastTicket
import me.vangoo.domain.services.AbilityTransformer
import me.vangoo.domain.services.MasteryProgressionCalculator
import me.vangoo.domain.services.SanityPenaltyRoller
import me.vangoo.domain.services.SpiritualityCalculator
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.SanityPenalty
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Потойбічний: шлях, Послідовність, засвоєння, духовність, глузд і список здібностей.
 *
 * **Живе на серверному потоці й тільки там.** У плагіні кожен мутатор був
 * `synchronized`, бо `BukkitRunnable` міг прийти з асинхронного пулу. У Fabric
 * тік-луп один, а планувальник фази 04 повертає роботу на нього ж — тож замків тут
 * навмисно немає. Дістатись сюди з іншого потоку — помилка виклику, а не гонка,
 * яку слід глушити.
 */
class Beyonder private constructor(
    val playerId: UUID,
    pathway: Pathway,
    sequence: Sequence,
    private val spiritualityCalculator: SpiritualityCalculator,
    private val random: Random,
) {

    var pathway: Pathway = pathway
        private set

    var sequence: Sequence = sequence
        private set

    var mastery: Mastery = Mastery.zero()
        private set

    var sanityLoss: SanityLoss = SanityLoss.none()
        private set

    var spirituality: Spirituality = Spirituality.empty(
        spiritualityCalculator.calculateMaximumSpirituality(sequence, Mastery.zero()),
    )
        private set

    private var pathwayAbilities: List<AbilitySpec> = emptyList()
    private val offPathwayAbilities = LinkedHashSet<AbilitySpec>()

    init {
        reloadAbilities()
    }

    constructor(
        playerId: UUID,
        sequence: Sequence,
        pathway: Pathway,
        random: Random = Random.Default,
    ) : this(playerId, pathway, sequence, SpiritualityCalculator(), random)

    val sequenceLevel: Int get() = sequence.level
    val maxSpirituality: Int get() = spirituality.maximum

    /** Здібності шляху плюс позашляхові: вкрадені, подаровані, одноразові. */
    val abilities: List<AbilitySpec> get() = pathwayAbilities + offPathwayAbilities

    fun abilityBy(identity: AbilityIdentity): AbilitySpec? =
        abilities.firstOrNull { it.identity == identity }

    fun has(spec: AbilitySpec): Boolean = abilities.any { it.identity == spec.identity }

    // ── Каст: дозвіл → квиток → розрахунок ──────────────────────────────────────

    /**
     * Перевіряє право на каст і резервує його ціну. Кулдаун тут не перевіряється —
     * він живий таймер і належить моду; мод питає його **до** цього виклику.
     */
    fun authorize(spec: AbilitySpec): CastAuthorization {
        if (!has(spec)) return CastAuthorization.Refused(CastRefusal.NotOwned)
        if (spec.type != AbilityType.ACTIVE) {
            return CastAuthorization.Refused(CastRefusal.NotCastable)
        }
        val cost = spec.spiritualityCost
        if (!spirituality.hasSufficient(cost)) {
            return CastAuthorization.Refused(
                CastRefusal.InsufficientSpirituality(cost, spirituality.current),
            )
        }
        return CastAuthorization.Granted(CastTicket(spec, cost, playerId))
    }

    /**
     * Гасить квиток: списує духовність, нараховує засвоєння, кидає на штраф глузду.
     * Викликається рівно один раз і тільки коли ефект справді стався — здібність, що
     * чекала вводу й не дочекалась, просто не гасить квиток, і гравець не платить.
     */
    fun settle(ticket: CastTicket): CastReceipt {
        require(ticket.casterId == playerId) { "Квиток виданий іншому Потойбічному" }
        ticket.consume()

        spirituality = spirituality.decrement(ticket.cost)

        val masteryGain = MasteryProgressionCalculator.calculateMasteryGain(ticket.cost, sequence)
        if (masteryGain > 0) {
            mastery = mastery.add(masteryGain)
            updateMaximumSpirituality()
        }

        // Робота на порожньому баку розхитує розум.
        if (spirituality.isCritical()) increaseSanityLoss(2)

        val penalty = SanityPenaltyRoller.roll(sanityLoss, sequence, maxSpirituality, random)
        // Втрату духовності від штрафу знімаємо тут. У плагіні це робив лише шлях
        // відкладених здібностей, а звичайний — ні, тож один і той самий штраф важив
        // по-різному залежно від того, як здібність написана.
        if (penalty.type == SanityPenalty.PenaltyType.SPIRITUALITY_LOSS) {
            spirituality = spirituality.decrement(penalty.amount)
        }

        return CastReceipt(
            spentSpirituality = ticket.cost,
            masteryGained = masteryGain,
            cooldownSeconds = ticket.spec.cooldownSeconds(sequence),
            penalty = penalty,
        )
    }

    // ── Просування шляхом ───────────────────────────────────────────────────────

    fun canAdvance(): Boolean = mastery.canAdvance() && sequence.canAdvance()

    fun canConsumePotion(potionPathway: Pathway, potionSequence: Sequence): Boolean =
        potionPathway.group == pathway.group &&
            sequence.level == potionSequence.level + 1 &&
            mastery.canAdvance()

    fun advance() {
        check(canAdvance()) { "Cannot advance: sequence=$sequence, mastery=$mastery" }
        sequence = sequence.advance()
        mastery = mastery.reset()
        updateMaximumSpirituality()
        reloadAbilities()
    }

    /**
     * Слабшає на одну Послідовність: Характеристику вже вилучено (Закон Конвергенції),
     * тож живий оригінал втрачає відповідну частку сили. На Посл. 9 — no-op.
     */
    fun demote() {
        if (!sequence.canDemote()) return
        sequence = sequence.demote()
        mastery = mastery.reset()
        updateMaximumSpirituality()
        pathwayAbilities = emptyList()
        reloadAbilities()
    }

    /** Піднесення зі зміною шляху всередині групи — доступне лише з Посл. 9. */
    fun advanceWithPathwayChange(newPathway: Pathway) {
        require(newPathway.group == pathway.group) {
            "New pathway must be from the same group: current=${pathway.group}, new=${newPathway.group}"
        }
        check(sequence.level >= PATHWAY_CHANGE_MIN_SEQUENCE) {
            "Pathway changes are only allowed at sequence $PATHWAY_CHANGE_MIN_SEQUENCE " +
                "or higher. Current sequence: ${sequence.level}"
        }
        check(canAdvance()) { "Cannot advance: sequence=$sequence, mastery=$mastery" }

        pathway = newPathway
        sequence = sequence.advance()
        mastery = mastery.reset()
        updateMaximumSpirituality()
        pathwayAbilities = emptyList()
        reloadAbilities()
    }

    // ── Позашляхові здібності ───────────────────────────────────────────────────

    /** false, якщо здібність із такою ідентичністю вже є. */
    fun grantOffPathway(spec: AbilitySpec): Boolean {
        if (has(spec)) return false
        offPathwayAbilities += spec
        return true
    }

    fun remove(identity: AbilityIdentity): Boolean {
        val fromOff = offPathwayAbilities.removeIf { it.identity == identity }
        val remaining = pathwayAbilities.filterNot { it.identity == identity }
        val fromPathway = remaining.size != pathwayAbilities.size
        pathwayAbilities = remaining
        return fromOff || fromPathway
    }

    fun clearOffPathway() {
        offPathwayAbilities.clear()
    }

    // ── Глузд і духовність ──────────────────────────────────────────────────────

    fun increaseSanityLoss(amount: Int) {
        sanityLoss = sanityLoss.increase(resist(amount))
    }

    fun decreaseSanityLoss(amount: Int) {
        sanityLoss = sanityLoss.decrease(amount)
    }

    /**
     * Пасивки-опору послаблюють **усяку** вхідну втрату глузду — і від чужих
     * здібностей, і від власного перенапруження. Береться найсильніший опір, а не
     * добуток: кілька пасивок не мають складатись в імунітет.
     */
    private fun resist(amount: Int): Int {
        if (amount <= 0) return amount
        val multiplier = abilities.minOfOrNull { it.sanityLossMultiplier } ?: 1.0
        return if (multiplier >= 1.0) amount else max(1, (amount * multiplier).roundToInt())
    }

    /**
     * Утримання сили, що триває в часі: тогл, канал, жива сесія.
     *
     * Списує рівно [amount] або не списує нічого — вичерпана сесія має згаснути, а не
     * працювати в борг. У плагіні цього методу не було, і кожна з 22 сесій писала ту саму
     * трійку «дістати Потойбічного → звірити `current() < cost` → `decrement`» власноруч;
     * саме тому вони й розійшлись у дрібницях (хтось звіряв `<`, хтось `<=`).
     *
     * Це не каст: квитка немає, засвоєння не нараховується, штраф глузду не кидається.
     * Ціну утримання здібність оголошує через [AbilitySpec.periodicCost].
     */
    fun payUpkeep(amount: Int): Boolean {
        require(amount >= 0) { "Ціна утримання не може бути від'ємною: $amount" }
        if (amount == 0) return true
        if (!spirituality.hasSufficient(amount)) return false
        spirituality = spirituality.decrement(amount)
        return true
    }

    fun regenerateSpirituality() {
        if (spirituality.isFull()) return
        val rate = spiritualityCalculator.calculateRegenerationRate(sequence, mastery)
        spirituality = spirituality.regenerate(rate)
    }

    fun restoreAfterSleep() {
        decreaseSanityLoss(5)
        if (!spirituality.isFull()) {
            spirituality = Spirituality.of(maxSpirituality / 2, maxSpirituality)
        }
    }

    /** Пряма зміна — для ефектів, що живлять або висушують духовність повз каст. */
    fun setSpirituality(value: Spirituality) {
        require(value.maximum == spirituality.maximum) {
            "Spirituality maximum is derived from sequence and mastery, got ${value.maximum} " +
                "instead of ${spirituality.maximum}"
        }
        spirituality = value
    }

    fun setMastery(value: Mastery) {
        mastery = value
        updateMaximumSpirituality()
    }

    // ── Підміна особистості (маріонетки) ────────────────────────────────────────

    fun snapshot(): BeyonderSnapshot = BeyonderSnapshot(
        pathway = pathway,
        sequence = sequence,
        mastery = mastery,
        spirituality = spirituality,
        sanityLoss = sanityLoss,
        pathwayAbilities = pathwayAbilities,
        offPathwayAbilities = offPathwayAbilities.toList(),
    )

    /** Вселитись у чужу особистість; знімок беруть **до** виклику. */
    fun possess(newPathway: Pathway, newSequence: Sequence) {
        pathway = newPathway
        sequence = newSequence
        mastery = Mastery.zero()
        pathwayAbilities = emptyList()
        reloadAbilities()
        updateMaximumSpirituality()
    }

    fun restoreFrom(snapshot: BeyonderSnapshot) {
        pathway = snapshot.pathway
        sequence = snapshot.sequence
        mastery = snapshot.mastery
        sanityLoss = snapshot.sanityLoss
        pathwayAbilities = snapshot.pathwayAbilities
        offPathwayAbilities.clear()
        offPathwayAbilities += snapshot.offPathwayAbilities
        spirituality = snapshot.spirituality
    }

    // ── Внутрішнє ───────────────────────────────────────────────────────────────

    /** Перезбирає список здібностей зі шляху; позашляхові не чіпає. */
    fun reloadAbilities() {
        val fromPathway = sequence.getAllSequencesUpToCurrent()
            .flatMap { pathway.abilitiesFor(it) }
        pathwayAbilities = AbilityTransformer.transform(pathwayAbilities, fromPathway)
    }

    private fun updateMaximumSpirituality() {
        val newMax = spiritualityCalculator.calculateMaximumSpirituality(sequence, mastery)
        spirituality = spirituality.withNewMaximum(newMax)
    }

    override fun toString(): String =
        "Beyonder($playerId, ${pathway.name} $sequence, mastery=$mastery, spirituality=$spirituality)"

    companion object {
        /** Змінити шлях можна лише поки ти ще Посл. 9 — далі особистість уже склалась. */
        const val PATHWAY_CHANGE_MIN_SEQUENCE: Int = 9

        /**
         * Відновлення зі сховища (фаза 09). Формат вільний — збережень плагіна не існує,
         * тож тут лише те, що не виводиться зі шляху й Послідовності.
         */
        fun restore(
            playerId: UUID,
            pathway: Pathway,
            sequence: Sequence,
            mastery: Mastery,
            currentSpirituality: Int,
            sanityLoss: SanityLoss,
            offPathwayAbilities: Collection<AbilitySpec> = emptyList(),
            random: Random = Random.Default,
        ): Beyonder = Beyonder(playerId, sequence, pathway, random).apply {
            this.mastery = mastery
            this.sanityLoss = sanityLoss
            this.offPathwayAbilities += offPathwayAbilities
            updateMaximumSpirituality()
            this.spirituality = Spirituality.of(
                min(max(0, currentSpirituality), this.spirituality.maximum),
                this.spirituality.maximum,
            )
        }
    }
}

/**
 * Незмінний зріз особистості Потойбічного. Живе тільки в пам'яті: маріонетка
 * повертає оригінал наприкінці, зберігати нічого.
 */
data class BeyonderSnapshot(
    val pathway: Pathway,
    val sequence: Sequence,
    val mastery: Mastery,
    val spirituality: Spirituality,
    val sanityLoss: SanityLoss,
    val pathwayAbilities: List<AbilitySpec>,
    val offPathwayAbilities: List<AbilitySpec>,
)
