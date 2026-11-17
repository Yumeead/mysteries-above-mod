package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.Random

/**
 * Authority's aura as a fire of its own round the body, not a copy of it (the user's call, 17.11.2026, with a
 * picture of a blazing anime aura): a tall flame envelope, narrow at the feet, widest at the chest, licking up a
 * block and more above the head, its rim and the streaks inside it running upwards, crackling now and then.
 *
 * Two flat sprites turned to the eye about the vertical: the mass of the fire just **behind** the body, and in
 * front of it only the rim and the streaks, so the body shows through, inside its fire. Both are frames of one
 * pixel sheet (`textures/effect/authority_aura.png`: eight frames, behind and in front), full bright, translucent.
 *
 * It follows the cast ([Edicts.aura]): kindles, grows and brightens a step at each knock with a brief flare,
 * blazes up at the blow and burns out. One's own aura is not drawn in first person: it would stand round the eye.
 */
internal object Auras {

    fun submit(context: LevelRenderContext, auras: List<Edicts.Aura>) {
        if (auras.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos
        val live = auras.mapNotNull { aura ->
            val body = level.getEntity(aura.entity) ?: return@mapNotNull null
            if (Whips.firstPerson(body) || body.isInvisible) null else aura to body
        }
        if (live.isEmpty()) return
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(TEXTURE)) { pose, buffer ->
            for ((aura, body) in live) fire(pose, buffer, camera, aura, body, aura.age + partial, partial)
        }
        val strokes = Strokes()
        for ((aura, body) in live) crackle(strokes, aura, body, aura.age + partial, partial, camera)
        if (!strokes.isEmpty) strokes.submit(context)
    }

    /**
     * How the fire hangs off a moving body: a damped spring for each of [Motion.HEIGHTS], all pulled towards the feet,
     * stiff low down and loose at the top. Running, the top trails and leans back; stopping, it swings past and
     * settles; jumping, it lags up and down. Real simulation, ticked with the body ([follow]), not a canned shape.
     */
    class Motion {
        internal val at = arrayOfNulls<Vec3>(HEIGHTS.size)
        internal val before = arrayOfNulls<Vec3>(HEIGHTS.size)
        internal val speed = arrayOfNulls<Vec3>(HEIGHTS.size)
        internal var pace = 0.0
        internal var pacePrev = 0.0
        internal var last: Vec3? = null

        companion object {
            /** The heights that have a spring of their own, as shares of the fire's height; the feet are fixed. */
            val HEIGHTS = doubleArrayOf(0.2, 0.4, 0.6, 0.8, 1.0)
            val STIFFNESS = doubleArrayOf(0.6, 0.42, 0.3, 0.22, 0.16)
            val DAMPING = doubleArrayOf(0.55, 0.6, 0.66, 0.7, 0.74)
        }
    }

    /** One tick of the springs, after the body moved to [feet]. */
    fun follow(aura: Edicts.Aura, feet: Vec3) {
        val motion = aura.motion
        val last = motion.last
        // A teleport, or the first tick: the fire comes along at once instead of streaking across.
        val jump = last == null || last.distanceToSqr(feet) > SNAP * SNAP
        motion.pacePrev = motion.pace
        val step = if (last == null) 0.0 else feet.subtract(last).length()
        motion.pace += ((if (jump) 0.0 else step) - motion.pace) * PACE_SMOOTHING
        motion.last = feet
        for (k in Motion.HEIGHTS.indices) {
            val at = motion.at[k]
            if (jump || at == null) {
                motion.at[k] = feet
                motion.before[k] = feet
                motion.speed[k] = Vec3.ZERO
                continue
            }
            val speed = motion.speed[k]!!.scale(Motion.DAMPING[k]).add(feet.subtract(at).scale(Motion.STIFFNESS[k]))
            var next = at.add(speed)
            // Never further than [MAX_LAG] from the body: a sprint must not tear the fire off it.
            val off = next.subtract(feet)
            if (off.length() > MAX_LAG) next = feet.add(off.normalize().scale(MAX_LAG))
            motion.before[k] = at
            motion.at[k] = next
            motion.speed[k] = speed
        }
    }

    private fun fire(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, aura: Edicts.Aura, body: Entity, age: Float, partial: Float) {
        val glow = glow(age)
        if (glow <= 0f) return
        val feet = body.getPosition(partial)
        val toEye = camera.subtract(feet).multiply(1.0, 0.0, 1.0).let { if (it.lengthSqr() < 1e-6) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        val right = Vec3(toEye.z, 0.0, -toEye.x)
        val motion = aura.motion
        val pace = motion.pacePrev + (motion.pace - motion.pacePrev) * partial
        // The head ends at [HEAD_SHARE] of the sheet's height; the sheet keeps its pixels square. Moving, it draws out.
        val height = body.bbHeight / HEAD_SHARE * size(age) * (1 + (pace * PACE_STRETCH).coerceAtMost(MAX_STRETCH))
        val width = height * SHEET_ASPECT
        val frame = frame(age, aura.entity)
        val alpha = glow.coerceAtMost(1f)
        val seed = aura.entity * 0.618
        // Where each row of the fire stands: the springs' lag, plus a sway that never quite repeats.
        val rows = (0..ROWS).map { i ->
            val h = i.toDouble() / ROWS
            // A flame squashes and draws out, it does not turn over: the lag may lower a row to under half its
            // height or lift it by two fifths, never below the feet.
            val lag = lagAt(motion, h, feet, partial).let { it.with(net.minecraft.core.Direction.Axis.Y, it.y.coerceIn(-height * h * SQUASH, height * h * DRAW_OUT)) }
            val sway = h * h * (SWAY * kotlin.math.sin(age * 0.23 + seed + h * 3.1) + SWAY * 0.6 * kotlin.math.sin(age * 0.61 + seed * 2 + h * 5.3))
            val breathe = 1 + BREATHE * kotlin.math.sin(age * 0.37 + seed * 3 + h * 4.7) + BREATHE * 0.5 * kotlin.math.sin(age * 0.89 + h * 2.3)
            Row(feet.add(lag).add(right.scale(sway)).add(0.0, height * h - SINK, 0.0), width * breathe, h)
        }
        for (front in listOf(false, true)) {
            val depth = toEye.scale(if (front) LAYER_GAP else -LAYER_GAP)
            val v0 = if (front) 0.5f else 0f
            val argb = argbOf(0xFFFFFF, alpha * (if (front) FRONT_ALPHA else 1f))
            for (i in 0 until ROWS) strip(pose, buffer, camera, rows[i], rows[i + 1], depth, right, frame, v0, argb, toEye)
        }
    }

    private class Row(val centre: Vec3, val width: Double, val share: Double)

    /** The springs' lag at share [h] of the height, interpolated between their heights and between ticks. */
    private fun lagAt(motion: Motion, h: Double, feet: Vec3, partial: Float): Vec3 {
        fun lag(k: Int): Vec3 {
            val at = motion.at[k] ?: return Vec3.ZERO
            val before = motion.before[k] ?: at
            // Both between ticks: the spring where it is this frame, against the body where it is this frame.
            return before.lerp(at, partial.toDouble()).subtract(feet)
        }
        val heights = Motion.HEIGHTS
        if (h <= heights[0]) return lag(0).scale(h / heights[0])
        for (k in 1 until heights.size) {
            if (h <= heights[k]) {
                val t = (h - heights[k - 1]) / (heights[k] - heights[k - 1])
                return lag(k - 1).lerp(lag(k), t)
            }
        }
        return lag(heights.size - 1)
    }

    /** One band of the fire between two rows: a quad that bends with them. */
    private fun strip(
        pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, low: Row, high: Row, depth: Vec3, right: Vec3,
        frame: Int, v0: Float, argb: Int, normal: Vec3,
    ) {
        val u0 = frame.toFloat() / FRAMES
        val u1 = (frame + 1f) / FRAMES
        // The sheet's top is the fire's tip.
        val vLow = v0 + (1 - low.share).toFloat() * 0.5f
        val vHigh = v0 + (1 - high.share).toFloat() * 0.5f
        val a = low.centre.add(depth)
        val b = high.centre.add(depth)
        corner(pose, buffer, camera, a.subtract(right.scale(low.width / 2)), u0, vLow, argb, normal)
        corner(pose, buffer, camera, a.add(right.scale(low.width / 2)), u1, vLow, argb, normal)
        corner(pose, buffer, camera, b.add(right.scale(high.width / 2)), u1, vHigh, argb, normal)
        corner(pose, buffer, camera, b.subtract(right.scale(high.width / 2)), u0, vHigh, argb, normal)
    }

    /**
     * Which frame of the sheet: the flames run on at an uneven pace, now faster, now slower, so the eight frames
     * never fall into one beat.
     */
    private fun frame(age: Float, entity: Int): Int {
        val phase = age / FRAME_TICKS + FRAME_WOBBLE * kotlin.math.sin(age * 0.21 + entity)
        return ((phase.toInt() % FRAMES) + FRAMES) % FRAMES
    }

    private fun corner(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, at: Vec3, u: Float, v: Float, argb: Int, normal: Vec3) {
        buffer.addVertex(pose, (at.x - camera.x).toFloat(), (at.y - camera.y).toFloat(), (at.z - camera.z).toFloat())
            .setColor(argb)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /** Short arcs round the body: now and then from the second knock on, several at once at the blow. */
    private fun crackle(strokes: Strokes, aura: Edicts.Aura, body: Entity, age: Float, partial: Float, camera: Vec3) {
        val beat = (age / CRACKLE_TICKS).toInt()
        val blow = age >= Edict.SEAL_TICKS && age < Edict.SEAL_TICKS + BLOW_CRACKLE_TICKS
        val step = (age / Edict.SEAL_KNOCK_TICKS).toInt()
        val random = Random(aura.entity * 7919L + beat)
        val arcs = when {
            blow -> BLOW_ARCS
            age < Edict.SEAL_TICKS && step >= 1 && random.nextInt(CRACKLE_ODDS) < step -> 1
            else -> 0
        }
        val feet = body.getPosition(partial)
        val reach = body.bbWidth / 2.0 + ARC_OUT
        repeat(arcs) {
            val a = feet.add((random.nextDouble() * 2 - 1) * reach, random.nextDouble() * body.bbHeight * 1.15, (random.nextDouble() * 2 - 1) * reach)
            val b = a.add((random.nextDouble() - 0.5) * ARC_SPAN, (random.nextDouble() - 0.3) * ARC_SPAN, (random.nextDouble() - 0.5) * ARC_SPAN)
            Lashes.bolt(strokes, a, b, random.nextLong(), 1f, ARC_RGB, ARC_JAG, camera, forks = false)
        }
    }

    /** How large the fire stands now, as a share of its full height: grows with the steps, leaps at the blow. */
    private fun size(age: Float): Double {
        if (age >= Edict.SEAL_TICKS) {
            val t = ((age - Edict.SEAL_TICKS) / Edict.AURA_FADE_TICKS).coerceIn(0f, 1f)
            return BLOW_SIZE - (BLOW_SIZE - 1.0) * t
        }
        val step = (age / Edict.SEAL_KNOCK_TICKS).toInt().coerceIn(0, SIZES.size - 1)
        val kindle = (age / KINDLE_TICKS).coerceAtMost(1f)
        return SIZES[step] * (0.6 + 0.4 * kindle)
    }

    /** The fire's strength: kindled, a step up at each knock with a brief flare, blazing at the blow, burning out. */
    fun glow(age: Float): Float {
        if (age >= Edict.SEAL_TICKS) {
            val t = ((age - Edict.SEAL_TICKS) / Edict.AURA_FADE_TICKS).coerceIn(0f, 1f)
            return 1f - t * t
        }
        val step = (age / Edict.SEAL_KNOCK_TICKS).toInt().coerceIn(0, STEPS.size - 1)
        val sinceKnock = age - step * Edict.SEAL_KNOCK_TICKS
        val flare = if (step > 0 && sinceKnock < KNOCK_FLARE_TICKS) KNOCK_FLARE * (1 - sinceKnock / KNOCK_FLARE_TICKS) else 0f
        return (STEPS[step] * (age / KINDLE_TICKS).coerceAtMost(1f) + flare).coerceAtMost(1f)
    }

    private val TEXTURE = modId("textures/effect/authority_aura.png")
    private const val FULL_BRIGHT = 0xF000F0

    /** The sheet: eight frames of 32 x 48 pixels, behind above, in front below. */
    private const val FRAMES = 8
    private const val FRAME_TICKS = 2f
    private const val SHEET_ASPECT = 32.0 / 48.0

    /** Where the head ends on the sheet, as a share of its height: the flame stands that much above it. */
    private const val HEAD_SHARE = 0.62

    /** How far behind and before the body's axis the two sprites stand, and how far below the feet they begin. */
    private const val LAYER_GAP = 0.35
    private const val SINK = 0.08
    private const val FRONT_ALPHA = 0.85f

    private val STEPS = floatArrayOf(0.45f, 0.7f, 0.95f)
    private val SIZES = doubleArrayOf(0.8, 0.9, 1.0)
    private const val KINDLE_TICKS = 10f

    /** Rows the fire is bent along; the springs' reach and when a jump counts as a teleport. */
    private const val ROWS = 12
    private const val MAX_LAG = 0.75
    private const val SQUASH = 0.55
    private const val DRAW_OUT = 0.4
    private const val SNAP = 4.0

    /** How much the fire draws out with the body's pace (blocks a tick), and how smoothly the pace is read. */
    private const val PACE_STRETCH = 0.9
    private const val MAX_STRETCH = 0.3
    private const val PACE_SMOOTHING = 0.3

    /** The never-repeating sway of the upper fire (blocks) and breathing of its width (share). */
    private const val SWAY = 0.06
    private const val BREATHE = 0.06
    private const val FRAME_WOBBLE = 1.5f
    private const val KNOCK_FLARE = 0.3f
    private const val KNOCK_FLARE_TICKS = 8f
    private const val BLOW_SIZE = 1.3

    private const val CRACKLE_TICKS = 3f
    private const val CRACKLE_ODDS = 4
    private const val BLOW_CRACKLE_TICKS = 10
    private const val BLOW_ARCS = 4
    private const val ARC_OUT = 0.25
    private const val ARC_SPAN = 0.9
    private const val ARC_JAG = 0.12
    private const val ARC_RGB = 0xFFF2A0
}
