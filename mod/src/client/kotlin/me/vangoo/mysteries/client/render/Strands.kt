package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The client half of `pathways/fool/Threads.kt`: a thread spun in the open from one body's hand to
 * another body, which everyone near sees — the Magician's thread to the one they heal, the
 * Marionettist's to the puppet they step into.
 *
 * It rides both bodies frame by frame, against their interpolated positions, so it never trails.
 * It runs out of the hand over its first ticks, sags a little while it is slack, and fades. A thread
 * that **pulls** goes taut instead, and flares white for the tick it is jerked.
 *
 * A pixel-wide line of light with a dimmer row either side, as the Door's rim is drawn: lightning,
 * which a shader pack draws as it is.
 *
 * The same lines wind a **spool** where a puppet fell: loop after loop round a middle, each on its
 * own tilt, tighter as the Characteristic gathers itself; wound, the ball flares and closes on the
 * core the server drops there. The spool is drawn translucent, not as light: nine loops of light on
 * one small ball add up to a white ring, and the thread is lost in it.
 */
object Strands {

    private class Strand(val from: Int, val to: Int, val rgb: Int, val ticks: Int, val pull: Boolean) {
        var age = 0
    }

    private val strands = mutableListOf<Strand>()

    private class Spool(val at: Vec3, val rgb: Int, val ticks: Int) {
        var age = 0
    }

    private val spools = mutableListOf<Spool>()

    fun accept(payload: WorldEffectPayload.Spool) {
        spools += Spool(payload.at, mix(payload.color and 0xFFFFFF, WHITE, SPOOL_PALE), payload.ticks)
    }

    fun accept(payload: WorldEffectPayload.Thread) {
        strands += Strand(payload.from, payload.to, pale(payload.color), payload.ticks, payload.pull)
    }

    fun tick() {
        strands.removeAll { ++it.age >= it.ticks + FADE_TICKS }
        spools.removeAll { ++it.age >= it.ticks + CLOSE_TICKS }
    }

    fun clear() {
        strands.clear()
        spools.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (strands.isEmpty() && spools.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val live = strands.mapNotNull { strand ->
            val a = level.getEntity(strand.from) ?: return@mapNotNull null
            val b = level.getEntity(strand.to) ?: return@mapNotNull null
            Triple(strand, hand(a, partial), body(b, partial))
        }
        if (live.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning()) { pose, buffer ->
                for ((strand, from, to) in live) draw(pose, buffer, camera, strand, strand.age + partial, from, to)
            }
        }
        if (spools.isNotEmpty()) {
            val winding = spools.toList()
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
                for (spool in winding) wind(pose, buffer, camera, spool, spool.age + partial)
            }
        }
    }

    private fun wind(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, spool: Spool, age: Float) {
        val progress = (age / spool.ticks).coerceIn(0f, 1f)
        val closing = ((age - spool.ticks) / CLOSE_TICKS).coerceIn(0f, 1f)
        val radius = (SPOOL_WIDE + (SPOOL_TIGHT - SPOOL_WIDE) * ease(progress)) * (1 - closing)
        if (radius <= 0.0) return
        // Closing, it flares white and goes.
        val rgb = mix(spool.rgb, WHITE, closing)
        val fade = 1f - closing * closing
        val core = argbOf(rgb, CORE * fade)
        val spin = age * SPIN

        LOOP_AXES.forEachIndexed { i, axis ->
            // Loop after loop: each starts once the one before it is halfway round.
            val wound = ((progress * (LOOPS + 1) - i) / 2f).coerceIn(0f, 1f)
            if (wound <= 0f) return@forEachIndexed
            val normal = axis.yRot(spin)
            val u = normal.cross(if (kotlin.math.abs(normal.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)).normalize()
            val v = normal.cross(u)
            fun point(turn: Float): Vec3 {
                val theta = 2 * Math.PI * turn
                return spool.at.add(u.scale(cos(theta) * radius)).add(v.scale(sin(theta) * radius))
            }
            var previous = point(0f)
            val steps = kotlin.math.ceil(wound * LOOP_SEGMENTS).toInt()
            for (step in 1..steps) {
                val next = point(minOf(step.toFloat() / LOOP_SEGMENTS, wound))
                pixelLine(pose, buffer, camera, previous, next, core, PIXEL * SPOOL_THREAD, LightCoordsUtil.FULL_BRIGHT)
                previous = next
            }
        }
    }

    private fun draw(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, strand: Strand, age: Float, from: Vec3, to: Vec3) {
        val reach = ease(age / WorldEffectPayload.Thread.REACH_TICKS)
        val fade = 1f - ((age - strand.ticks) / FADE_TICKS).coerceIn(0f, 1f)
        if (reach <= 0f || fade <= 0f) return
        // A pulling thread is jerked taut the tick it arrives, and flares white for that tick.
        val jerk = if (strand.pull) (1f - kotlin.math.abs(age - WorldEffectPayload.Thread.REACH_TICKS - 0.5f) / JERK_TICKS).coerceIn(0f, 1f) else 0f
        val slack = if (strand.pull) SLACK * (1f - reach) else SLACK
        val sag = from.distanceTo(to) * slack
        val rgb = if (jerk > 0f) mix(strand.rgb, WHITE, jerk) else strand.rgb
        val core = argbOf(rgb, fade * (CORE + (1 - CORE) * jerk))
        val halo = argbOf(rgb, fade * HALO)

        var previous = from
        for (i in 1..SEGMENTS) {
            val t = reach * i / SEGMENTS
            // A shallow sag: the middle of a slack thread hangs lower than its ends.
            val point = from.lerp(to, t.toDouble()).subtract(0.0, sag * 4 * t * (1 - t), 0.0)
            // Core first: lightning writes depth, and a core laid on its own halo loses half its pixels to it
            // (a dashed thread). A halo that loses to the core instead is never missed — light only adds.
            pixelLine(pose, buffer, camera, previous, point, core)
            pixelLine(pose, buffer, camera, previous, point, halo, PIXEL * HALO_WIDTH)
            previous = point
        }
    }

    /** The right hand of a body, roughly where it hangs; the middle of anything without hands. */
    private fun hand(entity: Entity, partial: Float): Vec3 {
        val at = entity.getPosition(partial)
        val living = entity as? LivingEntity ?: return at.add(0.0, entity.bbHeight * 0.5, 0.0)
        val yaw = Math.toRadians(Mth.lerp(partial, living.yBodyRotO, living.yBodyRot).toDouble())
        val forwardX = -sin(yaw)
        val forwardZ = cos(yaw)
        return at.add(-forwardZ * HAND_SIDE + forwardX * HAND_AHEAD, living.bbHeight * HAND_HEIGHT, forwardX * HAND_SIDE + forwardZ * HAND_AHEAD)
    }

    private fun body(entity: Entity, partial: Float): Vec3 = entity.getPosition(partial).add(0.0, entity.bbHeight * BODY_HEIGHT, 0.0)

    /** The pathway's colour, half washed to white: a thread is pale whatever pathway spun it. */
    private fun pale(rgb: Int): Int = mix(rgb and 0xFFFFFF, WHITE, 0.5f)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val x = a shr shift and 0xFF
            val y = b shr shift and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255) shl shift
        }
        return channel(16) or channel(8) or channel(0)
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1 - (1 - x) * (1 - x)
    }

    private const val WHITE = 0xFFFFFF
    private const val SEGMENTS = 12
    private const val FADE_TICKS = 6f
    private const val JERK_TICKS = 1.5f
    private const val SLACK = 0.05
    private const val CORE = 0.85f
    private const val HALO = 0.22f
    private const val HALO_WIDTH = 3.0
    private const val HAND_SIDE = 0.36
    private const val HAND_AHEAD = 0.2
    private const val HAND_HEIGHT = 0.42
    private const val BODY_HEIGHT = 0.6

    private const val LOOPS = 16
    private const val LOOP_SEGMENTS = 24
    private const val SPOOL_WIDE = 0.9
    private const val SPOOL_TIGHT = 0.3
    /** A spool's thread is finer than a thrown one: many turns make the ball, not one fat ring. */
    private const val SPOOL_THREAD = 0.5

    /**
     * The tilt of each loop: spread evenly over the half-sphere (a golden spiral), so no two lie on top of
     * each other and the turns cross the whole ball rather than piling up on its rim.
     */
    private val LOOP_AXES: List<Vec3> = List(LOOPS) { i ->
        val y = 1.0 - (i + 0.5) / LOOPS
        val r = kotlin.math.sqrt(1.0 - y * y)
        val phi = i * Math.PI * (3.0 - kotlin.math.sqrt(5.0))
        Vec3(cos(phi) * r, y, sin(phi) * r)
    }
    /** Radians a tick: a slow turn, so the ball reads as wound rather than drawn on the air. */
    private const val SPIN = 0.03f
    private const val CLOSE_TICKS = 8f
    /** A ball keeps more of its pathway's colour than a thread does: it is the Characteristic itself. */
    private const val SPOOL_PALE = 0.2f
}
