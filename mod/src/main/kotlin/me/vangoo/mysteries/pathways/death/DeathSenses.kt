package me.vangoo.mysteries.pathways.death

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * What only a Death Beyonder sees, and how the pathway tells the dead from the living.
 *
 * Souls, bodies, the smoke of a hidden spirit body — none of that may be drawn for everyone, or
 * the whole server would learn where someone died. The plugin needed a dedicated
 * `playSoulWisp(viewerId, …)` for it; vanilla already has the addressed form of `sendParticles`,
 * so these are three thin shapes over it rather than a service.
 */
internal val DEATH_COLOR: RgbColor = PathwayBranding.liquidOf(Retinue.DEATH)

/** Undead by vanilla's own tag — modded undead count without a list of ours. */
internal fun Entity.isUndead(): Boolean = type.builtInRegistryHolder().`is`(EntityTypeTags.UNDEAD)

/** Dead by nature: undead or a spirit. Rampage would join them, the day Rampage exists. */
internal fun Entity.isDeadSoul(): Boolean = isUndead() || Spirits.isSpirit(this)

/** Particles that only [viewer] receives. */
internal fun ServerPlayer.sees(
    particle: ParticleOptions,
    at: Vec3,
    count: Int,
    spread: Double = 0.0,
    spreadY: Double = spread,
    speed: Double = 0.0,
) {
    level().sendParticles(this, particle, true, false, at.x, at.y, at.z, count, spread, spreadY, spread, speed)
}

/** A lingering soul: a thin rising wisp at the place of death. */
internal fun ServerPlayer.seesSoul(at: Vec3) {
    sees(ParticleTypes.SOUL, at.add(0.0, 0.6, 0.0), 2, 0.12, 0.4, 0.01)
    sees(ModParticles.glyph(DEATH_COLOR.shade(0.25)), at.add(0.0, 1.5, 0.0), 1)
}

/** A body: ash low over the ground. */
internal fun ServerPlayer.seesCorpse(at: Vec3) {
    sees(ParticleTypes.ASH, at.add(0.0, 0.2, 0.0), 6, 0.4, 0.05)
    sees(ParticleTypes.SMOKE, at.add(0.0, 0.15, 0.0), 1, 0.2, 0.02)
}
