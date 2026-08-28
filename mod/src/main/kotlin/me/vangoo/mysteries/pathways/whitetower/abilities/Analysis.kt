package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity

/**
 * WhiteTower, Посл. 6 (Ерудит): **Аналіз**.
 *
 * Ерудит дивиться на чужу силу дві секунди — і на якийсь час уміє те саме. Не назавжди:
 * розібраний прийом тримається в голові, поки тримається аналіз, а тоді забувається.
 *
 * ## Чому позика, а не копія
 *
 * Плагінна `Analysis` копіювала здібність **назавжди**, тримала список украденого в
 * полі (`MAX_REMEMBERED_ABILITIES = 10`) і мусила мати другий режим — `DELETE`, — щоб
 * гравець міг звільнити місце. Разом із режимами приїжджало меню перемикання, а разом
 * із «назавжди» — питання, що з цим списком робить збереження. Обидва тягнуть за собою
 * фази, яких ще немає (11 і 09).
 *
 * Позика не тягне жодної: вона живе рівно стільки, скільки живе сесія, і зникає разом
 * із нею — при виході, при перекасті, при зупинці сервера. Списку немає, режимів немає,
 * зберігати нічого. І Ерудитом це виглядає **точніше**: широта замість глибини — те, що
 * вмієш усе потроху й ненадовго, а не крадій, який назбирав чужого назавжди.
 *
 * ## Опір кидає рушій, а не здібність
 *
 * Плагін рахував шанс сам: 20 % бази, ±10 % за різницю Послідовностей, +5 % за кожен
 * відомий рецепт. Тут це [contestedTarget] — і той самий кидок робить `AbilityExecutor`
 * для будь-якої сили проти Потойбічного. Провал з'їдає кулдаун, але не духовність.
 */
class Analysis : ActiveAbility(AbilityIdentity.of("whitetower_analysis")) {

    override val displayName: Component = Component.literal("Аналіз")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви розбираєте чужу силу на очах у власника — і ${borrowTicks(sequence) / TICKS_PER_SECOND} с " +
            "володієте нею самі.\n" +
            "▪ Ціль: Потойбічний у межах ${RANGE.toInt()} бл.\n" +
            "▪ Розбір триває ${SCAN_TICKS / TICKS_PER_SECOND} с — на цей час ви відкриті\n" +
            "▪ Чим вища Послідовність цілі, тим імовірніше вона встоїть",
    )

    /** Поки триває позика, другий аналіз нічого не дасть: сесія в гравця одна. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Нема чого аналізувати: наведіть погляд на Потойбічного")
        val donor = context.beyonders.of(target)
            ?: return AbilityOutcome.failed("${target.name.string} — не Потойбічний")

        val borrowed = donor.abilities
            .firstOrNull { it.type == AbilityType.ACTIVE && !context.beyonder.has(it) }
            ?: return AbilityOutcome.failed("У ${target.name.string} немає активної сили, якої б ви не знали")

        val pending = context.cast
        val ticks = borrowTicks(context.sequence)
        val targetId = target.uuid
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val messaging = context.messaging

        context.session {
            // Розбір: дві секунди Ерудит стоїть і читає. Позики ще немає, квиток
            // непогашений — зірваний аналіз не коштує нічого.
            repeat(SCAN_TICKS / SCAN_PERIOD_TICKS) {
                val self = owner ?: return@session
                val victim = self.level().getEntityInAnyDimension(targetId) ?: return@session
                self.level().sendParticles(
                    mote,
                    victim.x, victim.y + victim.bbHeight * 0.7, victim.z,
                    2, 0.3, 0.3, 0.3, 0.0,
                )
                waitTicks(SCAN_PERIOD_TICKS)
            }

            val self = beyonder ?: return@session
            if (!self.grantOffPathway(borrowed)) return@session
            pending.complete()
            try {
                announce(messaging, owner, targetId, borrowed, granted = true)
                waitTicks(ticks)
            } finally {
                self.remove(borrowed.identity)
                announce(messaging, owner, targetId, borrowed, granted = false)
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.4f,
        )
        return AbilityOutcome.Awaiting(Component.literal("Розбираєте силу: ${target.name.string}"))
    }

    /**
     * Обидві сторони мусять знати.
     *
     * Донор адресується **за UUID**, і саме тому через [Messaging]: позика переживає і
     * смерть, і перехід між вимірами, тож шукати його треба щоразу наново й по всіх
     * світах. Попередня редакція шукала його `self.level().server.playerList` — тобто
     * вручну переписувала те, що сервіс уже вміє, і мовчки губила рядок, коли з гри
     * виходив **кастер**, а не донор.
     */
    private fun announce(
        messaging: Messaging,
        self: ServerPlayer?,
        targetId: java.util.UUID,
        spec: AbilitySpec,
        granted: Boolean,
    ) {
        val name = (spec as? Ability)?.displayName?.string ?: spec.identity.id
        self?.let { BeyonderStatus.pushRoster(it) }

        val toSelf = if (granted) "Ви опанували: $name" else "Аналіз згас: $name забуто"
        self?.let { messaging.actionBar(it, Component.literal(toSelf)) }

        val toDonor = if (granted) "Вашу силу розібрано: $name" else "Розбір вашої сили розсіявся"
        messaging.actionBar(targetId, Component.literal(toDonor))
    }

    private fun borrowTicks(sequence: Sequence): Int =
        scale(BASE_BORROW_SECONDS, sequence, SequenceScaler.ScalingStrategy.STRONG) * TICKS_PER_SECOND

    private companion object {
        const val RANGE = 20.0
        const val COOLDOWN_SECONDS = 180
        const val BASE_BORROW_SECONDS = 20
        const val SCAN_TICKS = 2 * TICKS_PER_SECOND
        const val SCAN_PERIOD_TICKS = 4
    }
}
