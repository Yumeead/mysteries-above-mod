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
 * WhiteTower, Seq. 6 (Scholar of Yore): **Analysis**.
 *
 * The Scholar looks at someone else's power for two seconds — and for a while can do the same.
 * Not forever: a technique taken apart stays in the head while the analysis holds, and then it is
 * forgotten.
 *
 * ## Why a loan rather than a copy
 *
 * The plugin's `Analysis` copied an ability **forever**, kept the list of what it had taken in a
 * field (`MAX_REMEMBERED_ABILITIES = 10`) and had to have a second mode — `DELETE` — so the
 * player could free up room. Together with the modes came a switching menu, and together with
 * "forever" came the question of what persistence does with that list. Both drag in phases that
 * do not exist yet (11 and 09).
 *
 * A loan drags in none: it lives exactly as long as the session and disappears with it — on
 * leaving, on a recast, on a server shutdown. There is no list, no modes, nothing to persist.
 * And as a Scholar it looks **more accurate**: breadth instead of depth — being able to do a
 * little of everything, briefly, rather than being a thief who has hoarded other people's powers
 * forever.
 *
 * ## The engine rolls the resistance, not the ability
 *
 * The plugin computed the chance itself: 20 % base, ±10 % for the Sequence difference, +5 % per
 * known recipe. Here that is [contestedTarget] — and the same roll is made by `AbilityExecutor`
 * for any power against a Beyonder. A failure eats the cooldown but not the spirituality.
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

    /** While the loan runs, a second analysis gives nothing: a player has one session. */
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
            // The reading: for two seconds the Scholar stands and studies. There is no loan yet
            // and the ticket is unsettled — a broken analysis costs nothing.
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
     * Both sides must know.
     *
     * The donor is addressed **by UUID**, and that is exactly why through [Messaging]: the loan
     * outlives both death and a dimension change, so the donor has to be looked up anew each time
     * and across all worlds. The previous revision looked them up via `self.level().server
     * .playerList` — i.e. rewrote by hand what the service already does, and silently lost the
     * line when it was the **caster** who left the game rather than the donor.
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
