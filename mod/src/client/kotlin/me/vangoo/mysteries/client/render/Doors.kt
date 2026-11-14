package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.DoorKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The client half of the Door's doors (`pathways/door/abilities/Doorway.kt`) and of the passage a blink folds between two of them.
 *
 * A door is one packet, and from then on a function of its age ([DoorKind]'s phases): nothing of it
 * is streamed. It is built of three things, each drawn the way its matter asks:
 *
 * | part | what it is | drawn with |
 * |---|---|---|
 * | rim | the crack round the arch, and later its scar | [RenderTypes.lightning]: light, a pixel wide, pack-safe |
 * | leaves | the wall's own face, swinging out on its jambs — or a pale pane of the pathway's light | the wall's block textures ([RenderTypes.entityCutout]), or lightning |
 * | corridor | what lies behind: a passage deeper than the wall, arch after arch, to a glow | a shader of the mod's own (`shaders/core/rift`) |
 *
 * ## Pixels, not curves
 *
 * The arch is exactly a block wide and two tall, its top a half circle, and everything in it stands
 * on sixteenths of a block: the rim is a chain of pixels, the leaves are pixel-wide columns cut to the
 * arch, the corridor snaps its frames to the same grid. A door in a wall then lines up with the wall's
 * own texels, and its leaves at rest *are* the wall: each piece takes the texture of the block it was
 * cut from, at the very place on it.
 *
 * ## Under a shader pack
 *
 * A pack runs none of the mod's shaders (see [ShaderPacks]), so the corridor there is a plain glow in
 * the colour of where the door leads. The rim and the leaves are vanilla render types and stay as they are.
 */
object Doors {

    /** Where something flat and upright stands: the middle of its foot, and the way into it. */
    private open class Plane {
        var footing: Vec3 = Vec3.ZERO
        var intoX = 0f
        var intoZ = 1f
        /** The viewer's right, looking in: `into × up`. */
        val acrossX get() = -intoZ
        val acrossZ get() = intoX

        fun stand(at: Vec3, x: Float, z: Float) {
            footing = at
            intoX = x
            intoZ = z
        }
    }

    private class Door(payload: WorldEffectPayload.Door) : Plane() {
        init {
            stand(payload.footing, cos(payload.facing), sin(payload.facing))
        }

        val kind = payload.kind
        val hold = payload.holdTicks
        val depth = payload.depth
        val rim = RgbColor(payload.color and 0xFFFFFF)
        val palette = rim.shades(SHADES, SHADE_SPREAD).map { it.packed }.toIntArray()
        val tip = rim.shade(TIP_SHADE).packed
        val pane = rim.shade(PANE_SHADE).packed
        val far = payload.far and 0xFFFFFF
        val life = kind.life(hold)
        var age = 0
        /** The wall's face cut into the leaves' pieces — read from the world on the first frame. */
        var panels: List<Panel>? = null

        // The pose of the frame being drawn, set by [pose].
        var width = 1f
        var drawn = 1f
        var swing = 0f
        var scar = 0f
        var rimAlpha = 1f
        var tick = 0f

        /** Where the door stands in its life at [age] ticks: a pure function of it, phase after phase. */
        fun pose(age: Float) {
            tick = age
            var t = age
            width = if (kind.unfold > 0) clamp(t / kind.unfold) else 1f
            t -= kind.unfold
            drawn = if (kind.cut > 0) easeOut(clamp(t / kind.cut), 2) else 1f
            t -= kind.cut
            // Fast, then dead still: a hinge stops where it stops.
            swing = if (kind.open > 0) easeOut(clamp(t / kind.open), 3) else if (t >= 0) 1f else 0f
            t -= kind.open + hold
            // It gathers speed as it shuts, and lands.
            if (t > 0) swing = if (kind.close > 0) 1 - clamp(t / kind.close).let { it * it } else 0f
            t -= kind.close
            rimAlpha = if (t > 0) SCAR_ALPHA else 1f
            if (t > 0 && kind.fold > 0) width = 1 - clamp(t / kind.fold)
            t -= kind.fold
            scar = if (t > 0 && kind.scar > 0) clamp(t / kind.scar) else 0f
        }
    }

    /** A piece of a leaf: one pixel column of it within one block, and the texture of that block's face there. */
    private class Panel(
        val column: Int,
        val bottom: Float,
        val top: Float,
        val u0: Float,
        val u1: Float,
        val v0: Float,
        val v1: Float,
        val light: Int,
        val atlas: Identifier,
    )

    private class Passage(payload: WorldEffectPayload.Passage) {
        val from: Vec3 = payload.from
        val to: Vec3 = payload.to
        val palette = RgbColor(payload.color and 0xFFFFFF).shades(SHADES, SHADE_SPREAD).map { it.packed }.toIntArray()
        val rgb = payload.color and 0xFFFFFF
        val delay = payload.delay
        val flight = payload.flight
        val life = delay + flight + PASSAGE_FADE
        var age = 0
    }

    private val doors = mutableListOf<Door>()
    private val passages = mutableListOf<Passage>()

    fun accept(payload: WorldEffectPayload.Door) {
        doors += Door(payload)
    }

    fun accept(payload: WorldEffectPayload.Passage) {
        passages += Passage(payload)
    }

    fun tick() {
        doors.removeAll { ++it.age > it.life }
        passages.removeAll { ++it.age >= it.life }
    }

    fun clear() {
        doors.clear()
        passages.clear()
    }

    /**
     * Every door and passage standing this frame. Each kind of matter goes in as one batch; the batches
     * are written later in the frame, so they get their own copies of the lists.
     */
    fun submit(context: LevelRenderContext) {
        if (doors.isEmpty() && passages.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val plain = ShaderPacks.inUse
        val standing = doors.toList()
        val tunnels = passages.toList()
        for (door in standing) {
            door.pose(door.age + partial)
            if (door.kind.leaves == DoorKind.Leaves.WALL && door.panels == null) door.panels = cut(level, door)
        }

        val collector = context.submitNodeCollector()
        val poses = context.poseStack()
        collector.submitCustomGeometry(poses, LIGHT) { pose, buffer ->
            for (door in standing) {
                rim(pose, buffer, camera, door)
                if (door.kind.leaves == DoorKind.Leaves.PLAIN) paneLeaves(pose, buffer, camera, door)
                if (plain) corridorPlain(pose, buffer, camera, door)
            }
            for (tunnel in tunnels) tunnel(pose, buffer, camera, tunnel, partial)
        }
        val walled = standing.filter { it.swing > 0f && !it.panels.isNullOrEmpty() }
        if (walled.isNotEmpty()) {
            // ponytail: one atlas for every leaf — the blocks' own; a modded block on another atlas is drawn with the wrong one.
            collector.submitCustomGeometry(poses, RenderTypes.entityCutout(walled.first().panels!!.first().atlas)) { pose, buffer ->
                for (door in walled) wallLeaves(pose, buffer, camera, door)
            }
        }
        val deep = standing.filter { it.depth > 0f && it.swing > 0f }
        if (!plain && deep.isNotEmpty()) {
            collector.submitCustomGeometry(poses, RIFT) { pose, buffer ->
                for (door in deep) corridor(pose, buffer, camera, door)
            }
        }
    }

    // ── The rim ─────────────────────────────────────────────────────────────────────

    /**
     * The crack round the arch: it runs from both corners of the threshold up the jambs and round the
     * top until the halves meet, and once the door has shut it goes out the same way, pixel by pixel.
     * Its shade is laid out by height — deeper at the floor, lighter at the top — with a slow wave of
     * lightness running along it; while it is still being cut, its tip burns white.
     */
    private fun rim(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, door: Door) {
        if (door.width <= 0f) return
        val tip = if (door.drawn < 1f) door.tip else NO_TIP
        for (side in 0..1) {
            for ((pixels, alpha) in arrayOf(RIM[side] to door.rimAlpha, HALO[side] to door.rimAlpha * HALO_ALPHA)) {
                val count = pixels.size / 2
                val last = minOf(count, (door.drawn * count + 0.999f).toInt())
                outline(pose, buffer, camera, door, pixels, (door.scar * count).toInt(), last, door.width, alpha, door.palette, door.tick, tip)
            }
        }
    }

    /**
     * The pixels [first] until [last] of an outline standing in [plane], [width] of its breadth, shaded
     * from [palette] by height with a slow wave of lightness along it; while there is a [tip], the last
     * few pixels burn in it.
     */
    private fun outline(
        pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, plane: Plane, pixels: IntArray,
        first: Int, last: Int, width: Float, alpha: Float, palette: IntArray, tick: Float, tip: Int,
    ) {
        for (i in first until last) {
            val col = pixels[2 * i]
            val row = pixels[2 * i + 1]
            val wave = sin(i * PIXEL * WAVE_LENGTH - tick * WAVE_SPEED) * WAVE_AMPLITUDE
            val shade = (row.toFloat() / TOP_ROW * (SHADES - 1) + wave).toInt().coerceIn(0, SHADES - 1)
            val rgb = if (tip != NO_TIP && i >= last - TIP_PIXELS) tip else palette[shade]
            val x0 = col * PIXEL * width
            val x1 = (col + 1) * PIXEL * width
            val y0 = row * PIXEL
            val y1 = y0 + PIXEL
            glow(pose, buffer, argb(rgb, alpha), plane, camera, x0, y0, RIM_LIFT, x1, y0, RIM_LIFT, x1, y1, RIM_LIFT, x0, y1, RIM_LIFT)
        }
    }

    // ── The leaves ──────────────────────────────────────────────────────────────────

    /**
     * Where a leaf's edge at [edge] across the closed door stands once the leaf has swung open by the
     * angle whose cosine and sine are [cos] and [sin]: across the door ([swungX]) and out of its plane,
     * towards whoever it opens to ([swungOut]). Columns left of the middle hang on the left jamb, the
     * rest on the right one — [column] says which.
     */
    private fun hinge(column: Int) = if (column < 0) -HALF else HALF

    private fun swungX(column: Int, edge: Float, cos: Float) = hinge(column) + (edge - hinge(column)) * cos

    private fun swungOut(column: Int, edge: Float, sin: Float) = abs(edge - hinge(column)) * sin + LEAF_LIFT

    /** A pale pane of the pathway's light in place of leaves: space itself swinging, its outer edges brighter. */
    private fun paneLeaves(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, door: Door) {
        if (door.width <= 0f) return
        val angle = door.swing * MAX_SWING
        val cos = cos(angle)
        val sin = sin(angle)
        val w = door.width
        for (c in -HALF_PX until HALF_PX) {
            val e0 = c * PIXEL
            val e1 = e0 + PIXEL
            val top = TOP[c + HALF_PX] * PIXEL
            val alpha = if (c == -HALF_PX || c == HALF_PX - 1) PANE_EDGE_ALPHA else PANE_ALPHA
            val x0 = swungX(c, e0, cos) * w
            val x1 = swungX(c, e1, cos) * w
            val o0 = swungOut(c, e0, sin) * w
            val o1 = swungOut(c, e1, sin) * w
            glow(pose, buffer, argb(door.pane, alpha), door, camera, x0, 0f, o0, x1, 0f, o1, x1, top, o1, x0, top, o0)
        }
    }

    private fun wallLeaves(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, door: Door) {
        val panels = door.panels ?: return
        val angle = door.swing * MAX_SWING
        val cos = cos(angle)
        val sin = sin(angle)
        for (panel in panels) {
            val c = panel.column
            val e0 = c * PIXEL
            val e1 = e0 + PIXEL
            val dir = if (c < 0) 1f else -1f
            val x0 = swungX(c, e0, cos)
            val x1 = swungX(c, e1, cos)
            val out0 = swungOut(c, e0, sin)
            val out1 = swungOut(c, e1, sin)
            // The leaf's face turns with it: along it from the hinge, then square to that, outwards.
            val alongX = dir * (door.acrossX * cos) - door.intoX * sin
            val alongZ = dir * (door.acrossZ * cos) - door.intoZ * sin
            val nx = -dir * alongZ
            val nz = dir * alongX
            textured(pose, buffer, door, camera, x0, panel.bottom, out0, panel.u0, panel.v0, panel.light, nx, nz)
            textured(pose, buffer, door, camera, x1, panel.bottom, out1, panel.u1, panel.v0, panel.light, nx, nz)
            textured(pose, buffer, door, camera, x1, panel.top, out1, panel.u1, panel.v1, panel.light, nx, nz)
            textured(pose, buffer, door, camera, x0, panel.top, out0, panel.u0, panel.v1, panel.light, nx, nz)
        }
    }

    /**
     * The wall's face inside the arch, cut into the leaves' pieces: per pixel column, per block it
     * crosses, the face texture of that block at exactly that place on it — so at rest the leaves are
     * the wall. Where the arch reaches past the wall (a wall one block high), there is no leaf.
     */
    private fun cut(level: ClientLevel, door: Door): List<Panel> {
        val face = Direction.getApproximateNearest(-door.intoX.toDouble(), 0.0, -door.intoZ.toDouble())
        val panels = ArrayList<Panel>()
        for (c in -HALF_PX until HALF_PX) {
            val mid = (c + 0.5f) * PIXEL
            // Where the column lies across the face, in world terms: the viewer's right is the texture's.
            val h0 = door.footing.x * door.acrossX + door.footing.z * door.acrossZ + c * PIXEL
            val base = floor(h0 + PIXEL / 2)
            val top = TOP[c + HALF_PX] * PIXEL
            var bottom = 0f
            while (bottom < top) {
                val worldBottom = door.footing.y + bottom
                val blockY = floor(worldBottom + 1e-4)
                val upTo = minOf(top, (blockY + 1 - door.footing.y).toFloat())
                val pos = BlockPos.containing(
                    door.footing.x + door.acrossX * mid + door.intoX * INSIDE,
                    worldBottom + 1e-3,
                    door.footing.z + door.acrossZ * mid + door.intoZ * INSIDE,
                )
                val state = level.getBlockState(pos)
                if (!state.isAir) {
                    val sprite = faceSprite(state, face)
                    val u0 = (h0 - base).toFloat()
                    val v0 = (1 - (worldBottom - blockY)).toFloat()
                    val v1 = (1 - (door.footing.y + upTo - blockY)).toFloat()
                    panels += Panel(
                        c, bottom, upTo,
                        sprite.u(u0), sprite.u(u0 + PIXEL), sprite.v(v0), sprite.v(v1),
                        LightCoordsUtil.getLightCoords(level, pos.relative(face)),
                        sprite.atlasLocation(),
                    )
                }
                bottom = upTo
            }
        }
        return panels
    }

    private val parts = ArrayList<BlockStateModelPart>()

    /** The texture a block shows on [face]: its first untinted quad there, or its particle texture. */
    private fun faceSprite(state: BlockState, face: Direction): TextureAtlasSprite {
        val model = Minecraft.getInstance().modelManager.blockStateModelSet.get(state)
        parts.clear()
        model.collectParts(RandomSource.create(state.hashCode().toLong()), parts)
        for (part in parts) {
            for (quad in part.getQuads(face)) if (!quad.materialInfo().isTinted) return quad.materialInfo().sprite()
        }
        return model.particleMaterial().sprite()
    }

    private fun TextureAtlasSprite.u(t: Float) = u0 + (u1 - u0) * t
    private fun TextureAtlasSprite.v(t: Float) = v0 + (v1 - v0) * t

    // ── The corridor ────────────────────────────────────────────────────────────────

    /**
     * The doorway's own face, cut to the arch in pixel columns, and the shader draws what lies behind
     * it. A door in the air has no leaves, so its corridor widens out of a line between the jambs
     * instead; a door in a wall is open wherever its leaves are not.
     */
    private inline fun columns(door: Door, column: (c: Int, x0: Float, x1: Float, top: Float) -> Unit) {
        val aperture = if (door.kind.leaves == DoorKind.Leaves.NONE) door.swing * HALF else HALF
        for (c in -HALF_PX until HALF_PX) {
            val x0 = maxOf(c * PIXEL, -aperture)
            val x1 = minOf((c + 1) * PIXEL, aperture)
            if (x1 > x0) column(c, x0, x1, TOP[c + HALF_PX] * PIXEL)
        }
    }

    private fun corridor(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, door: Door) {
        // It breathes: the far end draws in and out, slowly.
        val depth = (door.depth * (1 + BREATH * sin(door.tick * BREATH_SPEED)) * 64).toInt().coerceIn(0, Short.MAX_VALUE.toInt())
        val clock = (door.tick * 16).toInt().coerceIn(0, Short.MAX_VALUE.toInt())
        val color = argb(door.far, 1f)
        val ox = (door.footing.x - camera.x).toFloat()
        val oy = (door.footing.y - camera.y).toFloat()
        val oz = (door.footing.z - camera.z).toFloat()
        fun vertex(x: Float, y: Float) {
            buffer.addVertex(pose, ox + door.acrossX * x - door.intoX * CORRIDOR_LIFT, oy + y, oz + door.acrossZ * x - door.intoZ * CORRIDOR_LIFT)
                .setColor(color)
                .setUv(x, y)
                .setUv1(depth, 0)
                .setUv2(clock, 0)
                .setNormal(door.intoX, 0f, door.intoZ)
        }
        columns(door) { _, x0, x1, top ->
            vertex(x0, 0f)
            vertex(x1, 0f)
            vertex(x1, top)
            vertex(x0, top)
        }
    }

    /** Under a shader pack: no corridor, only a glow in the colour of where the door leads. */
    private fun corridorPlain(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, door: Door) {
        if (door.depth <= 0f || door.swing <= 0f) return
        val argb = argb(door.far, PLAIN_CORRIDOR_ALPHA)
        columns(door) { _, x0, x1, top ->
            glow(pose, buffer, argb, door, camera, x0, 0f, CORRIDOR_LIFT, x1, 0f, CORRIDOR_LIFT, x1, top, CORRIDOR_LIFT, x0, top, CORRIDOR_LIFT)
        }
    }

    // ── The passage ─────────────────────────────────────────────────────────────────

    private val arch = Plane()

    /**
     * The passage a blink folds between its two doors: the door's own outline standing again and again
     * along the way, a faint line along its floor on either side and one along its crown. Not a tube —
     * a corridor of door frames, sparse: the same corridor the Door's doors have behind them.
     *
     * It builds itself out from the first door to the second while they open; while the Traveller rushes
     * along it each arch flares as they pass; then it goes out behind them, from where they set off.
     */
    private fun tunnel(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, passage: Passage, partial: Float) {
        val span = passage.to.subtract(passage.from)
        val length = span.length()
        if (length < ARCH_SPACING) return
        val t = passage.age + partial
        val built = length * (if (passage.delay > 0) easeOut(clamp(t / passage.delay), 2) else 1f)
        val gone = length * clamp((t - passage.delay - passage.flight) / PASSAGE_FADE)
        if (built <= gone) return
        // Where the Traveller is along it — the same curve the server pushes them by.
        val at = if (t < passage.delay) -length
        else length * WorldEffectPayload.Passage.travelled((t - passage.delay) / passage.flight)

        val flat = Vec3(span.x, 0.0, span.z).let { if (it.lengthSqr() < 1e-6) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        val count = RIM[0].size / 2
        var s = ARCH_SPACING
        while (s < length - ARCH_SPACING / 2) {
            val foot = passage.from.add(span.scale(s / length))
            val near = near(foot, camera)
            if (s in gone..built && near > 0f) {
                val flare = (1 - abs(s - at) / PASS_REACH).coerceAtLeast(0.0).toFloat()
                arch.stand(foot, flat.x.toFloat(), flat.z.toFloat())
                for (side in 0..1) {
                    outline(pose, buffer, camera, arch, RIM[side], 0, count, 1f, (ARCH_ALPHA + PASS_FLARE * flare) * near, passage.palette, t, NO_TIP)
                }
            }
            s += ARCH_SPACING
        }

        // The lines go a piece at a time, so the piece passing the eye can go out on its own.
        val across = Vec3(-flat.z, 0.0, flat.x).scale(HALF + PIXEL / 2.0)
        val floor = Vec3(0.0, PIXEL / 2.0, 0.0)
        val crown = Vec3(0.0, (TOP_ROW + 0.5) * PIXEL.toDouble(), 0.0)
        var piece = gone
        while (piece < built) {
            val next = minOf(built, piece + LINE_PIECE)
            val a = passage.from.add(span.scale(piece / length))
            val b = passage.from.add(span.scale(next / length))
            val near = near(a.add(b).scale(0.5), camera)
            if (near > 0f) {
                val argb = argb(passage.rgb, LINE_ALPHA * near)
                line(pose, buffer, camera, a.add(across).add(floor), b.add(across).add(floor), argb)
                line(pose, buffer, camera, a.subtract(across).add(floor), b.subtract(across).add(floor), argb)
                line(pose, buffer, camera, a.add(crown), b.add(crown), argb)
            }
            piece = next
        }
    }

    /**
     * How much of a piece of the passage standing on [foot] is left at the eye: none right at it, all of it
     * a few blocks off. Rushing along it, the caster passes inside every arch — a pixel at arm's length
     * is a slab across the view.
     */
    private fun near(foot: Vec3, camera: Vec3): Float {
        val d = foot.add(0.0, 1.0, 0.0).distanceTo(camera).toFloat()
        val t = clamp((d - NEAR_START) / (NEAR_END - NEAR_START))
        return t * t * (3 - 2 * t)
    }

    /** A hairline from [from] to [to], a pixel wide, turned to the eye. */
    private fun line(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, from: Vec3, to: Vec3, argb: Int) {
        val a = from.subtract(camera)
        val b = to.subtract(camera)
        if (a.distanceToSqr(b) < 1e-6) return
        val side = b.subtract(a).cross(a.add(b)).let { if (it.lengthSqr() < 1e-9) Vec3(0.0, PIXEL / 2.0, 0.0) else it.normalize().scale(PIXEL / 2.0) }
        quad(
            pose, buffer, argb,
            (a.x - side.x).toFloat(), (a.y - side.y).toFloat(), (a.z - side.z).toFloat(),
            (b.x - side.x).toFloat(), (b.y - side.y).toFloat(), (b.z - side.z).toFloat(),
            (b.x + side.x).toFloat(), (b.y + side.y).toFloat(), (b.z + side.z).toFloat(),
            (a.x + side.x).toFloat(), (a.y + side.y).toFloat(), (a.z + side.z).toFloat(),
        )
    }

    // ── Writing ─────────────────────────────────────────────────────────────────────

    /** A quad of light in the door's own terms: across it, up it, and out of its plane towards its front. */
    private fun glow(
        pose: PoseStack.Pose, buffer: VertexConsumer, argb: Int, door: Plane, camera: Vec3,
        ax: Float, ay: Float, aOut: Float, bx: Float, by: Float, bOut: Float,
        cx: Float, cy: Float, cOut: Float, dx: Float, dy: Float, dOut: Float,
    ) {
        val ox = (door.footing.x - camera.x).toFloat()
        val oy = (door.footing.y - camera.y).toFloat()
        val oz = (door.footing.z - camera.z).toFloat()
        quad(
            pose, buffer, argb,
            ox + door.acrossX * ax - door.intoX * aOut, oy + ay, oz + door.acrossZ * ax - door.intoZ * aOut,
            ox + door.acrossX * bx - door.intoX * bOut, oy + by, oz + door.acrossZ * bx - door.intoZ * bOut,
            ox + door.acrossX * cx - door.intoX * cOut, oy + cy, oz + door.acrossZ * cx - door.intoZ * cOut,
            ox + door.acrossX * dx - door.intoX * dOut, oy + dy, oz + door.acrossZ * dx - door.intoZ * dOut,
        )
    }

    /** Lightning culls its back faces, so a quad goes in whichever winding faces the eye — the origin here. */
    private fun quad(
        pose: PoseStack.Pose, buffer: VertexConsumer, argb: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = dx - ax; val vy = dy - ay; val vz = dz - az
        val facing = (uy * vz - uz * vy) * ax + (uz * vx - ux * vz) * ay + (ux * vy - uy * vx) * az < 0
        buffer.addVertex(pose, ax, ay, az).setColor(argb)
        if (facing) {
            buffer.addVertex(pose, bx, by, bz).setColor(argb)
            buffer.addVertex(pose, cx, cy, cz).setColor(argb)
            buffer.addVertex(pose, dx, dy, dz).setColor(argb)
        } else {
            buffer.addVertex(pose, dx, dy, dz).setColor(argb)
            buffer.addVertex(pose, cx, cy, cz).setColor(argb)
            buffer.addVertex(pose, bx, by, bz).setColor(argb)
        }
    }

    private fun textured(
        pose: PoseStack.Pose, buffer: VertexConsumer, door: Door, camera: Vec3,
        x: Float, y: Float, out: Float, u: Float, v: Float, light: Int, nx: Float, nz: Float,
    ) {
        buffer.addVertex(
            pose,
            (door.footing.x - camera.x).toFloat() + door.acrossX * x - door.intoX * out,
            (door.footing.y - camera.y).toFloat() + y,
            (door.footing.z - camera.z).toFloat() + door.acrossZ * x - door.intoZ * out,
        )
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, 0f, nz)
    }

    private fun argb(rgb: Int, alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24 or (rgb and 0xFFFFFF)

    private fun clamp(t: Float) = t.coerceIn(0f, 1f)

    /** 0 → 1, fast out of the start and settling: `1 − (1−t)ⁿ`. */
    private fun easeOut(t: Float, power: Int): Float {
        var inverse = 1f
        repeat(power) { inverse *= 1 - t }
        return 1 - inverse
    }

    // ── The arch, in pixels ─────────────────────────────────────────────────────────

    private const val PIXEL = 1f / 16
    /** Half the arch's width: it is exactly a block across, like any door. */
    private const val HALF = 0.5f
    private const val HALF_PX = 8
    /** The height its top starts curving at: below it the jambs are straight, above it a half circle. */
    private const val SPRING_PX = 24
    private const val TOP_ROW = SPRING_PX + HALF_PX

    /** How high each of the 16 columns of the opening reaches, in pixels. */
    private val TOP = IntArray(2 * HALF_PX) { i ->
        val x = i - HALF_PX + 0.5
        SPRING_PX + floor(sqrt(HALF_PX * HALF_PX - x * x)).toInt()
    }

    /**
     * The pixels just outside the opening, for each side (right, then left), from the threshold's
     * corner up to the top, as pairs of column and row: up the jamb, then round the half circle at
     * [radius] pixels from its centre, each pixel once.
     */
    private fun outline(radius: Double): Array<IntArray> {
        val right = ArrayList<Int>()
        val jamb = floor(radius).toInt()
        for (row in 0 until SPRING_PX) right += listOf(jamb, row)
        val steps = 256
        for (i in 0..steps) {
            val angle = PI / 2 * i / steps
            val col = floor(radius * cos(angle)).toInt()
            val row = SPRING_PX + floor(radius * sin(angle)).toInt()
            if (right[right.size - 2] != col || right[right.size - 1] != row) right += listOf(col, row)
        }
        val pixels = right.toIntArray()
        val left = IntArray(pixels.size) { if (it % 2 == 0) -1 - pixels[it] else pixels[it] }
        return arrayOf(pixels, left)
    }

    private val RIM = outline(HALF_PX + 0.5)
    /** One pixel further out and dimmer: the glow round the crack, in a step, not a blur. */
    private val HALO = outline(HALF_PX + 1.5)

    /** How far each part stands out of the wall's face, so none of them fights it for depth. */
    private const val CORRIDOR_LIFT = 0.008f
    private const val LEAF_LIFT = 0.012f
    private const val RIM_LIFT = 0.016f
    /** How far behind the face a leaf's block is looked for. */
    private const val INSIDE = 0.25

    /**
     * How far the leaves swing before they stop dead. Short of square, so they lean over the opening
     * and not over the jambs: past 90° a leaf hides the glowing cut it was cut along.
     */
    private const val MAX_SWING = (80 * PI / 180).toFloat()
    private const val SHADES = 5
    private const val SHADE_SPREAD = 0.42
    private const val TIP_SHADE = 0.7
    private const val TIP_PIXELS = 2
    private const val HALO_ALPHA = 0.3f
    private const val SCAR_ALPHA = 0.55f
    private const val WAVE_LENGTH = 1.6f
    private const val WAVE_SPEED = 0.11f
    private const val WAVE_AMPLITUDE = 1.1f

    private const val PANE_SHADE = 0.3
    private const val PANE_ALPHA = 0.3f
    private const val PANE_EDGE_ALPHA = 0.6f
    private const val PLAIN_CORRIDOR_ALPHA = 0.35f
    /** The passage: an arch every this many blocks, faint, flaring as the Traveller passes within reach. */
    private const val ARCH_SPACING = 1.5
    private const val ARCH_ALPHA = 0.22f
    private const val PASS_FLARE = 0.6f
    private const val PASS_REACH = 2.0
    private const val LINE_ALPHA = 0.18f
    /** How long the passage takes to go out behind the Traveller. */
    private const val PASSAGE_FADE = 16
    /** The lines go a piece this long at a time. */
    private const val LINE_PIECE = 0.75
    /** The passage is gone nearer the eye than the first, whole beyond the second (blocks). */
    private const val NEAR_START = 1.2f
    private const val NEAR_END = 3.5f
    private const val NO_TIP = -1
    private const val BREATH = 0.05f
    private const val BREATH_SPEED = 0.12f

    private val LIGHT: RenderType = RenderTypes.lightning()

    /**
     * The corridor: the vanilla layouts of a fogged world pass, a shader of our own, premultiplied
     * blending, both faces, depth tested but not written — reversed depth, as everywhere on 26.x.
     */
    private val PIPELINE: RenderPipeline = RenderPipeline.builder()
        .withLocation(modId("pipeline/rift"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withVertexShader(modId("core/rift"))
        .withFragmentShader(modId("core/rift"))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
        .build()

    /** `RenderType.create` is package-private; the access widener opens it. */
    private val RIFT: RenderType = RenderType.create(
        "mysteries_above_rift",
        RenderSetup.builder(PIPELINE).sortOnUpload().createRenderSetup(),
    )
}
