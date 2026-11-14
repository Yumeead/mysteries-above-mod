package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/*
 * The Fool's matter: paper. Where the other pathways speak in light, the Clown and the Magician
 * speak in sheets, figures and scraps — thrown, folded, torn — and the scraps fall like paper, lit
 * by the place they land in. The shared layer of the pathway, as `HolyLight.kt` is the Sun's and
 * `Doorway.kt` the Door's: a shape goes into `Effects` only when another pathway asks for it.
 */

/**
 * A sheet thrown from [from] to [to], spinning flat; it tears into scraps where it lands if it [hit]
 * something, and flutters down if not. One packet: the client flies it.
 *
 * @return the ticks it takes to get there — a blow struck by the sheet lands then, not at the throw.
 */
internal fun throwSheet(level: ServerLevel, from: Vec3, to: Vec3, hit: Boolean): Int {
    val ticks = WorldEffectPayload.Sheet.flight(from.distanceTo(to))
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, from.add(to).scale(0.5), from.distanceTo(to) / 2 + AUDIENCE),
        WorldEffectPayload.Sheet(from, to, ticks, hit),
    )
    return ticks
}

/**
 * [body] becomes paper where it stands: a paper figure in its very pose, struck through at once,
 * falling apart into scraps. The body itself is free to be somewhere else by the next tick — the
 * figure is drawn from the packet, not from the entity.
 */
internal fun paperBody(level: ServerLevel, body: LivingEntity) {
    val at = body.position()
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, at, AUDIENCE),
        WorldEffectPayload.Effigy(body.id, at, body.yBodyRot),
    )
    // The blow lands on paper: a dry tear, and pages settling.
    level.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0f, 1.6f)
    level.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.7f)
}

/**
 * [count] scraps of paper shed around [at] within [spread], drifting off at up to [speed]. Paper-white
 * unless [color] says otherwise — ash, or the red of a wound taken off a body.
 */
internal fun scraps(
    level: ServerLevel,
    at: Vec3,
    count: Int,
    spread: Double = 0.3,
    speed: Double = 0.05,
    color: RgbColor = ModParticles.PAPER,
) {
    level.sendParticles(ModParticles.scrap(color), at.x, at.y, at.z, count, spread, spread, spread, speed)
}

/**
 * A face coming off a head or going onto one (see [WorldEffectPayload.Face.Kind]); [to] matters only
 * for [WorldEffectPayload.Face.Kind.TAKE]. A [WorldEffectPayload.Face.Kind.DROP] must go out **before**
 * the borrowed skin is handed back: the client peels off the skin the head wears when the packet lands.
 */
internal fun face(level: ServerLevel, kind: WorldEffectPayload.Face.Kind, from: Entity, to: Entity = from) {
    val middle = from.position().add(to.position()).scale(0.5)
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, middle, from.distanceTo(to) / 2 + AUDIENCE),
        WorldEffectPayload.Face(kind, from.id, to.id),
    )
}

/** Who sees paper move: as far as anyone could tell a sheet from a leaf. */
private const val AUDIENCE = 48.0

/**
 * [count] scraps drawn in from the air around [at] and settling on it — paper gathering into a shape
 * in the hand: a weapon being woven, a batch of figurines being folded.
 */
internal fun gather(level: ServerLevel, at: Vec3, count: Int) {
    val random = level.random
    repeat(count) {
        val offset = Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.3, random.nextDouble() - 0.5)
            .normalize().scale(GATHER_RADIUS)
        val from = at.add(offset)
        val pull = offset.scale(-GATHER_PULL)
        // The `count = 0` form: the deltas arrive as the velocity itself.
        level.sendParticles(ModParticles.scrap(), from.x, from.y, from.z, 0, pull.x, pull.y, pull.z, 1.0)
    }
}

private const val GATHER_RADIUS = 0.9
private const val GATHER_PULL = 0.16
