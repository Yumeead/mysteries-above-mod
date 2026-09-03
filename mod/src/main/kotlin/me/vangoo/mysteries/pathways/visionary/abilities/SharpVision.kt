package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Visionary, Seq. 9 (Spectator): **Sharp Vision**.
 *
 * Night vision for the caster and a public outline on everything living nearby — the same
 * vanilla-glow tradeoff `GoodMemory` and Sun's `EvilDetection` already made, here without even a
 * ledger: the burst is one-shot, so there is nothing to track between casts.
 */
class SharpVision : ActiveAbility(AbilityIdentity.of("visionary_sharp_vision")) {

    override val displayName: Component = Component.literal("Гострий зір")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int =
        scale(BASE_COOLDOWN_SECONDS, sequence, SequenceScaler.ScalingStrategy.STRONG)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Нічне бачення собі й підсвічує все живе поруч на ${duration(sequence) / TICKS_PER_SECOND} с.\n" +
            "▪ Радіус ${range(sequence).toInt()} бл.\n" +
            "▪ Відкат ${cooldownSeconds(sequence)} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val radius = range(context.sequence)
        val durationTicks = duration(context.sequence)
        val nightVisionAmplifier = ((9 - context.sequence.level) / 3).coerceAtMost(2)

        context.level.sendParticles(
            ParticleTypes.END_ROD, context.origin.x, context.origin.y + 1.0, context.origin.z,
            30, 0.5, 0.5, 0.5, 0.0,
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.5f,
        )

        context.caster.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, nightVisionAmplifier))

        val nearby = context.targeting.livingNear(radius)
        nearby.forEach { it.addEffect(MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, true, false, true)) }

        return AbilityOutcome.done(
            if (nearby.isEmpty()) "Гострий зір: нікого поблизу" else "Гострий зір: підсвічено ${nearby.size}",
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.WEAK).toDouble()

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * TICKS_PER_SECOND

    private companion object {
        const val BASE_RANGE = 100
        const val BASE_DURATION_SECONDS = 30
        const val BASE_COOLDOWN_SECONDS = 40
    }
}
