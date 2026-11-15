package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The client half of the Fool's paper (`pathways/fool/Paper.kt`): sheets in flight, and bodies that
 * have become paper. Both arrive as one packet each and are built here as a function of their age.
 *
 * | shape | what is seen | drawn with |
 * |---|---|---|
 * | sheet | a sheet spinning flat through the air, tilted so the thrower sees its face; it tears into scraps where it strikes, or flutters down where it misses | the sheet's own texture, [RenderTypes.entityCutout] |
 * | paper body | the body in its very pose, but paper: it shudders under the blow, a cut runs across it, and it falls apart into scraps | the vanilla player model with a paper skin; the cut is lightning, a pixel wide |
 * | face | a face coming off one head and onto another, or peeling off and tearing on the way down; a blank paper mask settling on | the face square of the skin the head wears (or of the paper skin), [RenderTypes.entityTranslucent] |
 *
 * Everything here is vanilla render types: a shader pack draws it as it is.
 */
object Paper {

    private class Sheet(val from: Vec3, val to: Vec3, val ticks: Int, val hit: Boolean) {
        var age = 0
        val dir: Vec3 = to.subtract(from).let { if (it.lengthSqr() < 1e-6) Vec3(0.0, 0.0, 1.0) else it.normalize() }
    }

    private class Body(val state: AvatarRenderState?, val at: Vec3, val yaw: Float, val slim: Boolean) {
        var age = 0
        val forwardX = -sin(Math.toRadians(yaw.toDouble())).toFloat()
        val forwardZ = cos(Math.toRadians(yaw.toDouble())).toFloat()
    }

    private class Face(val kind: WorldEffectPayload.Face.Kind, val from: Int, val to: Int, val texture: Identifier, val start: Head) {
        var age = 0
    }

    /** Where a head is and which way its face looks. */
    private class Head(val eye: Vec3, val pitch: Float, val yaw: Float)

    private val sheets = mutableListOf<Sheet>()
    private val bodies = mutableListOf<Body>()
    private val faces = mutableListOf<Face>()

    private val wide by lazy { PlayerModel(Minecraft.getInstance().entityModels.bakeLayer(ModelLayers.PLAYER), false) }
    private val slim by lazy { PlayerModel(Minecraft.getInstance().entityModels.bakeLayer(ModelLayers.PLAYER_SLIM), true) }

    fun accept(payload: WorldEffectPayload.Sheet) {
        sheets += Sheet(payload.from, payload.to, payload.ticks, payload.hit)
    }

    fun accept(payload: WorldEffectPayload.Effigy) {
        val client = Minecraft.getInstance()
        val entity = client.level?.getEntity(payload.entity)
        // The pose is the body's at the moment it was struck; a body this client never had stands upright.
        val state = entity?.let { client.entityRenderDispatcher.extractEntity(it, 1f) as? AvatarRenderState }
        val slim = state?.skin?.model() == PlayerModelType.SLIM
        bodies += Body(state, payload.at, payload.yaw, slim)
    }

    fun accept(payload: WorldEffectPayload.Face) {
        val client = Minecraft.getInstance()
        val entity = client.level?.getEntity(payload.from) ?: return
        val texture = when (payload.kind) {
            // The face the head wears now: for a drop, the borrowed one, still on until the skin goes back.
            WorldEffectPayload.Face.Kind.TAKE, WorldEffectPayload.Face.Kind.DROP ->
                (client.entityRenderDispatcher.extractEntity(entity, 1f) as? AvatarRenderState)?.skin?.body()?.texturePath() ?: return
            WorldEffectPayload.Face.Kind.MASK, WorldEffectPayload.Face.Kind.UNMASK -> BODY
        }
        faces += Face(payload.kind, payload.from, payload.to, texture, head(entity, 1f))
    }

    fun tick() {
        faces.removeAll { face ->
            val falls = face.kind == WorldEffectPayload.Face.Kind.DROP || face.kind == WorldEffectPayload.Face.Kind.UNMASK
            if (++face.age < FACE_LIFE) return@removeAll false
            if (falls) tear(fallen(face, FACE_LIFE.toFloat()))
            true
        }
        sheets.removeAll { sheet ->
            if (++sheet.age < sheet.ticks) return@removeAll false
            land(sheet)
            true
        }
        bodies.removeAll { body ->
            if (++body.age == BURST) burst(body)
            body.age >= BODY_LIFE
        }
    }

    fun clear() {
        sheets.clear()
        bodies.clear()
        faces.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (sheets.isEmpty() && bodies.isEmpty() && faces.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val collector = context.submitNodeCollector()
        val poses = context.poseStack()

        val flying = sheets.toList()
        if (flying.isNotEmpty()) {
            collector.submitCustomGeometry(poses, RenderTypes.entityCutout(SHEET)) { pose, buffer ->
                for (sheet in flying) sheet(pose, buffer, camera, sheet, sheet.age + partial, level)
            }
        }

        val standing = bodies.toList()
        for (body in standing) {
            if (body.age >= BURST) continue
            val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(body.at.x, body.at.y + 1.0, body.at.z))
            // It shudders under the blow for a tick, then stands rigid while it is cut.
            val shudder = if (body.age < SHUDDER_TICKS) SHUDDER * sin((body.age + partial) * SHUDDER_RATE) else 0f
            poses.pushPose()
            poses.translate(
                body.at.x - camera.x + body.forwardZ * shudder,
                body.at.y - camera.y,
                body.at.z - camera.z - body.forwardX * shudder,
            )
            poses.mulPose(Axis.YP.rotationDegrees(180f - body.yaw))
            poses.scale(-1f, -1f, 1f)
            poses.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE)
            poses.translate(0.0, MODEL_DROP, 0.0)
            val model = if (body.slim) slim else wide
            collector.submitModel(model, body.state ?: upright, poses, RenderTypes.entityCutout(BODY), light, OverlayTexture.NO_OVERLAY, -1, null)
            poses.popPose()
        }

        val cutting = standing.filter { it.age >= SHUDDER_TICKS }
        if (cutting.isNotEmpty()) {
            collector.submitCustomGeometry(poses, RenderTypes.lightning()) { pose, buffer ->
                for (body in cutting) cut(pose, buffer, camera, body, body.age + partial)
            }
        }

        for (face in faces.toList()) {
            val (frame, alpha) = place(face, face.age + partial, partial) ?: continue
            // Near the eye a face fills the screen: one's own, in first person, is not drawn at all.
            val shown = alpha * ((frame.center.distanceTo(camera) - NEAR_EYE) / NEAR_FADE).toFloat().coerceIn(0f, 1f)
            if (shown <= 0f) continue
            val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(frame.center))
            collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(face.texture)) { pose, buffer ->
                square(pose, buffer, camera, frame, argbOf(0xFFFFFF, shown), light)
            }
        }
    }

    // ── Faces ───────────────────────────────────────────────────────────────────────

    /** A face square: its middle, the way it looks, and its two edges. */
    private class Frame(val center: Vec3, val forward: Vec3, val right: Vec3, val up: Vec3)

    private fun head(entity: Entity, partial: Float) = Head(entity.getEyePosition(partial), entity.getViewXRot(partial), entity.getViewYRot(partial))

    private fun live(id: Int, partial: Float): Head? = Minecraft.getInstance().level?.getEntity(id)?.let { head(it, partial) }

    private fun frame(eye: Vec3, pitch: Float, yaw: Float, out: Double): Frame {
        val forward = Vec3.directionFromRotation(pitch, yaw)
        val right = forward.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        return Frame(eye.add(forward.scale(out)), forward, right, right.cross(forward))
    }

    /** Where the face is at [age] and how opaque — or `null` while it has nowhere to be. */
    private fun place(face: Face, age: Float, partial: Float): Pair<Frame, Float>? = when (face.kind) {
        WorldEffectPayload.Face.Kind.TAKE -> {
            val from = face.start
            val to = live(face.to, partial) ?: return null
            val t = ease((age / TAKE_FLIGHT).coerceIn(0f, 1f))
            val sink = ((age - TAKE_FLIGHT) / (FACE_LIFE - TAKE_FLIGHT)).coerceIn(0f, 1f)
            // A low arc between the two heads, turning from the one face's way to the other's.
            val eye = from.eye.lerp(to.eye, t.toDouble()).add(0.0, ARC * 4 * t * (1 - t), 0.0)
            val yaw = Mth.rotLerp(t, from.yaw, to.yaw)
            frame(eye, Mth.lerp(t, from.pitch, to.pitch), yaw, FACE_OUT - SINK * sink) to (1f - sink)
        }
        WorldEffectPayload.Face.Kind.MASK -> {
            val head = live(face.from, partial) ?: return null
            val settle = ease((age / MASK_SETTLE).coerceIn(0f, 1f))
            val sink = ((age - MASK_SETTLE) / (FACE_LIFE - MASK_SETTLE)).coerceIn(0f, 1f)
            frame(head.eye, head.pitch, head.yaw, FACE_OUT + MASK_AHEAD * (1 - settle) - SINK * sink) to settle * (1f - sink)
        }
        else -> fallen(face, age) to 1f
    }

    /** A face peeled off: it comes away from the head, then drops, tipping forward as it goes. */
    private fun fallen(face: Face, age: Float): Frame {
        val start = frame(face.start.eye, face.start.pitch, face.start.yaw, FACE_OUT)
        val peel = ease((age / PEEL_TICKS).coerceIn(0f, 1f)) * PEEL
        val fall = (age - PEEL_TICKS).coerceAtLeast(0f)
        val center = start.center.add(start.forward.scale(peel.toDouble())).subtract(0.0, GRAVITY * fall * fall, 0.0)
        // Tipping about its own horizontal edge, face first.
        val tip = (fall * TUMBLE).toDouble()
        val forward = start.forward.scale(cos(tip)).add(start.up.scale(-sin(tip)))
        val up = start.up.scale(cos(tip)).add(start.forward.scale(sin(tip)))
        return Frame(center, forward, start.right, up)
    }

    /** Four corners of the skin's face, the head's right edge on the image's left as a skin is drawn. */
    private fun square(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, frame: Frame, argb: Int, light: Int) {
        val r = frame.right.scale(FACE_HALF)
        val u = frame.up.scale(FACE_HALF)
        val n = frame.forward
        faceCorner(pose, buffer, camera, frame.center.add(r).add(u), FACE_U0, FACE_V0, argb, light, n)
        faceCorner(pose, buffer, camera, frame.center.add(r).subtract(u), FACE_U0, FACE_V1, argb, light, n)
        faceCorner(pose, buffer, camera, frame.center.subtract(r).subtract(u), FACE_U1, FACE_V1, argb, light, n)
        faceCorner(pose, buffer, camera, frame.center.subtract(r).add(u), FACE_U1, FACE_V0, argb, light, n)
    }

    private fun faceCorner(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, at: Vec3, u: Float, v: Float, argb: Int, light: Int, normal: Vec3) {
        buffer.addVertex(pose, (at.x - camera.x).toFloat(), (at.y - camera.y).toFloat(), (at.z - camera.z).toFloat())
            .setColor(argb)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /** Where a fallen face ends: it tears like the paper it was. */
    private fun tear(frame: Frame) {
        val level = Minecraft.getInstance().level ?: return
        val r = level.random
        repeat(FACE_SCRAPS) {
            level.addParticle(
                ModParticles.scrap(),
                frame.center.x, frame.center.y, frame.center.z,
                (r.nextDouble() - 0.5) * SCRAP_SPEED, r.nextDouble() * SCRAP_SPEED, (r.nextDouble() - 0.5) * SCRAP_SPEED,
            )
        }
    }

    private fun ease(t: Float): Float = 1 - (1 - t) * (1 - t)

    // ── Sheets ──────────────────────────────────────────────────────────────────────

    /**
     * The sheet where it is now: on its line, spinning in its own plane, its plane tilted off the
     * line of flight so that the one who threw it sees its face rather than its edge.
     */
    private fun sheet(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, sheet: Sheet, age: Float, level: net.minecraft.client.multiplayer.ClientLevel) {
        val t = (age / sheet.ticks).coerceIn(0f, 1f).toDouble()
        val at = sheet.from.lerp(sheet.to, t)
        val forward = sheet.dir
        val right = forward.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up = right.cross(forward)
        val normal = up.scale(cos(TILT)).add(right.scale(sin(TILT)))
        val u = forward
        val v = normal.cross(u)
        val spin = age * SPIN
        val c = cos(spin) * SHEET_HALF
        val s = sin(spin) * SHEET_HALF
        // The four corners, the square turned by the spin inside the sheet's own plane.
        val a = at.add(u.scale(c - s)).add(v.scale(s + c))
        val b = at.add(u.scale(-c - s)).add(v.scale(-s + c))
        val d = at.add(u.scale(c + s)).add(v.scale(s - c))
        val e = at.add(u.scale(-c + s)).add(v.scale(-s - c))
        val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(at))
        corner(pose, buffer, camera, a, 0f, 0f, light, normal)
        corner(pose, buffer, camera, b, 1f, 0f, light, normal)
        corner(pose, buffer, camera, e, 1f, 1f, light, normal)
        corner(pose, buffer, camera, d, 0f, 1f, light, normal)
    }

    private fun corner(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, at: Vec3, u: Float, v: Float, light: Int, normal: Vec3) {
        buffer.addVertex(pose, (at.x - camera.x).toFloat(), (at.y - camera.y).toFloat(), (at.z - camera.z).toFloat())
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /** Where a sheet ends: torn to scraps on what it struck, or two scraps of a sheet that lost its way. */
    private fun land(sheet: Sheet) {
        val level = Minecraft.getInstance().level ?: return
        val count = if (sheet.hit) HIT_SCRAPS else MISS_SCRAPS
        val back = sheet.dir.scale(-SCRAP_SPEED)
        repeat(count) {
            val r = level.random
            level.addParticle(
                ModParticles.scrap(),
                sheet.to.x, sheet.to.y, sheet.to.z,
                back.x + (r.nextDouble() - 0.5) * SCRAP_SPEED * 2,
                (r.nextDouble() * 0.5 + 0.2) * SCRAP_SPEED * 2,
                back.z + (r.nextDouble() - 0.5) * SCRAP_SPEED * 2,
            )
        }
    }

    // ── Paper bodies ────────────────────────────────────────────────────────────────

    /** The cut: from the right shoulder to the left hip, a pixel wide, run across in two ticks; then it fades. */
    private fun cut(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, body: Body, age: Float) {
        val run = ((age - SHUDDER_TICKS) / CUT_TICKS).coerceIn(0f, 1f)
        val fade = 1f - ((age - BURST) / (BODY_LIFE - BURST)).coerceIn(0f, 1f)
        if (run <= 0f || fade <= 0f) return
        val rightX = body.forwardZ.toDouble()
        val rightZ = -body.forwardX.toDouble()
        val front = body.at.add(body.forwardX * CUT_FRONT, 0.0, body.forwardZ * CUT_FRONT)
        val from = front.add(rightX * CUT_HALF, CUT_TOP, rightZ * CUT_HALF)
        val to = front.add(-rightX * CUT_HALF, CUT_BOTTOM, -rightZ * CUT_HALF)
        pixelLine(pose, buffer, camera, from, from.lerp(to, run.toDouble()), argbOf(CUT_COLOR, fade))
    }

    /** The figure goes to pieces: scraps all over where the body stood, flying apart from its middle. */
    private fun burst(body: Body) {
        val level = Minecraft.getInstance().level ?: return
        val r = level.random
        repeat(BURST_SCRAPS) {
            val height = 0.1 + r.nextDouble() * BODY_HEIGHT
            val side = (r.nextDouble() - 0.5) * BODY_WIDTH
            val depth = (r.nextDouble() - 0.5) * BODY_DEPTH
            val ox = body.forwardZ * side + body.forwardX * depth
            val oz = -body.forwardX * side + body.forwardZ * depth
            val speed = BURST_SPEED * (0.5 + r.nextDouble())
            val length = kotlin.math.sqrt(ox * ox + oz * oz).coerceAtLeast(0.05)
            level.addParticle(
                ModParticles.scrap(),
                body.at.x + ox, body.at.y + height, body.at.z + oz,
                ox / length * speed, r.nextDouble() * BURST_LIFT, oz / length * speed,
            )
        }
    }

    /** A figure this client had no body for: standing straight, arms down. */
    private val upright by lazy { AvatarRenderState() }

    private val SHEET = modId("textures/effect/paper_sheet.png")
    private val BODY = modId("textures/entity/paper_body.png")
    private val UP = Vec3(0.0, 1.0, 0.0)

    private const val SHEET_HALF = 0.2
    private const val TILT = 0.6
    private const val SPIN = 0.9
    private const val HIT_SCRAPS = 7
    private const val MISS_SCRAPS = 2
    private const val SCRAP_SPEED = 0.08

    /** What the vanilla living renderer does before it draws a player: its 15/16 scale, and the model's own drop. */
    private const val PLAYER_SCALE = 0.9375f
    private const val MODEL_DROP = -1.501

    private const val SHUDDER_TICKS = 3
    private const val SHUDDER = 0.04f
    private const val SHUDDER_RATE = 3f
    private const val CUT_TICKS = 2
    private const val BURST = SHUDDER_TICKS + CUT_TICKS
    private const val BODY_LIFE = BURST + 4

    private const val CUT_FRONT = 0.2
    private const val CUT_HALF = 0.34
    private const val CUT_TOP = 1.55
    private const val CUT_BOTTOM = 0.55
    private const val CUT_COLOR = 0xFFFFF4

    private const val BURST_SCRAPS = 48
    private const val BODY_HEIGHT = 1.75
    private const val BODY_WIDTH = 0.6
    private const val BODY_DEPTH = 0.3
    private const val BURST_SPEED = 0.09
    private const val BURST_LIFT = 0.09

    /** The face of a 64×64 skin: 8 pixels square at (8, 8). */
    private const val FACE_U0 = 8f / 64
    private const val FACE_U1 = 16f / 64
    private const val FACE_V0 = 8f / 64
    private const val FACE_V1 = 16f / 64
    /** Half a head, at the player's 15/16, and a hair out from it so the face does not fight the head. */
    private const val FACE_HALF = 0.235
    private const val FACE_OUT = 0.245
    private const val FACE_LIFE = 14
    private const val TAKE_FLIGHT = 9f
    private const val ARC = 0.4
    private const val SINK = 0.08
    private const val MASK_SETTLE = 6f
    private const val MASK_AHEAD = 0.35
    private const val PEEL_TICKS = 3f
    private const val PEEL = 0.15f
    private const val GRAVITY = 0.012
    private const val TUMBLE = 0.12f
    private const val FACE_SCRAPS = 9
    private const val NEAR_EYE = 0.35
    private const val NEAR_FADE = 0.5

}
