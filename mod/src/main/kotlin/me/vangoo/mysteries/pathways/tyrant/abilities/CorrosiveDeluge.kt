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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.HitResult

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Corrosive Deluge**.
 *
 * A huge sphere of water gathers over the chosen spot and bursts into a corrosive downpour:
 * everything alive in the area corrodes until it runs out, and any flame goes out with it.
 */
class CorrosiveDeluge : ActiveAbility(AbilityIdentity.of("tyrant_corrosive_deluge")) {

    override val displayName: Component = Component.literal("Їдка Злива")

    override val spiritualityCost: Int = 150

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Водяна сфера лускає над обраним місцем їдкою зливою.\n" +
            "▪ Шкода: ${damage(sequence)} щосекунди, радіус ${RADIUS.toInt()} бл., ${DURATION_TICKS / 20} с\n" +
            "▪ Гасить полум'я в зоні",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val hit = context.caster.pick(MAX_RANGE, 1.0f, false)
        if (hit.type != HitResult.Type.BLOCK) {
            return AbilityOutcome.failed("Немає точки для зливи — цільтесь у землю чи блок")
        }
        val center = (hit as net.minecraft.world.phys.BlockHitResult).blockPos.let {
            net.minecraft.world.phys.Vec3(it.x + 0.5, it.y + 1.0, it.z + 0.5)
        }

        val damage = damage(context.sequence)

        context.level.sendParticles(ParticleTypes.DRIPPING_WATER, center.x, center.y + 5.0, center.z, 20, 2.0, 1.0, 2.0, 0.02)
        context.level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 20, RADIUS, 0.3, RADIUS, 0.05)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.4f, 0.6f)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.PLAYERS, 1.4f, 0.8f)

        var tick = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            tick++
            if (tick > DURATION_TICKS) return@every handle.cancel()

            context.level.sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 4.0, center.z, 12, RADIUS / 2, 0.3, RADIUS / 2, 0.0)
            if (tick % PULSE_TICKS != 0) return@every

            val box = net.minecraft.world.phys.AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
            for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== context.caster }) {
                target.clearFire()
                target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
                val at = target.position()
                context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 8, 0.3, 0.4, 0.3, 0.0)
            }
            context.level.playSound(null, center.x, center.y, center.z, SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.9f, 0.7f)
        }

        return AbilityOutcome.done("Їдка злива накриває зону")
    }

    private fun damage(sequence: Sequence): Int = scale(BASE_TICK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_TICK_DAMAGE = 3
        const val RADIUS = 6.0
        const val MAX_RANGE = 25.0
        const val DURATION_TICKS = 160
        const val PULSE_TICKS = 20
        const val COOLDOWN_SECONDS = 25
    }
}
