package me.vangoo.mysteries.pathways.tyrant.abilities

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
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Water Curtain**.
 *
 * Raises a wall of water ahead of the Songster: it swallows other people's arrows and bolts,
 * douses flame, and grants Resistance to whoever shelters behind it.
 */
class WaterCurtain : ActiveAbility(AbilityIdentity.of("tyrant_water_curtain")) {

    override val displayName: Component = Component.literal("Водяна Завіса")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Піднімає перед вами стіну води на ${DURATION_TICKS / 20} с: вона поглинає чужі снаряди, " +
            "гасить полум'я й дає Опір II усім, хто стоїть за нею.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val forward = Vec3(caster.lookAngle.x, 0.0, caster.lookAngle.z).normalize()
        val wall = caster.position().add(forward.scale(DISTANCE))

        curtain(context, wall)
        context.level.sendParticles(ParticleTypes.SPLASH, wall.x, wall.y + 1.0, wall.z, 16, 1.0, 1.5, 0.3, 0.05)
        context.level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0f, 0.7f)
        context.level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 1.0f, 1.1f)

        val half = WIDTH / 2.0
        var tick = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            tick++
            if (tick > DURATION_TICKS) return@every handle.cancel()

            val box = AABB(wall.x - half, wall.y, wall.z - half, wall.x + half, wall.y + HEIGHT, wall.z + half)
            for (entity in context.level.getEntitiesOfClass(net.minecraft.world.entity.Entity::class.java, box)) {
                if (entity is Projectile) {
                    if (entity.owner === caster) continue
                    entity.discard()
                    context.level.sendParticles(ParticleTypes.SPLASH, entity.x, entity.y, entity.z, 12, 0.2, 0.2, 0.2, 0.0)
                    context.level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7f, 1.3f)
                } else if (entity is LivingEntity) {
                    entity.clearFire()
                }
            }

            if (tick % 20 == 0 && caster.position().distanceToSqr(wall) <= SHELTER_RANGE * SHELTER_RANGE) {
                caster.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 40, 1))
            }
        }

        return AbilityOutcome.done("Водяна завіса піднялась перед вами")
    }

    /** A standing sheet of spray for the duration — a puff-and-scatter local shape, not a reusable one yet. */
    private fun curtain(context: CastContext, wall: Vec3) {
        val half = WIDTH / 2.0
        for (i in 0 until CURTAIN_POINTS) {
            val x = wall.x + (i.toDouble() / CURTAIN_POINTS - 0.5) * WIDTH
            val y = wall.y + (i % 3) * (HEIGHT / 3.0)
            context.level.sendParticles(ParticleTypes.FALLING_WATER, x, y, wall.z, 2, 0.1, 0.3, 0.1, 0.0)
        }
    }

    private companion object {
        const val WIDTH = 5.0
        const val HEIGHT = 3.0
        const val DISTANCE = 2.5
        const val SHELTER_RANGE = 5.0
        const val DURATION_TICKS = 160
        const val COOLDOWN_SECONDS = 20
        const val CURTAIN_POINTS = 24
    }
}
