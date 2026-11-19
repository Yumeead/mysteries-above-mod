package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.client.beyonder.ClientDossiers
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Scrutiny
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The client half of the Sheriff's Recognition (`pathways/justiciar/abilities/Recognition.kt`), for the Sheriff alone:
 *
 * - **The gaze**: four corners close round the one looked at as the Sheriff holds them in the eye, and snap shut with a
 *   flash once they are remembered; looking away lets them drift apart and fade.
 * - **The footprints** of every remembered one, wherever they walk while this client has them loaded: pressed into the
 *   ground left and right, glowing faintly, going out over [PRINT_TICKS]. Drawn through walls and hills, the way a
 *   name tag is: the Sheriff knows where the one they remember is, not only sees it.
 */
internal object Recognitions {

    private class Look(val payload: Scrutiny) {
        var age = 0
        var stage = payload.stage
        var since = 0
    }

    private class Print(val x: Double, val y: Double, val z: Double, val yaw: Double, val left: Boolean, val color: Int) {
        var age = 0
    }

    private class Trail(var lastX: Double, var lastZ: Double, var left: Boolean) {
        val prints = ArrayDeque<Print>()
    }

    private val looks = HashMap<Int, Look>()
    private val trails = HashMap<UUID, Trail>()

    fun accept(payload: Scrutiny) {
        when (payload.stage) {
            Scrutiny.Stage.GAZE -> looks[payload.entity] = Look(payload)
            else -> looks[payload.entity]?.let { it.stage = payload.stage; it.since = it.age }
        }
    }

    fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        looks.values.removeAll { look ->
            look.age++
            when (look.stage) {
                Scrutiny.Stage.GAZE -> look.age > look.payload.ticks + GAZE_GRACE
                Scrutiny.Stage.LOST -> look.age - look.since > LOST_TICKS
                Scrutiny.Stage.KNOWN -> look.age - look.since > KNOWN_TICKS
            }
        }

        for (trail in trails.values) {
            for (print in trail.prints) print.age++
            while (trail.prints.isNotEmpty() && trail.prints.first().age >= PRINT_TICKS) trail.prints.removeFirst()
        }
        val followed = ClientDossiers.followed
        trails.keys.retainAll(followed)
        if (followed.isEmpty()) return
        val self = minecraft.player
        for (entity in level.entitiesForRendering()) {
            if (entity.uuid !in followed || entity === self) continue
            step(entity, trails.getOrPut(entity.uuid) { Trail(entity.x, entity.z, false) }, PRINT_COLOUR)
        }
    }

    /** A print where the body has walked a stride since the last one, and only on the ground. */
    private fun step(body: Entity, trail: Trail, colour: Int) {
        val dx = body.x - trail.lastX
        val dz = body.z - trail.lastZ
        val moved = dx * dx + dz * dz
        if (moved > TELEPORT * TELEPORT) {
            trail.lastX = body.x
            trail.lastZ = body.z
            return
        }
        if (moved < STRIDE * STRIDE || !body.onGround()) return
        val yaw = atan2(dz, dx)
        trail.left = !trail.left
        // Left and right of the line walked, as feet fall.
        val side = if (trail.left) FOOT_APART else -FOOT_APART
        trail.prints += Print(body.x - sin(yaw) * side, body.y + LIFT, body.z + cos(yaw) * side, yaw, trail.left, colour)
        while (trail.prints.size > MAX_PRINTS) trail.prints.removeFirst()
        trail.lastX = body.x
        trail.lastZ = body.z
    }

    fun clear() {
        looks.clear()
        trails.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (looks.isEmpty() && trails.values.all { it.prints.isEmpty() }) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos

        val prints = ArrayList<Print>()
        for (trail in trails.values) prints += trail.prints
        if (prints.isNotEmpty()) {
            val clock = (level.gameTime % 24000L) + partial
            val newest = trails.values.mapNotNull { it.prints.lastOrNull() }.toSet()
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textSeeThrough(FOOTPRINT)) { pose, buffer ->
                for (print in prints) {
                    val age = print.age + partial
                    val fade = 1 - ((age - (PRINT_TICKS - PRINT_FADE)) / PRINT_FADE).coerceIn(0f, 1f)
                    val pulse = if (print in newest) 0.75f + 0.25f * sin(clock * 0.25f) else 0.75f
                    val appear = (age / PRINT_APPEAR).coerceIn(0f, 1f)
                    flat(pose, buffer, camera, print, argbOf(print.color, fade * pulse * appear))
                }
            }
        }

        val strokes = Strokes()
        for ((id, look) in looks) {
            val body = level.getEntity(id) ?: continue
            val age = look.age + partial
            val (spread, alpha, colour) = when (look.stage) {
                Scrutiny.Stage.GAZE -> {
                    val t = (age / look.payload.ticks).coerceIn(0f, 1f)
                    Triple(1 + (1 - easeOut(t)) * OPEN, 0.35f + 0.55f * t, look.payload.color)
                }
                Scrutiny.Stage.LOST -> {
                    val t = ((age - look.since) / LOST_TICKS).coerceIn(0f, 1f)
                    Triple(1 + OPEN * t, (1 - t) * 0.6f, look.payload.color)
                }
                Scrutiny.Stage.KNOWN -> {
                    val t = ((age - look.since) / KNOWN_TICKS).coerceIn(0f, 1f)
                    Triple(1f - 0.08f * (1 - t), 1 - t * t, Strands.mix(look.payload.color, WHITE, 0.6f * (1 - t)))
                }
            }
            if (alpha > 0.01f) corners(strokes, body, camera, partial, spread, argbOf(colour, alpha))
        }
        if (!strokes.isEmpty) strokes.submit(context)
    }

    /** Four corners round [body], in a plane turned to the eye, [spread] times as far out as they close to. */
    private fun corners(strokes: Strokes, body: Entity, camera: Vec3, partial: Float, spread: Float, argb: Int) {
        val feet = body.getPosition(partial)
        val centre = feet.add(0.0, body.bbHeight / 2.0, 0.0)
        val toEye = camera.subtract(centre).let { Vec3(it.x, 0.0, it.z) }.normalize()
        val right = Vec3(-toEye.z, 0.0, toEye.x)
        val halfW = (body.bbWidth / 2.0 + MARGIN) * spread
        val halfH = (body.bbHeight / 2.0 + MARGIN) * spread
        val arm = minOf(halfW, halfH) * ARM
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val corner = centre.add(right.scale(halfW * sx)).add(0.0, halfH * sy, 0.0)
            strokes.cold(corner, corner.add(right.scale(-arm * sx)), argb, FULL_BRIGHT, PIXEL * 2)
            strokes.cold(corner, corner.add(0.0, -arm * sy, 0.0), argb, FULL_BRIGHT, PIXEL * 2)
        }
    }

    /** A print lying on the ground, toe along [Print.yaw]; a left foot is the right one mirrored. */
    private fun flat(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, print: Print, argb: Int) {
        val fx = cos(print.yaw) * PRINT_SIZE / 2
        val fz = sin(print.yaw) * PRINT_SIZE / 2
        val sx = -fz * 0.7
        val sz = fx * 0.7
        // Toe ahead (v = 0), heel behind (v = 1); the two sides swap for the other foot.
        val corners = arrayOf(
            Vec3(print.x + fx - sx, print.y, print.z + fz - sz), Vec3(print.x + fx + sx, print.y, print.z + fz + sz),
            Vec3(print.x - fx + sx, print.y, print.z - fz + sz), Vec3(print.x - fx - sx, print.y, print.z - fz - sz),
        )
        val u0 = if (print.left) 1f else 0f
        val u1 = 1 - u0
        val us = floatArrayOf(u0, u1, u1, u0)
        val vs = floatArrayOf(0f, 0f, 1f, 1f)
        for (order in BOTH) for (i in order) {
            val p = corners[i]
            buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                .setColor(argb).setUv(us[i], vs[i]).setLight(FULL_BRIGHT)
        }
    }

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)

    private val FOOTPRINT = modId("textures/effect/footprint.png")
    private val BOTH = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))
    private const val WHITE = 0xFFFFFF
    private const val FULL_BRIGHT = 0xF000F0
    private const val PRINT_COLOUR = 0xEBC38E

    private const val STRIDE = 0.85
    private const val FOOT_APART = 0.14
    private const val TELEPORT = 6.0
    private const val LIFT = 0.03
    private const val PRINT_SIZE = 0.5
    private const val PRINT_TICKS = 200
    private const val PRINT_FADE = 80f
    private const val PRINT_APPEAR = 6f
    private const val MAX_PRINTS = 48

    private const val OPEN = 0.9f
    private const val MARGIN = 0.15
    private const val ARM = 0.4
    private const val GAZE_GRACE = 20
    private const val LOST_TICKS = 8f
    private const val KNOWN_TICKS = 14f
}
