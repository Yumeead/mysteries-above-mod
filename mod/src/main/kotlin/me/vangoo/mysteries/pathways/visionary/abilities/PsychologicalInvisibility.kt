package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Visionary, Seq. 6 (Hypnotist): **Psychological Invisibility**.
 *
 * Slips out of everyone's perception for as long as the Hypnotist keeps paying — and drops the
 * instant either side draws blood.
 *
 * ## A session, not a toggle — same reasoning as Sun's `Blessing`
 *
 * The power must go out **on its own** the moment damage passes either way, and a
 * `ToggleablePassiveAbility`'s switch lives outside the ability, unreachable from inside. A
 * session decides for itself when to stop.
 *
 * ## Invisible to everyone, by the precedent Door's `Flash` already set
 *
 * The plugin hid the caster from every other player individually (`hidePlayerFromTarget`) and
 * had to remember to show them all again on every exit path. `MobEffects.INVISIBILITY` is the
 * same effect for the whole world in one call, and it cannot be forgotten to remove — the
 * session's `finally` always runs.
 *
 * ## Breaking on damage is an external cancel, not a flag the loop polls
 *
 * The `IncomingDamage` subscription calls [me.vangoo.mysteries.ability.context.Scheduling.stop]
 * directly — the exact mechanism a recast or a disconnect already uses to end a session from the
 * outside. That leaves `sustain` untouched and still doing its own upkeep bookkeeping correctly,
 * rather than a hand-rolled loop re-implementing it.
 */
class PsychologicalInvisibility : ActiveAbility(AbilityIdentity.of("visionary_psychological_invisibility")) {

    override val displayName: Component = Component.literal("Психологічна невидимість")

    override val spiritualityCost: Int = 0

    override fun periodicCost(sequence: Sequence): Int = COST_PER_SECOND

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви зникаєте зі сприйняття. Будь-яка шкода — завдана чи отримана — миттєво знімає ефект.\n" +
            "▪ Витрата: $COST_PER_SECOND духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Невидимість вимкнено")
        }

        val casterId = context.caster.uuid
        val messaging = context.messaging
        val scheduling = context.scheduling
        val abilityIdentity = identity
        val subscription = context.events.on<IncomingDamage>(EVENT_FOREVER) { damage ->
            if (damage.victim.uuid == casterId || damage.attacker?.uuid == casterId) {
                scheduling.stop(casterId, abilityIdentity)
            }
        }

        context.session {
            try {
                val end = sustain(periodTicks = 1, upkeep = periodicCost, upkeepPeriodTicks = TICKS_PER_SECOND) { player ->
                    player.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, REFRESH_TICKS, 0, true, false, true))
                }
                val reason = when (end) {
                    SessionEnd.EXHAUSTED -> "виснаження"
                    else -> null
                }
                if (reason != null) messaging.actionBar(casterId, Component.literal("✗ Невидимість зникла · $reason"))
            } finally {
                subscription.cancel()
                owner?.removeEffect(MobEffects.INVISIBILITY)
                owner?.let { player ->
                    player.level().playSound(
                        null, player.x, player.y, player.z,
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 0.6f,
                    )
                }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.8f,
        )
        messaging.actionBar(context.caster, Component.literal("✦ Ви зникли зі сприйняття світу"))
        return AbilityOutcome.Done()
    }

    private companion object {
        const val COST_PER_SECOND = 10
        const val COOLDOWN_SECONDS = 5

        /** A little longer than the tick period, so the effect never blinks between refreshes. */
        const val REFRESH_TICKS = 3 * TICKS_PER_SECOND
    }
}
