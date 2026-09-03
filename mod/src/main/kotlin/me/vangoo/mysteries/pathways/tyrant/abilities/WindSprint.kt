package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind Sprint**.
 *
 * The wind catches every stride: the Wind-Blessed runs very fast and leaves a windy trail
 * behind. A cast starts it, a second cast ends it early; it also stops on its own once
 * spirituality runs out.
 */
class WindSprint : ActiveAbility(AbilityIdentity.of("tyrant_wind_sprint")) {

    override val displayName: Component = Component.literal("Вітряний біг")

    override val spiritualityCost: Int = 0

    override fun periodicCost(sequence: Sequence): Int = PERIODIC_COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вітер підхоплює ваш крок: ви біжите дуже швидко, лишаючи за собою вітряний слід.\n" +
            "▪ Витрата: $PERIODIC_COST духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Вітер стих")
        }

        val messaging = context.messaging

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { player ->
                    player.addEffect(MobEffectInstance(MobEffects.SPEED, GUST_TICKS, SPEED_AMPLIFIER, true, false, true))
                    trail(player)
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Вітер стих: духовність скінчилась"))
                }
            } finally {
                owner?.removeEffect(MobEffects.SPEED)
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0f, 1.5f,
        )
        return AbilityOutcome.done("Вітряний біг активний")
    }

    private fun trail(player: net.minecraft.server.level.ServerPlayer) {
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        val feet = player.position()
        level.sendParticles(ParticleTypes.SMALL_GUST, feet.x, feet.y + 0.1, feet.z, 3, 0.2, 0.05, 0.2, 0.01)
    }

    private companion object {
        const val PERIODIC_COST = 15
        const val COOLDOWN_SECONDS = 3
        const val SPEED_AMPLIFIER = 2
        /** Over two seconds so the effect does not lapse between upkeep beats. */
        const val GUST_TICKS = 2 * TICKS_PER_SECOND
    }
}
