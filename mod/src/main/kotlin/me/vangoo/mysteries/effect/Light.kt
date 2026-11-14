package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Light that a power **gives off**: the shapes that make a cast look like something happened in
 * the world rather than in the chat.
 *
 * Three of them, and they differ by who draws them:
 *
 * | shape | drawn by | why there |
 * |---|---|---|
 * | [beam] | the client, from one packet | a line redrawn every tick would be a stream of particle packets |
 * | [flash] | each client for itself | only the client knows where its camera looks |
 * | [flare] | the server, as a real block | real light lights the place up for everyone and stops mobs spawning |
 *
 * The glow itself is the client's: [ModParticles.SPIRIT_LIGHT] is blended additively, so where
 * the light is thick it whitens on its own — the hot core of a beam is nothing but its own overlap.
 */

/** A line of light from [from] to [to] that holds for [durationTicks] and then fades on its own. */
fun beam(level: ServerLevel, from: Vec3, to: Vec3, color: RgbColor, durationTicks: Int, width: Float) {
    require(durationTicks > 0) { "Тривалість променя мусить бути додатною: $durationTicks" }
    val middle = from.add(to).scale(0.5)
    val audience = from.distanceTo(to) / 2 + BEAM_AUDIENCE
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, middle, audience),
        WorldEffectPayload.Beam(from, to, color.packed, durationTicks, width),
    )
}

/** A flash at [at]: those within [radius] who look at it see their screen whiten by up to [strength]. */
fun flash(level: ServerLevel, at: Vec3, radius: Double, strength: Float, color: RgbColor) {
    ModChannels.WORLD_EFFECT.sendTo(
        PlayerLookup.around(level, at, radius),
        WorldEffectPayload.Flash(at, color.packed, radius.toFloat(), strength.coerceIn(0f, 1f)),
    )
}

/**
 * Real light at [at] for [durationTicks]: a vanilla `minecraft:light` block, so the place is lit
 * for everyone, the way a torch lights it. It dims to half at two thirds of the term and then goes
 * out — light that snaps off reads as a switch, not as a flare.
 *
 * Only air is lit; if [at] is taken (the point of impact is often the block itself), the one
 * above is tried. With neither free there is simply no light.
 *
 * ponytail: a server stopped between the kindling and the dousing leaves one invisible light
 * block behind — the same ceiling as `Daytime`; breaking it by hand removes it.
 */
fun flare(scheduling: Scheduling, level: ServerLevel, at: BlockPos, durationTicks: Int, brightness: Int) {
    require(durationTicks > 0) { "Тривалість світла мусить бути додатною: $durationTicks" }
    val lit = kindle(level, at, brightness) ?: kindle(level, at.above(), brightness) ?: return
    scheduling.after((durationTicks * 2 / 3).coerceAtLeast(1)) {
        if (level.getBlockState(lit).block === Blocks.LIGHT) {
            level.setBlock(lit, lightOf(brightness / 2), LIGHT_FLAGS)
        }
    }
    scheduling.after(durationTicks) { douse(level, lit) }
}

/** Places a light block if there is air there. Returns the position, or `null` if the spot is taken. */
fun kindle(level: ServerLevel, pos: BlockPos, brightness: Int): BlockPos? {
    if (!level.getBlockState(pos).isAir) return null
    return if (level.setBlock(pos, lightOf(brightness), LIGHT_FLAGS)) pos else null
}

/** Removes a light block. Someone else's block in that spot is left alone. */
fun douse(level: ServerLevel, pos: BlockPos) {
    if (level.getBlockState(pos).block === Blocks.LIGHT) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), LIGHT_FLAGS)
    }
}

/**
 * The target held out: the power breaks on a shell in the colour of the **defender's** pathway, so
 * both sides see whose will stopped it. A one-off burst, not a shape that lasts.
 */
fun ward(target: LivingEntity, color: RgbColor) {
    val level = target.level() as? ServerLevel ?: return
    val centre = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
    val radius = maxOf(target.bbWidth.toDouble(), target.bbHeight / 2.0) * WARD_SPREAD
    burst(level, centre, radius, color, WARD_POINTS, WARD_BURST)
    level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f)
}

/**
 * A shell of light that bursts out of [centre]: [points] lights on a sphere of [radius], each
 * flying straight outwards at [speed] blocks a tick. The light brakes as it flies, so it carries
 * about seven times [speed] before it burns out — a pulse that must reach `r` blocks is sent at `r / 7`.
 */
fun burst(level: ServerLevel, centre: Vec3, radius: Double, color: RgbColor, points: Int, speed: Double) {
    val light = ModParticles.light(color)
    // An even spread over the sphere (a golden-angle spiral): a random scatter clumps and leaves holes.
    for (i in 0 until points) {
        val polar = acos(1 - 2 * (i + 0.5) / points)
        val azimuth = PI * (3 - sqrt(5.0)) * i
        val out = Vec3(sin(polar) * cos(azimuth), cos(polar), sin(polar) * sin(azimuth))
        val at = centre.add(out.scale(radius))
        // `count = 0`: the deltas arrive as the exact velocity — the shell bursts outwards, not in a spread.
        level.sendParticles(light, at.x, at.y, at.z, 0, out.x, out.y, out.z, speed)
    }
}

/**
 * A ring of light running out from [centre] to [radius] at [centre]'s height, braking as it goes:
 * one shot of lights, each flying straight outwards, the way a shock wave spreads — fast, then slow.
 *
 * Not a ring redrawn every tick where the front is now: each of those generations stands where it
 * was put and burns out behind the front, and from inside the wave — where its caster stands — that
 * wake is a field of dying lights in their own face. One generation has no wake.
 *
 * The front is exact: [waveArrival] says on which tick it passes a distance, so a power that acts
 * on what the wave touches times itself by the same numbers.
 */
fun wave(level: ServerLevel, centre: Vec3, radius: Double, color: RgbColor) {
    val light = ModParticles.light(color)
    val speed = waveSpeed(radius)
    // As many lights as keep the spacing at the edge: a ring of fixed count thins out to dots there.
    val count = (TAU * radius / WAVE_SPACING).toInt().coerceAtLeast(MIN_WAVE_POINTS)
    repeat(count) { i ->
        val angle = TAU * i / count
        val x = cos(angle)
        val z = sin(angle)
        // `count = 0`: the deltas arrive as the exact velocity — straight out, level with the centre.
        level.sendParticles(light, centre.x + x * WAVE_START, centre.y, centre.z + z * WAVE_START, 0, x, 0.0, z, speed)
    }
}

/** The tick on which [wave]'s front, sent to [radius], passes [distance] from its centre. */
fun waveArrival(distance: Double, radius: Double): Int {
    val run = distance - WAVE_START
    if (run <= 0) return 0
    // The light covers v·(1 − fᵗ)/(1 − f) in t ticks; solved for t.
    val left = 1 - run * (1 - ModParticles.LIGHT_FRICTION) / waveSpeed(radius)
    if (left <= 0) return ModParticles.LIGHT_MIN_LIFETIME
    return ceil(ln(left) / ln(ModParticles.LIGHT_FRICTION)).toInt().coerceIn(0, ModParticles.LIGHT_MIN_LIFETIME)
}

/** Just fast enough that the front reaches [radius] while the shortest-lived light still burns. */
private fun waveSpeed(radius: Double): Double {
    val f = ModParticles.LIGHT_FRICTION
    return (radius - WAVE_START).coerceAtLeast(0.0) * (1 - f) / (1 - f.pow(ModParticles.LIGHT_MIN_LIFETIME))
}

private fun lightOf(brightness: Int) =
    Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness.coerceIn(0, LightBlock.MAX_LEVEL))

/** Tell the client, do not wake the neighbours: a light block affects nothing. */
private const val LIGHT_FLAGS = 2

/** How far beyond its own half-length a beam is still sent: about the distance particles are drawn at. */
private const val BEAM_AUDIENCE = 64.0

private const val WARD_POINTS = 36
private const val WARD_SPREAD = 0.85
private const val WARD_BURST = 0.05

private const val TAU = 2 * PI
/** Blocks between two lights of a wave front at its edge: dense enough to read as a line, not a dotted one. */
private const val WAVE_SPACING = 0.9
private const val MIN_WAVE_POINTS = 8
/** The ring is born a step out from its centre, not inside whoever sent it. */
private const val WAVE_START = 1.0
