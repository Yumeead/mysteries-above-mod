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
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind Blades**.
 *
 * The Wind-Blessed compresses air into an invisible cutting gust — a line struck along the line
 * of sight. Sneaking releases a three-blade fan across a wider arc instead of one line.
 */
class Windblades : ActiveAbility(AbilityIdentity.of("tyrant_windblades")) {

    override val displayName: Component = Component.literal("Леза Вітру")

    override val spiritualityCost: Int = 40

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стискає повітря у невидиме різальне лезо на лінії зору.\n" +
            "▪ Шкода: ${damage(sequence)}\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл.\n" +
            "▪ Присівши — віяло з трьох лез ширшим сектором",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val eye = caster.eyePosition
        val forward = caster.lookAngle.normalize()
        val damage = damage(context.sequence)

        val eye0 = caster.eyePosition
        context.level.playSound(
            null, eye0.x, eye0.y, eye0.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2f, 1.0f,
        )

        if (caster.isShiftKeyDown) {
            slash(context, eye, rotateYaw(forward, -FAN_SPREAD_DEGREES), damage)
            slash(context, eye, forward, damage)
            slash(context, eye, rotateYaw(forward, FAN_SPREAD_DEGREES), damage)
        } else {
            slash(context, eye, forward, damage)
        }

        return AbilityOutcome.done("Леза вітру розсікли повітря")
    }

    /** One blade: the nearest living target within a narrow cone along [direction], if any. */
    private fun slash(context: CastContext, eye: Vec3, direction: Vec3, damage: Int) {
        val caster = context.caster
        val target = context.level
            .getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(MAX_RANGE)) {
                it !== caster && it.eyePosition.subtract(eye).let { toIt ->
                    toIt.length() <= MAX_RANGE && toIt.normalize().dot(direction) >= BLADE_COS
                }
            }
            .minByOrNull { it.eyePosition.distanceToSqr(eye) }

        val reach = target?.let { eye.distanceTo(it.eyePosition) } ?: MAX_RANGE
        val end = eye.add(direction.scale(reach))
        beam(context, eye, end)

        if (target != null) {
            target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
            val push = Vec3(direction.x, 0.2, direction.z).normalize().scale(KNOCKBACK)
            target.deltaMovement = target.deltaMovement.add(push)
            target.hurtMarked = true
            val hit = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
            context.level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0.0, 0.0, 0.0, 0.0)
            context.level.playSound(
                null, hit.x, hit.y, hit.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f,
            )
        }
    }

    private fun beam(context: CastContext, from: Vec3, to: Vec3) {
        val steps = (from.distanceTo(to) / 0.5).toInt().coerceAtLeast(1)
        val step = to.subtract(from).scale(1.0 / steps)
        var point = from
        repeat(steps) {
            point = point.add(step)
            context.level.sendParticles(ParticleTypes.SMALL_GUST, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
    }

    private fun rotateYaw(v: Vec3, degrees: Double): Vec3 {
        val rad = Math.toRadians(degrees)
        val cos = cos(rad)
        val sin = sin(rad)
        return Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos)
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 5
        const val MAX_RANGE = 30.0
        const val KNOCKBACK = 0.6
        const val COOLDOWN_SECONDS = 10
        const val FAN_SPREAD_DEGREES = 20.0
        /** ~5deg half-angle around the ray — a blade, not a cone. */
        const val BLADE_COS = 0.996
    }
}
