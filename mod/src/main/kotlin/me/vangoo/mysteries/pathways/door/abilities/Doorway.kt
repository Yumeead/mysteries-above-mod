package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The Door pathway's shared layer: **the door itself**.
 *
 * Two ask for it — `DoorOpening` (both ends of a passage through a wall) and `TravellersDoor`
 * (the entrance into space).
 *
 * ## What was removed from it and why
 *
 * The second revision tried to make the door noticeable by adding layers: a frame two layers
 * deep, a vortex of three arms, vanilla `PORTAL`s falling towards the centre, `END_ROD` along the
 * threshold, `REVERSE_PORTAL` from under it. Noticeable it became; stylish it did not:
 *
 * | what was removed | why |
 * |---|---|
 * | the second frame layer | two planes of one blue read as **two** frames, not as thickness |
 * | the three-armed vortex | something spun in the pane that pointed at nothing |
 * | `PORTAL` and `REVERSE_PORTAL` | vanilla purple has nothing in common with the pathway colour |
 * | `END_ROD` along the threshold | white, likewise: one bright blot from another palette spoils the rest |
 *
 * What is left is **one arch** and **one pane**, both in the pathway's colour.
 *
 * ## A door does not appear, it is **drawn**
 *
 * The second revision scaled a finished arch from zero: the door grew out of the floor like a
 * slider. Here it is different — the line **flows** from both corners of the threshold up the
 * posts and on along the arc until the halves meet at the top. It is the same duration, but it
 * reads as "something is drawing a door" rather than "something is getting bigger".
 *
 * The [progress] curve is `1 − (1−t)²`: a fast start, a slow meeting at the top. Closing is
 * mirrored, so the door fades from the threshold and the top holds the longest.
 *
 * ## The colour is a palette, not one blue
 *
 * `RgbColor.shades` gives five lightnesses of **one** hue, and the arch lays them out by height:
 * darker near the threshold, lighter at the top. This is not decoration — it is what makes a flat
 * outline three-dimensional, and it works the same for any of the 22 pathways.
 *
 * ## Both the frame and the pane are glyphs, not sparks
 *
 * Both are drawn with [ModParticles.SPIRIT_GLYPH]: a fixed short lifetime, zero physics. The
 * motion that is visible — a wave of lightness along the frame, a slow settling in the pane — is
 * computed by the code itself, frame by frame, rather than by particle physics: `SPIRIT_MOTE`
 * will not do here, because the vanilla constructor with a non-zero velocity mixes ±0.4
 * blocks/tick of random spread into any vector (CLAUDE.md, trap 6), and a controlled fall would
 * drown in it.
 *
 * ⚠ A glyph latches onto a player if the packet carries a non-zero offset (see
 * `SpiritGlyphParticle`). The door places it with the `count = 1` form and zero deltas — it is a
 * shape of the world.
 */
internal fun doorway(
    scheduling: Scheduling,
    level: ServerLevel,
    /** The point on the floor under the door. */
    footing: Vec3,
    /** The horizontal direction "through the door"; the length does not matter. */
    facing: Vec3,
    durationTicks: Int,
    color: RgbColor,
) {
    require(durationTicks > 0) { "Двері мусять простояти хоч тік: $durationTicks" }

    val forward = horizontal(facing)
    // The vector along the pane: the frame is drawn in the plane perpendicular to the passage.
    val across = forward.cross(UP).normalize()
    val palette = color.shades(SHADES, SHADE_SPREAD).map(ModParticles::glyph)
    // The pane is a glyph too, not a spark: a mote's own physics mixes ±0.4 blocks/tick of random
    // spread into any velocity it is given (that is what read as "the particles are flying off" —
    // CLAUDE.md, trap 6), so the fall is drawn by the very layout of the points in [pane] rather
    // than by particle physics.
    val veil = ModParticles.glyph(color.shade(VEIL_SHADE))

    val frames = max(1, durationTicks / PERIOD_TICKS)
    var step = 0

    lateinit var handle: TaskHandle
    handle = scheduling.every(PERIOD_TICKS, delayTicks = 0) {
        if (step >= frames) return@every handle.cancel()

        val drawn = progress(step, frames)
        outline(level, palette, footing, across, drawn, step)
        // A soft threshold instead of a hard "there/not there": the pane heats up and fades
        // together with the outline rather than snapping on the moment drawn crossed VEIL_FROM.
        val veilStrength = ((drawn - VEIL_FROM) / (1.0 - VEIL_FROM)).coerceIn(0.0, 1.0)
        if (veilStrength > 0.0) pane(level, veil, footing, across, step, veilStrength)

        // Three notes for the whole opening: the chime marks that the line has set off, it does
        // not accompany every step of it.
        if (step < OPEN_FRAMES && step % CHIME_EVERY == 0) {
            level.playSound(
                null, footing.x, footing.y + 1.0, footing.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.45f, OPEN_PITCH + step * OPEN_PITCH_STEP,
            )
        }

        step++
    }
}

/** The horizontal component of a direction; the degenerate case faces north. */
internal fun horizontal(direction: Vec3): Vec3 = Vec3(direction.x, 0.0, direction.z).let {
    if (it.lengthSqr() < 1.0e-6) Vec3(0.0, 0.0, 1.0) else it.normalize()
}

/**
 * How much of the outline is already drawn: 0 — nothing, 1 — the halves have met at the top.
 *
 * `1 − (1−t)²` on opening: the line breaks away from the threshold fast and finishes the top
 * slowly. On closing it is the same curve in reverse, so the top disappears last — the door folds
 * back into where it rose from.
 */
private fun progress(step: Int, frames: Int): Double {
    val left = frames - step
    val t = when {
        step < OPEN_FRAMES -> (step + 1).toDouble() / OPEN_FRAMES
        left <= CLOSE_FRAMES -> left.toDouble() / CLOSE_FRAMES
        else -> 1.0
    }.coerceIn(0.0, 1.0)
    val inverse = 1.0 - t
    return 1.0 - inverse * inverse
}

/**
 * The outline: the posts, then the arc — both halves flowing towards each other.
 *
 * The points are laid out by **outline length**, not by height: otherwise the arc, where the
 * step in height is small but the step along the arc is large, would come out twice as sparse as
 * the posts.
 *
 * While the door stands open the outline does not freeze: a slow wave of lightness runs along the
 * line ([WAVE_SPEED] keeps it unhurried), shifting which of the five lightnesses a point takes.
 * The points themselves do not move — only how brightly they shine does.
 */
private fun outline(
    level: ServerLevel,
    palette: List<ParticleOptions>,
    footing: Vec3,
    across: Vec3,
    drawn: Double,
    step: Int,
) {
    val jamb = SPRING
    val arc = PI * ARCH_RISE / 2.0
    val half = jamb + arc
    val reach = half * drawn

    var travelled = 0.0
    while (travelled <= reach) {
        val (side, up) = if (travelled <= jamb) {
            HALF_WIDTH to travelled
        } else {
            val angle = (travelled - jamb) / ARCH_RISE
            HALF_WIDTH * cos(angle) to SPRING + ARCH_RISE * sin(angle)
        }

        // The palette is laid out by height: deeper near the threshold, lighter under the top.
        // The wave mixes in a small shift along the outline's length — the same number as the
        // height, only alive.
        val wave = sin(travelled * WAVE_LENGTH - step * WAVE_SPEED) * WAVE_AMPLITUDE
        val shade = ((up / HEIGHT) * (palette.size - 1) + wave).toInt().coerceIn(palette.indices)
        val glyph = palette[shade]
        put(level, glyph, footing.add(across.scale(side)).add(0.0, up, 0.0))
        put(level, glyph, footing.add(across.scale(-side)).add(0.0, up, 0.0))
        travelled += SPACING
    }
}

/**
 * The pane: a few motes slowly settling inside the frame.
 *
 * Not a vortex and not a fill — precisely a **hint**: just enough that the opening does not read
 * as empty.
 *
 * Drawn with [ModParticles.SPIRIT_GLYPH], not [ModParticles.SPIRIT_MOTE]: the vanilla particle
 * constructor with a non-zero velocity mixes ±0.4 blocks/tick of random spread into any vector it
 * is given (CLAUDE.md, trap 6) — so a mote will not do here, the spread would drown any
 * controlled fall. A glyph, on the other hand, is placed where it is told with no physics, so the
 * "falling" is the layout of the points itself: each virtual mote (index [i]) has its own phase
 * within [FALL_CYCLE] frames and descends evenly from the top of the frame to the threshold,
 * after which it starts again from the top — and that is what reads as slow settling, governed by
 * exactly two constants.
 */
private fun pane(
    level: ServerLevel,
    veil: ParticleOptions,
    footing: Vec3,
    across: Vec3,
    step: Int,
    strength: Double,
) {
    val radiusX = HALF_WIDTH * PANE_WIDTH * strength
    // The vertical span is drawn in towards the centre along with strength rather than staying
    // full height: otherwise at strength → 0 the pane would become a thin vertical stroke instead
    // of vanishing into a point.
    val centre = HEIGHT * (PANE_TOP + PANE_BOTTOM) / 2.0
    val top = centre + (HEIGHT * PANE_TOP - centre) * strength
    val bottom = centre - (centre - HEIGHT * PANE_BOTTOM) * strength

    repeat(PANE_MOTES) { i ->
        val phase = (step + i * FALL_CYCLE / PANE_MOTES) % FALL_CYCLE
        val fall = phase.toDouble() / FALL_CYCLE
        val angle = TAU * (i + PANE_PHASE * step) / PANE_MOTES
        val radius = PANE_INNER + (1.0 - PANE_INNER) * level.random.nextDouble()
        put(
            level, veil,
            footing
                .add(across.scale(radiusX * radius * cos(angle)))
                .add(0.0, top - (top - bottom) * fall, 0.0),
        )
    }
}

private fun put(level: ServerLevel, particle: ParticleOptions, point: Vec3) {
    level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
}

private val UP = Vec3(0.0, 1.0, 0.0)
private const val TAU = 2.0 * PI

/** A glyph lives four ticks; every two of them give two generations, so the line does not blink at the seam. */
private const val PERIOD_TICKS = 2

// ── The arch ────────────────────────────────────────────────────────────────────
private const val HALF_WIDTH = 0.58
/** The height the arc starts at: below it the posts are straight. */
private const val SPRING = 1.25
private const val ARCH_RISE = 0.72
private const val HEIGHT = SPRING + ARCH_RISE
/** The step between outline points: smaller means a denser frame, with glyphs lying on top of each other. */
private const val SPACING = 0.09
private const val SHADES = 5
private const val SHADE_SPREAD = 0.42

/** The wavelength of the lightness wave along the outline: how many blocks of outline to one period. */
private const val WAVE_LENGTH = 1.6
/** The wave's speed: small — so the outline feels alive rather than flickering. */
private const val WAVE_SPEED = 0.22
/** The wave's span in palette steps: back and forth around a single shade. */
private const val WAVE_AMPLITUDE = 1.1

// ── The pane ────────────────────────────────────────────────────────────────────
private const val PANE_WIDTH = 0.72
private const val PANE_MOTES = 3
private const val PANE_INNER = 0.35
private const val PANE_PHASE = 0.37
/** The pane's top and bottom: motes settle between them rather than flaring and dying. */
private const val PANE_TOP = 0.92
private const val PANE_BOTTOM = 0.08
/** How many frames a full settling from top to bottom takes: more means slower and calmer. */
private const val FALL_CYCLE = 40
/** Until the outline is two thirds done there is no pane: it would hang in emptiness. */
private const val VEIL_FROM = 0.66
private const val VEIL_SHADE = -0.25

// ── Opening ─────────────────────────────────────────────────────────────────────
private const val OPEN_FRAMES = 9
/** Closing is slower than opening: an abrupt disappearance read as a snap rather than a fade. */
private const val CLOSE_FRAMES = 12
private const val CHIME_EVERY = 3
private const val OPEN_PITCH = 1.15f
private const val OPEN_PITCH_STEP = 0.06f
