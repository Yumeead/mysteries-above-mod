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
 * Visionary, Seq. 6 (Hypnotist): **Dragon Scale**.
 *
 * A manifestation of draconic scale: Resistance II and Fire Resistance II. A straight port —
 * the plugin gated it above Seq. 6, which the roster already enforces by not handing this
 * ability to anyone weaker.
 */
class DragonScale : ActiveAbility(AbilityIdentity.of("visionary_dragon_scale")) {

    override val displayName: Component = Component.literal("Луска дракона")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int =
        scale(BASE_COOLDOWN_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK).coerceAtLeast(MIN_COOLDOWN_SECONDS)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Маніфестація драконячої луски. Дає Опір II та Вогнестійкість II на " +
            "${duration(sequence) / TICKS_PER_SECOND} с.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val durationTicks = duration(context.sequence)

        context.caster.addEffect(MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 1))
        context.caster.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, durationTicks, 0))

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.9f, 0.6f,
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1f, 0.5f,
        )
        context.level.sendParticles(
            ParticleTypes.FLAME, context.origin.x, context.origin.y + 1.0, context.origin.z, 40, 0.6, 1.0, 0.6, 0.0,
        )
        context.level.sendParticles(
            ParticleTypes.LAVA, context.origin.x, context.origin.y + 1.2, context.origin.z, 12, 0.5, 1.0, 0.5, 0.0,
        )

        return AbilityOutcome.done("🐉 Луска дракона вкриває ваше тіло")
    }

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * TICKS_PER_SECOND

    private companion object {
        const val BASE_DURATION_SECONDS = 21
        const val BASE_COST = 150
        const val BASE_COOLDOWN_SECONDS = 75
        const val MIN_COOLDOWN_SECONDS = 60
    }
}
