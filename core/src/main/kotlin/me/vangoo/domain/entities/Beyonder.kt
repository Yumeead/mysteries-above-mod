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
 * A Beyonder: pathway, Sequence, mastery, spirituality, sanity and the list of abilities.
 *
 * **Lives on the server thread and only there.** In the plugin every mutator was
 * `synchronized`, because a `BukkitRunnable` could arrive from an async pool. In Fabric the
 * tick loop is single and the phase 04 scheduler returns work onto it — so there are
 * deliberately no locks here. Reaching this from another thread is a call-site bug, not a
 * race to be muffled.
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

    /** The pathway's abilities plus off-pathway ones: stolen, granted, single-use. */
    val abilities: List<AbilitySpec> get() = pathwayAbilities + offPathwayAbilities

    fun abilityBy(identity: AbilityIdentity): AbilitySpec? =
        abilities.firstOrNull { it.identity == identity }

    fun has(spec: AbilitySpec): Boolean = abilities.any { it.identity == spec.identity }

    // ── Cast: authorize → ticket → settle ───────────────────────────────────────

    /**
     * Checks the right to cast and reserves its price. The cooldown is not checked here —
     * it is a live timer and belongs to the mod; the mod asks it **before** this call.
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
     * Settles the ticket: spends spirituality, accrues mastery, rolls for the sanity penalty.
     * Called exactly once and only when the effect actually happened — an ability that waited
     * for input and never got it simply does not settle the ticket, and the player pays nothing.
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

        // Working on an empty tank rattles the mind.
        if (spirituality.isCritical()) increaseSanityLoss(2)

        val penalty = SanityPenaltyRoller.roll(sanityLoss, sequence, maxSpirituality, random)
        // The spirituality loss from the penalty is taken here. In the plugin only the
        // deferred-ability path did this and the ordinary one did not, so the very same
        // penalty weighed differently depending on how the ability was written.
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

    // ── Advancing along the pathway ─────────────────────────────────────────────

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
     * Falls one Sequence back: the Characteristic has already been extracted (the Law of
     * Convergence), so the living original loses the matching share of power. At Seq. 9 it is
     * a no-op.
     */
    fun demote() {
        if (!sequence.canDemote()) return
        sequence = sequence.demote()
        mastery = mastery.reset()
        updateMaximumSpirituality()
        pathwayAbilities = emptyList()
        reloadAbilities()
    }

    /** Ascension with a pathway change inside the group — available only from Seq. 9. */
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

    // ── Off-pathway abilities ───────────────────────────────────────────────────

    /** false if an ability with this identity is already present. */
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

    // ── Sanity and spirituality ─────────────────────────────────────────────────

    fun increaseSanityLoss(amount: Int) {
        sanityLoss = sanityLoss.increase(resist(amount))
    }

    fun decreaseSanityLoss(amount: Int) {
        sanityLoss = sanityLoss.decrease(amount)
    }

    /**
     * Resistance passives weaken **any** incoming sanity loss — both from other people's
     * abilities and from one's own overexertion. The strongest resistance is taken, not the
     * product: several passives must not add up to immunity.
     */
    private fun resist(amount: Int): Int {
        if (amount <= 0) return amount
        val multiplier = abilities.minOfOrNull { it.sanityLossMultiplier } ?: 1.0
        return if (multiplier >= 1.0) amount else max(1, (amount * multiplier).roundToInt())
    }

    /**
     * Upkeep of a power that lasts in time: a toggle, a channel, a live session.
     *
     * Spends exactly [amount] or spends nothing — an exhausted session must go out, not run
     * on credit. The plugin had no such method, and each of its 22 sessions wrote the same
     * trio of "fetch the Beyonder → check `current() < cost` → `decrement`" by hand; that is
     * exactly why they drifted apart in details (some checked `<`, some `<=`).
     *
     * This is not a cast: there is no ticket, no mastery is accrued, no sanity penalty is
     * rolled. The ability declares the upkeep price through [AbilitySpec.periodicCost].
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

    /** A direct change — for effects that feed or drain spirituality outside a cast. */
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

    // ── Identity substitution (marionettes) ─────────────────────────────────────

    fun snapshot(): BeyonderSnapshot = BeyonderSnapshot(
        pathway = pathway,
        sequence = sequence,
        mastery = mastery,
        spirituality = spirituality,
        sanityLoss = sanityLoss,
        pathwayAbilities = pathwayAbilities,
        offPathwayAbilities = offPathwayAbilities.toList(),
    )

    /** Move into another identity; the snapshot is taken **before** the call. */
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

    // ── Internals ───────────────────────────────────────────────────────────────

    /** Rebuilds the ability list from the pathway; off-pathway ones are left alone. */
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
        /** The pathway can only be changed while still at Seq. 9 — beyond that the identity has set. */
        const val PATHWAY_CHANGE_MIN_SEQUENCE: Int = 9

        /**
         * Restoring from storage (phase 09). The format is free — the plugin's saves do not
         * exist, so only what cannot be derived from the pathway and the Sequence is here.
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
 * An immutable slice of a Beyonder's identity. Lives in memory only: the marionette gives the
 * original back at the end, there is nothing to persist.
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
