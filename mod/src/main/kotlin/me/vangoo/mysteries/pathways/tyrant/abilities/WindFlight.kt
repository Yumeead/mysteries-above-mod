package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
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
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.GameType

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind Flight**.
 *
 * The wind lifts the Wind-Blessed off the ground. In **Flight** it holds them fully aloft; a
 * sneaking cast switches to **Glide**, which only softens the fall. A cast with nothing running
 * starts Flight; a plain cast while airborne ends it.
 *
 * ## Two vanilla mechanics instead of a hand-flown velocity loop
 *
 * The plugin drove both modes itself: a repeating task nudging the player's velocity to fake
 * lift, and a second one damping it into a fall. Flight is exactly what `abilities.mayfly` was
 * built for — the same switch creative mode uses, so movement stays server-authoritative and
 * feels native rather than shoved. Glide is exactly vanilla `SLOW_FALLING`. Neither needs a
 * velocity tick of the mod's own.
 */
class WindFlight : ActiveAbility(AbilityIdentity.of("tyrant_wind_flight")) {

    override val displayName: Component = Component.literal("Політ на вітрі")

    /** The cast is free: the price is the upkeep, and grounding yourself must cost nothing. */
    override val spiritualityCost: Int = 0

    /** The stronger the Beyonder, the cheaper the wind holds them: same duration, less spirituality. */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вітер підіймає вас у небо.\n" +
            "▪ Політ тримає в повітрі повністю; Ковзання лише лагідно опускає\n" +
            "▪ Shift+каст перемикає режим; Витрата: ${periodicCost(sequence)} духовності/с\n" +
            "▪ Повторний каст без Shift вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            if (context.caster.isShiftKeyDown) {
                switchTo(context.caster, !glide.contains(context.caster.uuid))
                return AbilityOutcome.Awaiting(Component.literal("Режим змінено"))
            }
            context.stopSession()
            return AbilityOutcome.done("Вітер стих")
        }

        val messaging = context.messaging
        val casterId = context.caster.uuid
        if (context.caster.isShiftKeyDown) glide.add(casterId) else glide.remove(casterId)

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { player ->
                    hold(player, glide.contains(casterId))
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Вітер стих: духовність скінчилась"))
                }
            } finally {
                glide.remove(casterId)
                owner?.let { grounded(it) }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
        return AbilityOutcome.done("Політ на вітрі активний")
    }

    private fun switchTo(player: ServerPlayer, toGlide: Boolean) {
        val owner = player.uuid
        if (toGlide) glide.add(owner) else glide.remove(owner)
        if (toGlide) grounded(player)
        player.playSound(SoundEvents.PHANTOM_FLAP, 1.0f, 1.4f)
    }

    private fun hold(player: ServerPlayer, glide: Boolean) {
        if (glide) {
            player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, true, false, true))
        } else if (player.gameMode() == GameType.SURVIVAL) {
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
        }
    }

    private fun grounded(player: ServerPlayer) {
        player.removeEffect(MobEffects.SLOW_FALLING)
        if (player.gameMode() == GameType.SURVIVAL) {
            player.abilities.mayfly = false
            player.abilities.flying = false
            player.onUpdateAbilities()
        }
    }

    private companion object {
        /** Seq. 6 ~= 35/s, Seq. 5 ~= 32/s, Seq. 0 ~= 22/s. */
        const val BASE_PERIODIC_COST = 50
        const val COOLDOWN_SECONDS = 3
        /** Over ten seconds so the effect does not lapse between upkeep beats. */
        const val GLIDE_TICKS = 10 * TICKS_PER_SECOND

        /** Which players currently prefer Glide over Flight. Not backed by any vanilla state. */
        val glide = mutableSetOf<java.util.UUID>()
    }
}
