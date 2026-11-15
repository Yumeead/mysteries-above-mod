package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/*
 * The Fool's other matter: threads. Spirit Vision shows a Marionettist every body's own (see
 * `ThreadSight`); these are the ones a power spins in the open, from one body to another, and
 * everyone near sees them.
 */

/**
 * A thread from [from]'s hand to [to]'s body for [ticks]: it runs out of the hand, holds, and fades,
 * riding both bodies as they move. With [pull] it goes taut and flares — the jerk of a string.
 */
internal fun thread(
    level: ServerLevel,
    from: Entity,
    to: Entity,
    color: RgbColor,
    ticks: Int,
    pull: Boolean = false,
    except: ServerPlayer? = null,
) {
    val middle = from.position().add(to.position()).scale(0.5)
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, middle, from.distanceTo(to) / 2 + AUDIENCE).filter { it !== except },
        WorldEffectPayload.Thread(from.id, to.id, color.packed, ticks, pull),
    )
}

/**
 * A ball of threads winding itself at [at] for [ticks] and closing when it is wound: a power
 * gathering itself out of a fallen puppet. One packet; the client winds it.
 *
 * ponytail: sent once, so whoever walks into range mid-wind hears the chime but sees no ball;
 * resend with the age already wound if that matters.
 */
internal fun spool(level: ServerLevel, at: Vec3, color: RgbColor, ticks: Int) {
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, at, SPOOL_AUDIENCE),
        WorldEffectPayload.Spool(at, color.packed, ticks),
    )
}

/** A ball the size of a fist is seen further than a hairline. */
private const val SPOOL_AUDIENCE = 48.0

/** As far as a thread a pixel thick could be told from the air around it. */
private const val AUDIENCE = 40.0
