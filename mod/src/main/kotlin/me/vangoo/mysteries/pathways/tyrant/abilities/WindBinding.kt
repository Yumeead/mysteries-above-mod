package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Wind Binding**.
 *
 * A spiral vortex wraps around the target and chains them in place: their legs will not carry
 * them, a jump will not leave the ground, and the wind smothers any motion the moment it starts.
 */
class WindBinding : ActiveAbility(AbilityIdentity.of("tyrant_wind_binding")) {

    override val displayName: Component = Component.literal("Вітряні Пута")

    override val spiritualityCost: Int = 150

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Спіральний вихор обвиває ціль на лінії зору й приковує її до місця: ані кроку, ані стрибка.\n" +
            "▪ Тривалість ${duration(sequence) / 20.0} с\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл.",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(MAX_RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі на лінії зору")
        val duration = duration(context.sequence)

        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, duration, SLOWNESS_AMPLIFIER))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, duration, JUMP_BLOCK_AMPLIFIER))
        context.effects.bodyAura(target, duration, counterFlow = ParticleTypes.SMALL_GUST)

        val base = target.position()
        context.level.playSound(null, base.x, base.y, base.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2f, 0.7f)
        context.level.playSound(null, base.x, base.y, base.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.9f, 0.6f)

        var tick = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            tick++
            if (tick > duration || target.isRemoved) return@every handle.cancel()
            target.deltaMovement = Vec3(0.0, if (target.onGround()) 0.0 else -0.1, 0.0)
        }

        return AbilityOutcome.done("Вітряні пута приковують ${target.name.string}")
    }

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DURATION_TICKS = 100
        const val MAX_RANGE = 20.0
        const val SLOWNESS_AMPLIFIER = 5
        const val JUMP_BLOCK_AMPLIFIER = 128
        const val COOLDOWN_SECONDS = 30
    }
}
