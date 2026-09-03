package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.mysteries.ability.AbilityContext
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Pins [target] to the spot it stands on for [durationTicks]: `BrandOfRestraint`'s "anchor" and
 * `Punishment`'s `IMMOBILIZE` mode copy-pasted this same teleport-back loop in the plugin — one
 * helper instead of two. Drift beyond [tolerance] blocks is corrected by teleporting back to the
 * anchor, keeping the victim's own look direction; velocity is zeroed first so they do not slide
 * back out on the next tick.
 */
fun AbilityContext.rootInPlace(
    target: LivingEntity,
    durationTicks: Int,
    periodTicks: Int = 2,
    tolerance: Double = 0.2,
    boundaryParticle: ParticleOptions? = null,
    boundarySound: SoundEvent? = null,
) {
    val anchor = target.position()
    val toleranceSquared = tolerance * tolerance

    val loop = scheduling.every(periodTicks, periodTicks) {
        if (!target.isAlive) return@every
        val current = target.position()
        if (current.distanceToSqr(anchor) > toleranceSquared) {
            target.deltaMovement = Vec3.ZERO
            target.teleportTo(anchor.x, anchor.y, anchor.z)
        }
        if (boundaryParticle != null) {
            level.sendParticles(boundaryParticle, anchor.x, anchor.y + 0.5, anchor.z, 3, 0.3, 0.3, 0.3, 0.0)
        }
        if (boundarySound != null) {
            level.playSound(null, anchor.x, anchor.y, anchor.z, boundarySound, SoundSource.PLAYERS, 0.5f, 0.5f)
        }
    }
    scheduling.after(durationTicks) { loop.cancel() }
}
