package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.flare
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.DoorKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

/**
 * The Door pathway's shared layer: **the door itself**.
 *
 * Three ask for it — `DoorOpening` (both ends of a passage through a wall), `TravellersDoor` (the
 * entrance into space and the way back out) and `Blink` (a door at either end of the passage the
 * Traveller rushes through).
 *
 * ## One packet, built by the client
 *
 * The door is a single [WorldEffectPayload.Door], and each client builds it frame by frame as a
 * function of its age (`client/render/Doors`): a crack runs round the arch, the leaves swing open
 * on the jambs and stop dead, a corridor recedes behind them deeper than the wall is thick, and
 * once they shut a hairline scar goes out pixel by pixel. Its phases are [DoorKind]'s, shared, so
 * the server knows to the tick when a door stands open.
 *
 * The first revisions drew the door from the server out of glyph particles: about 57 packets every
 * two ticks per door, and an outline of dots that could never become a surface, let alone swing.
 *
 * ## What stays on the server
 *
 * What must be the same for everyone: the real light in front of the threshold ([flare]) and the
 * sound — the Door's amethyst chime while it opens and a stone click when it shuts.
 */
internal fun doorway(
    scheduling: Scheduling,
    level: ServerLevel,
    /** The middle of the threshold, on the floor; for a door in a wall, on the wall's face. */
    footing: Vec3,
    /** The horizontal direction **into** the door — into the wall, or the way one steps through. */
    facing: Vec3,
    /** How long it stands open, once open. */
    holdTicks: Int,
    color: RgbColor,
    kind: DoorKind = DoorKind.FREE,
    /** How far its corridor runs in; 0 — nothing behind it. */
    depth: Float = 0f,
    /** The glow at the corridor's end: the colour of where the door leads. */
    far: RgbColor = color.shade(FAR_SHADE),
) {
    require(holdTicks >= 0) { "Двері не можуть стояти від'ємний час: $holdTicks" }
    val into = horizontal(facing)
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, footing, AUDIENCE),
        WorldEffectPayload.Door(footing, atan2(into.z, into.x).toFloat(), kind, holdTicks, depth, color.packed, far.packed),
    )

    val shut = kind.shut(holdTicks)
    // A door that only blinks past lights nothing worth a block.
    if (shut >= LIT_FROM) {
        flare(scheduling, level, BlockPos.containing(footing.add(into.scale(-0.5)).add(0.0, 1.0, 0.0)), shut, DOOR_LIGHT)
    }
    if (kind.cut == 0) return

    // Three notes while it opens, rising: they mark that the line has set off, not every step of it.
    for ((i, tick) in (0 until kind.opened step CHIME_EVERY).withIndex()) {
        later(scheduling, tick) { sound(level, footing, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, OPEN_PITCH + i * OPEN_PITCH_STEP) }
    }
    later(scheduling, shut) { sound(level, footing, SoundEvents.STONE_BUTTON_CLICK_OFF, 0.7f, CLOSE_PITCH) }
}

/**
 * The passage a blink folds through space from [from] to [to]: it builds itself out over [delay]
 * ticks and is rushed along for [flight]. One packet; the client draws it.
 */
internal fun passage(level: ServerLevel, from: Vec3, to: Vec3, color: RgbColor, delay: Int, flight: Int) {
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, from.add(to).scale(0.5), from.distanceTo(to) / 2 + AUDIENCE),
        WorldEffectPayload.Passage(from, to, color.packed, delay, flight),
    )
}

/** The horizontal component of a direction; the degenerate case faces north. */
internal fun horizontal(direction: Vec3): Vec3 = Vec3(direction.x, 0.0, direction.z).let {
    if (it.lengthSqr() < 1.0e-6) Vec3(0.0, 0.0, 1.0) else it.normalize()
}

private fun later(scheduling: Scheduling, ticks: Int, block: () -> Unit) {
    if (ticks <= 0) block() else scheduling.after(ticks, block)
}

private fun sound(level: ServerLevel, at: Vec3, event: SoundEvent, volume: Float, pitch: Float) =
    level.playSound(null, at.x, at.y + 1.0, at.z, event, SoundSource.PLAYERS, volume, pitch)

/** Who is sent a door: anyone near enough to make it out. */
private const val AUDIENCE = 64.0
/** The real light in front of an open door: cold, a little less than a torch. */
private const val DOOR_LIGHT = 7
/** A door that stands shorter than this lights nothing. */
private const val LIT_FROM = 20
/** The corridor's end glows a lighter tone of the rim unless told where it leads. */
private const val FAR_SHADE = 0.45
private const val CHIME_EVERY = 3
private const val OPEN_PITCH = 1.15f
private const val OPEN_PITCH_STEP = 0.06f
private const val CLOSE_PITCH = 0.7f
