package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.mysteries.ability.AbilityContext
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * A circular area, alive for a fixed number of ticks, that knows who stands inside it.
 *
 * Justiciar's plugin ancestor wrote this shape three times over — `ProhibitionZone`,
 * `SpawnBanZone`, `JurisdictionSession` — each its own centre/radius/expiry check plus its own
 * boundary-particle `BukkitTask`. One primitive replaces all three; what a zone actually *does*
 * while it stands (buff its owner, ban an action, purge a mob type) stays with the ability that
 * raised it, exactly as [me.vangoo.mysteries.ability.context.Effects] only draws shapes and never
 * decides what they mean.
 */
class Zone(val center: Vec3, private val level: ServerLevel, val radius: Double) {

    fun contains(pos: Vec3): Boolean = pos.distanceToSqr(center) <= radius * radius

    fun playersInside(): List<ServerPlayer> = level.players().filter { contains(it.position()) }

    fun drawBoundary(particle: ParticleOptions, points: Int = 18) {
        for (i in 0 until points) {
            val angle = 2.0 * Math.PI * i / points
            val x = center.x + radius * cos(angle)
            val z = center.z + radius * sin(angle)
            level.sendParticles(particle, x, center.y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}

/**
 * Raises a [Zone] centred on the caster: draws its boundary every [boundaryPeriodTicks] (which
 * doubles as [onTick]'s cadence — Justiciar's zones check membership at most a couple of times a
 * second, so one scheduled job covers both), then calls [onExpire] once [durationTicks] run out.
 */
fun AbilityContext.raiseZone(
    radius: Double,
    durationTicks: Int,
    boundaryParticle: ParticleOptions,
    boundaryPeriodTicks: Int = 30,
    onTick: (Zone) -> Unit = {},
    onExpire: (Zone) -> Unit = {},
): Zone {
    val zone = Zone(origin, level, radius)
    val loop = scheduling.every(boundaryPeriodTicks, 0) {
        zone.drawBoundary(boundaryParticle)
        onTick(zone)
    }
    scheduling.after(durationTicks) {
        loop.cancel()
        onExpire(zone)
    }
    return zone
}
