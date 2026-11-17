package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/*
 * The Justiciar's matter: law set in iron. Everything here stands along the axes of the world, appears
 * with a blow and goes out in steps; colour is state, not pathway — gold while the law is pronounced,
 * iron while it stands. The shapes are drawn by the client (`client/render/Edicts.kt`, `Lashes.kt`);
 * the server only says what and to whom, and keeps the ticks both sides must agree on.
 */

/** An aura is seen across a room; a chain on an ankle, a little less. */
private const val AUDIENCE = 40.0

/** An aura kindles round [owner], rises a step at each knock, flares on tick [SEAL_TICKS] and burns out. */
internal fun seal(level: ServerLevel, owner: LivingEntity, color: RgbColor) =
    send(level, owner, Edict(EdictKind.SEAL, owner.id, color.packed, Edict.SEAL_TICKS + Edict.AURA_FADE_TICKS))

/**
 * Hoops dropped down [target]'s body, snapped shut, cooled to iron, and broken again after [ticks] —
 * and, with [crown], a small one on the head after them.
 */
internal fun bonds(level: ServerLevel, target: LivingEntity, color: RgbColor, ticks: Int, crown: Boolean) {
    send(level, target, Edict(EdictKind.BONDS, target.id, color.packed, ticks))
    if (crown) send(level, target, Edict(EdictKind.CROWN, target.id, color.packed, ticks))
}

/** Any other edict of [kind] on [owner], for [ticks]: the shapes are the client's. */
internal fun edict(level: ServerLevel, owner: LivingEntity, kind: EdictKind, color: RgbColor, ticks: Int) =
    send(level, owner, Edict(kind, owner.id, color.packed, ticks))

/** A [shape] of line from [from] to [to], riding both. */
internal fun lash(level: ServerLevel, from: Entity, to: Entity, shape: WorldEffectPayload.Lash.Shape, color: RgbColor) {
    val middle = from.position().add(to.position()).scale(0.5)
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, middle, from.distanceTo(to) / 2 + AUDIENCE),
        WorldEffectPayload.Lash(from.id, to.id, shape, color.packed),
    )
}

/** The dry knock of the law pronounced, from where [at] is. */
internal fun gavel(level: ServerLevel, at: Vec3, volume: Float = 1f, pitch: Float = 1f) =
    level.playSound(null, at.x, at.y, at.z, ModSounds.GAVEL, SoundSource.PLAYERS, volume, pitch)

/** Iron taken up by one more link. */
internal fun ratchet(level: ServerLevel, at: Vec3, pitch: Float = 1f) =
    level.playSound(null, at.x, at.y, at.z, ModSounds.RATCHET, SoundSource.PLAYERS, 1f, pitch)

/** Iron meeting iron. */
internal fun clang(level: ServerLevel, at: Vec3, pitch: Float = 1f) =
    level.playSound(null, at.x, at.y, at.z, ModSounds.CLANG, SoundSource.PLAYERS, 1f, pitch)

private fun send(level: ServerLevel, around: Entity, payload: WorldEffectPayload) =
    ModChannels.WORLD_EFFECT.sendTo(PlayerLookup.around(level, around.position(), AUDIENCE), payload)
