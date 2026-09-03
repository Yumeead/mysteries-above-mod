package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Pressure Wave**.
 *
 * The Wind-Blessed releases a circular squall: compressed air flies out in every direction,
 * shoving and hurting everything around while a "hurricane cloak" spins around them for a moment.
 */
class PressureWave : ActiveAbility(AbilityIdentity.of("tyrant_pressure_wave")) {

    override val displayName: Component = Component.literal("Владар Вітру")

    override val spiritualityCost: Int = 55

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Круговий шквал стиснутого повітря розлітається довкола вас.\n" +
            "▪ Шкода: ${damage(sequence)}, з відкиданням\n" +
            "▪ Радіус ${RADIUS.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val center = caster.position()
        val damage = damage(context.sequence)

        vortex(context, center.add(0.0, 1.0, 0.0))
        context.level.playSound(
            null, center.x, center.y, center.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.4f, 0.9f,
        )

        val box = caster.boundingBox.inflate(RADIUS)
        for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster }) {
            if (target.position().distanceTo(center) > RADIUS) continue

            target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
            var push = target.position().subtract(center)
            push = Vec3(push.x, 0.0, push.z)
            push = if (push.lengthSqr() < 1.0e-4) Vec3(0.0, 0.0, 1.0) else push.normalize()
            push = push.scale(KNOCKBACK)
            target.deltaMovement = target.deltaMovement.add(push.x, 0.45, push.z)
            target.hurtMarked = true
        }

        return AbilityOutcome.done("Шквал розлітається довкола вас")
    }

    /** A spiral shroud around the body at eye height — a burst at the moment of the cast, not a lasting shape. */
    private fun vortex(context: CastContext, center: Vec3) {
        for (i in 0 until VORTEX_POINTS) {
            val angle = TAU * i / VORTEX_POINTS
            val radius = VORTEX_RADIUS * (i.toDouble() / VORTEX_POINTS)
            val height = VORTEX_HEIGHT * (i.toDouble() / VORTEX_POINTS)
            context.level.sendParticles(
                ParticleTypes.SMALL_GUST,
                center.x + radius * cos(angle), center.y + height, center.z + radius * sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 6
        const val RADIUS = 12.0
        const val KNOCKBACK = 1.4
        const val COOLDOWN_SECONDS = 14
        const val VORTEX_POINTS = 16
        const val VORTEX_RADIUS = 1.2
        const val VORTEX_HEIGHT = 1.0
        val TAU = (2.0 * Math.PI)
    }
}
