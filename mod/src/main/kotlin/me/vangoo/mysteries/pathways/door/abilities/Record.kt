package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.AbilityCast
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Door, Посл. 6 (Писар): **Запис здібності**.
 *
 * Писар бере на приціл Потойбічного й чекає. Щойно той застосує силу — Писар запише її
 * собі, **на один раз**.
 *
 * ## Чекання замість меню, і чому це те саме
 *
 * Плагін відкривав список голів гравців поблизу, а вже потім десять секунд чекав чужого
 * касту. Список тут не потрібен: ціль однаково мусить бути поруч, а «поруч і під
 * прицілом» — це вже вибір, зроблений руками. Той самий хід, що в `Analysis` Білої Вежі.
 *
 * ## Один запис за раз
 *
 * Плагін тримав до двадцяти записаних сил, рахував ліміт від Послідовності й засвоєння
 * (22 рядки) і мусив мати режим видалення, щоб гравець міг звільнити місце. Тут запис
 * один: новий витісняє попередній. Зникли ліміт, режим і питання «що з цим списком
 * робить збереження» — а сила лишилась тією самою: чужий прийом, який ти маєш витратити.
 *
 * ## Квиток гаситься на записі, не на касті
 *
 * Каст лише **починає** очікування, тож повертається [AbilityOutcome.Awaiting], а
 * духовність списується тоді, коли сила справді лягла в записник. Ціль, що так нічого й
 * не застосувала, не коштує Писареві нічого.
 */
class Record : ActiveAbility(AbilityIdentity.of("door_record")) {

    override val displayName: Component = Component.literal("Запис здібності")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.WRITABLE_BOOK)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви берете Потойбічного на приціл і чекаєте його сили.\n" +
            "▪ Ціль: Потойбічний у ${RANGE.toInt()} бл.; чекання ${WATCH_TICKS / TICKS_PER_SECOND} с\n" +
            "▪ Записана сила лишається до першого використання\n" +
            "▪ Новий запис витісняє попередній\n" +
            "▪ Чим вища Послідовність сили, тим імовірніший провал",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть погляд на Потойбічного")
        val donor = context.beyonders.of(target)
            ?: return AbilityOutcome.failed("${target.name.string} — не Потойбічний")

        val pending = context.cast
        val scribe = context.beyonder
        val messaging = context.messaging
        val owner = context.caster.uuid
        val donorId = donor.playerId
        val donorSequence = donor.sequenceLevel
        val mySequence = scribe.sequenceLevel

        // Підписка мусить зняти себе першим влучанням, тобто обробник потребує посилання
        // на неї, а вона — на нього. Розв'язується `lateinit`, як у `bodyAura`.
        lateinit var watch: Subscription
        watch = context.events.on<AbilityCast>(WATCH_TICKS) { event ->
            if (event.caster.uuid != donorId) return@on
            val spec = event.spec
            if (!isRecordable(spec)) {
                messaging.actionBar(owner, Component.literal("Ця сила надто абстрактна для запису"))
                return@on
            }
            watch.cancel()

            val chance = SequenceBasedSuccessChance(mySequence, donorSequence)
            if (!chance.rollSuccess()) {
                messaging.actionBar(
                    owner,
                    Component.literal("Запис зірвався (шанс ${chance.getFormattedChance()})"),
                )
                return@on
            }

            // Витісняємо попередній запис: місце в записнику одне.
            scribe.abilities.filterIsInstance<OneShot>().forEach { scribe.remove(it.identity) }
            val recorded = OneShot(spec as ActiveAbility)
            if (!scribe.grantOffPathway(recorded)) return@on

            pending.complete()
            messaging.actionBar(owner, Component.literal("✓ Записано: ${nameOf(spec)} (на один раз)"))
            (event.caster.level().getPlayerByUUID(owner) as? ServerPlayer)?.let { scribeSelf ->
                BeyonderStatus.pushRoster(scribeSelf)
                scribeSelf.level().playSound(
                    null, scribeSelf.x, scribeSelf.y, scribeSelf.z,
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.5f,
                )
            }
        }

        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            target.x, target.y + target.bbHeight * 0.7, target.z,
            12, 0.3, 0.3, 0.3, 0.0,
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 1.0f, 2.0f,
        )
        return AbilityOutcome.Awaiting(
            Component.literal("Чекаєте сили: ${target.name.string}"),
        )
    }

    /**
     * Записати можна тільки те, що **робиться** — тобто активну силу, і не рефлексивну.
     *
     * Заборона на `Аналіз`, `Створення заклинань` і сам `Запис` плагінна, і причина в
     * усіх трьох одна: вони не мають власного ефекту, а лише поводяться з чужими силами.
     * Записаний `Запис` дав би нескінченну сходинку, записаний `Аналіз` — другу таку саму.
     */
    private fun isRecordable(spec: AbilitySpec): Boolean =
        spec is ActiveAbility && spec.identity.id !in FORBIDDEN

    private fun nameOf(spec: AbilitySpec): String =
        (spec as? Ability)?.displayName?.string ?: spec.identity.id

    /**
     * Чужа сила, яка зникає після першого ж застосування.
     *
     * Обгортка, а не копія: ціна, перепочинок, опис і сам ефект лишаються донорові, а
     * записник додає рівно одне — знімає себе, щойно спрацював. Ідентичність своя, бо
     * інакше `grantOffPathway` побачив би її як ту саму силу, що в донора.
     */
    private class OneShot(private val inner: ActiveAbility) :
        ActiveAbility(AbilityIdentity.of("door_recorded_${inner.identity.id}")) {

        override val displayName: Component =
            Component.literal("${inner.displayName.string} (запис)")

        override val spiritualityCost: Int get() = inner.spiritualityCost

        override fun cooldownSeconds(sequence: Sequence): Int = inner.cooldownSeconds(sequence)

        override fun periodicCost(sequence: Sequence): Int = inner.periodicCost(sequence)

        override val icon: ItemStack get() = ItemStack(Items.WRITTEN_BOOK)

        override fun description(sequence: Sequence): Component = Component.literal(
            "Записана чужа сила. Зникне одразу після використання.\n" +
                inner.description(sequence).string,
        )

        override fun cast(context: CastContext): AbilityOutcome {
            val outcome = inner.cast(context)
            // Зірваний каст сторінку не витрачає: платити нема за що, стирати нема чого.
            if (outcome is AbilityOutcome.Failed) return outcome
            context.beyonder.remove(identity)
            BeyonderStatus.pushRoster(context.caster)
            context.messaging.actionBar(
                context.caster,
                Component.literal("Сторінку витрачено: ${inner.displayName.string}"),
            )
            return outcome
        }
    }

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 60
        const val WATCH_TICKS = 10 * TICKS_PER_SECOND
        val FORBIDDEN = setOf(
            "door_record",
            "whitetower_analysis",
            "whitetower_spellcasting",
        )
    }
}
