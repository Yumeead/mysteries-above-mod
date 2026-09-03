package me.vangoo.mysteries.pathways.sun.abilities

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
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Seq. 8 (Light Seeker): **Night Vision**.
 *
 * Two miniature suns in the eyes: darkness does not exist for the Seeker — neither the ordinary
 * kind nor the kind that is sent.
 *
 * ## Immunity to `DARKNESS` is the whole point
 *
 * Night vision itself is what any potion gives. What a potion does not give is resistance to the
 * vanilla `DARKNESS` (a Warden's blinding pulses): it is applied on top and blacks out the screen
 * even for someone who can see in the dark. The tick removes it every second, and **that** is
 * what tells the power from the bottle.
 */
class NightVision : ActiveAbility(AbilityIdentity.of("sun_night_vision")) {

    override val displayName: Component = Component.literal("Нічне бачення")

    /** The cast is free: the price is the upkeep, and putting the eyes out must cost nothing. */
    override val spiritualityCost: Int = 0

    /**
     * The upkeep grows with the Sequence — and it must, because **regeneration** grows with it
     * too: 2 points/s at Seq. 8 against 6 at Seq. 5 (and 9 at full mastery). The flat price taken
     * from the plugin turned out at Seq. 5 to be below the regeneration, i.e. the power became
     * free and the spirituality bar crept back up to the ceiling while it was being sustained.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Два мініатюрних сонця у ваших очах.\n" +
            "▪ Нічне бачення, поки тримаєте\n" +
            "▪ Темрява вас не засліплює — навіть наслана\n" +
            "▪ Витрата: ${periodicCost(sequence)} духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Сонця в очах згасли")
        }

        val messaging = context.messaging

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { priest ->
                    priest.addEffect(
                        MobEffectInstance(MobEffects.NIGHT_VISION, VISION_TICKS, 0, true, false, true),
                    )
                    priest.removeEffect(MobEffects.DARKNESS)
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Сонця згасли: духовність скінчилась"))
                }
            } finally {
                owner?.removeEffect(MobEffects.NIGHT_VISION)
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 2.0f,
        )
        return AbilityOutcome.done("Темрява для вас скінчилась")
    }

    private companion object {
        const val BASE_PERIODIC_COST = 9
        const val COOLDOWN_SECONDS = 3
        /**
         * Over twenty seconds — so the client does not flicker with darkening: vanilla starts
         * blinking night vision over the last ten seconds of its duration.
         */
        const val VISION_TICKS = 25 * TICKS_PER_SECOND
    }
}
