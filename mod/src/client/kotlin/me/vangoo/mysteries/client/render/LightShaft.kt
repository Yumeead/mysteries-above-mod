package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A shaft of light as geometry: one strip along the beam, turned about its own axis to face the
 * camera, and a shader of the mod's own (`shaders/core/light_shaft`) that lights it as a volume of
 * hazy air — the brightness of each pixel is the lit air its view ray crosses.
 *
 * ## Why not particles, and why not the beacon
 *
 * A beam sown out of particles is a swarm of points however densely it is sown; the beacon's beam is
 * a hard-edged prism. Sunlight through a gap in the clouds is neither: it has no surface, its edges
 * are soft, its core is white, dust drifts in it, and it is lost in the air towards its source.
 * All of that is the shader; the strip only says where the volume stands.
 *
 * ## What the strip cannot say, the shader is told
 *
 * A strip's width faces the eye at every point, so the shaft is as wide on screen as a cylinder would
 * be — but looked at along its length, a ray crosses far more lit air than from the side. The axis
 * rides in each vertex's normal, and the shader brightens the shaft by how slanting its view of it is.
 *
 * Where it meets the ground the strip would end in a hard line, as any geometry does under the depth
 * test. So the frame's depth after the opaque terrain is copied ([captureDepth]) and the shader thins
 * the haze over the last of the air in front of whatever stands behind it.
 *
 * ## Why premultiplied blending
 *
 * Additive light, like the spirit light's, saturates to white against the noon sky and loses its
 * colour. The shaft both adds its light and veils what is behind it, most of all at its heart, so it
 * stays gold by day and glows at night.
 */
object LightShaft {

    /**
     * The vanilla layouts of a fogged world pass plus one sampler for the scene's depth, a shader of
     * our own, premultiplied blending, both faces, depth tested but not written — reversed depth, as
     * everywhere on 26.x.
     */
    private val PIPELINE: RenderPipeline = RenderPipeline.builder()
        .withLocation(modId("pipeline/light_shaft"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader(modId("core/light_shaft"))
        .withFragmentShader(modId("core/light_shaft"))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
        .build()

    private val DEPTH = modId("light_shaft_depth")

    /** `RenderType.create` is package-private; the access widener opens it — there is no public way to a type of one's own. */
    val TYPE: RenderType = RenderType.create(
        "mysteries_above_light_shaft",
        RenderSetup.builder(PIPELINE)
            .withTexture("Sampler0", DEPTH) { RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) }
            .sortOnUpload()
            .createRenderSetup(),
    )

    /** Whether a depth copy exists: before the first one the texture has no view, and binding it would throw. */
    val ready: Boolean get() = SceneDepth.ready

    /**
     * Copies the frame's depth as it stands — call it once the opaque terrain is drawn and before the
     * shafts are. A pass may not read the depth buffer it is tested against, so the shader reads a copy.
     */
    fun captureDepth() = SceneDepth.capture()

    /**
     * Writes one shaft from [from] to [to] in [rgb] at [intensity]: its haze [sourceRadius] blocks across
     * at its source and [radius] where it ends, widening or narrowing evenly in between.
     * [age] is how many ticks it has stood (its dust drifts by that clock, not the day's, which wraps);
     * [front] is how many blocks its leading end thins over while it is still on its way.
     * Positions go in relative to [camera]: the level's pose stack is the identity at the eye.
     */
    fun draw(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        camera: Vec3,
        from: Vec3,
        to: Vec3,
        sourceRadius: Double,
        radius: Double,
        rgb: Int,
        intensity: Float,
        age: Float,
        front: Double,
    ) {
        val span = to.subtract(from)
        val length = span.length()
        if (length < MIN_LENGTH || intensity <= 0f) return
        val shape = Shape(
            color = (intensity.coerceIn(0f, 1f) * 255).toInt() shl 24 or (rgb and 0xFFFFFF),
            clock = (age * 16).toInt().coerceIn(0, Short.MAX_VALUE.toInt()),
            front = short(front),
            size = short(length),
            axis = span.scale(1 / length),
        )
        strip(from, to, camera, sourceRadius, radius) { last, next, before, after ->
            vertex(buffer, pose, last.start.subtract(camera), shape, last.radius, -1f, before)
            vertex(buffer, pose, last.end.subtract(camera), shape, last.radius, 1f, before)
            vertex(buffer, pose, next.end.subtract(camera), shape, next.radius, 1f, after)
            vertex(buffer, pose, next.start.subtract(camera), shape, next.radius, -1f, after)
        }
    }

    /**
     * Under a shader pack the shaft's own shader is never run (see [ShaderPacks]), so there it is
     * drawn the way the pack already draws vanilla lightning: the same strip, its haze as vertex
     * colour — full along the axis, gone at the rims and at both ends — and the pack lights it.
     */
    val PLAIN: RenderType = RenderTypes.lightning()

    /** [draw] for [PLAIN]: the same shaft, no shader of the mod's own. */
    fun drawPlain(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        camera: Vec3,
        from: Vec3,
        to: Vec3,
        sourceRadius: Double,
        radius: Double,
        rgb: Int,
        intensity: Float,
    ) {
        val length = from.distanceTo(to)
        if (length < MIN_LENGTH || intensity <= 0f) return
        val rim = rgb and 0xFFFFFF
        fun axis(along: Double): Int {
            val ends = if (along <= 0.0 || along >= length) 0f else 1f
            return (intensity.coerceIn(0f, 1f) * PLAIN_ALPHA * ends * 255).toInt() shl 24 or rim
        }
        strip(from, to, camera, sourceRadius, radius) { last, next, before, after ->
            val lastAxis = last.start.add(last.end).scale(0.5).subtract(camera)
            val nextAxis = next.start.add(next.end).scale(0.5).subtract(camera)
            // Rim to axis, then axis to the other rim: the light falls off across in two quads.
            for ((a, d) in listOf(last.start to next.start, last.end to next.end)) {
                plainQuad(buffer, pose, a.subtract(camera) to rim, lastAxis to axis(before), nextAxis to axis(after), d.subtract(camera) to rim)
            }
        }
    }

    /** Lightning culls its back faces, so the quad goes in whichever order faces the eye — the origin here. */
    private fun plainQuad(buffer: VertexConsumer, pose: PoseStack.Pose, a: Pair<Vec3, Int>, b: Pair<Vec3, Int>, c: Pair<Vec3, Int>, d: Pair<Vec3, Int>) {
        val faces = b.first.subtract(a.first).cross(d.first.subtract(a.first)).dot(a.first) < 0
        for ((at, color) in if (faces) listOf(a, b, c, d) else listOf(a, d, c, b)) {
            buffer.addVertex(pose, at.x.toFloat(), at.y.toFloat(), at.z.toFloat()).setColor(color)
        }
    }

    /**
     * Walks the strip from [from] to [to] segment by segment, each end turned to the camera on its own:
     * one quad for a long beam seen from near one end would twist away from the eye at the other. It
     * leaves its source thin and widens over the first segment — a ray out of a hand, not a slab beside
     * the eye. Its radius runs evenly from [sourceRadius] to [radius]. [quad] gets the two rims of a
     * segment and how far along the shaft each stands.
     */
    private inline fun strip(from: Vec3, to: Vec3, camera: Vec3, sourceRadius: Double, radius: Double, quad: (Rim, Rim, Double, Double) -> Unit) {
        val span = to.subtract(from)
        val length = span.length()
        val axis = span.scale(1 / length)
        val segments = ceil(length / SEGMENT).toInt().coerceAtLeast(1)
        var last = rim(from, axis, camera, sourceRadius * SOURCE_WIDTH)
        for (i in 1..segments) {
            val t = i.toDouble() / segments
            val next = rim(from.add(span.scale(t)), axis, camera, sourceRadius + (radius - sourceRadius) * t)
            quad(last, next, length * (i - 1) / segments, length * t)
            last = next
        }
    }

    /** What every vertex of one shaft carries besides where it stands. */
    private class Shape(val color: Int, val clock: Int, val front: Int, val size: Int, val axis: Vec3)

    private class Rim(val start: Vec3, val end: Vec3, val radius: Double)

    /** The two rims of the shaft at [point]: across the axis and across the line to the eye. */
    private fun rim(point: Vec3, axis: Vec3, camera: Vec3, radius: Double): Rim {
        var side = axis.cross(camera.subtract(point))
        // Looked at straight along the axis there is no "across the view"; any perpendicular will do.
        if (side.lengthSqr() < 1e-6) side = axis.cross(if (abs(axis.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0))
        val offset = side.normalize().scale(radius)
        return Rim(point.subtract(offset), point.add(offset), radius)
    }

    /**
     * UV0 — across the shaft (−1 at one rim, 1 at the other) and blocks from its source; UV1 — its
     * clock in sixteenths of a tick and how far its front thins; UV2 — its length and its radius there;
     * the normal — its axis. Lengths go in 1/64 of a block, as shorts.
     */
    private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, at: Vec3, shape: Shape, radius: Double, across: Float, along: Double) {
        buffer.addVertex(pose, at.x.toFloat(), at.y.toFloat(), at.z.toFloat())
            .setColor(shape.color)
            .setUv(across, along.toFloat())
            .setUv1(shape.clock, shape.front)
            .setUv2(shape.size, short(radius))
            .setNormal(shape.axis.x.toFloat(), shape.axis.y.toFloat(), shape.axis.z.toFloat())
    }

    private fun short(blocks: Double): Int = (blocks * 64).toInt().coerceIn(0, Short.MAX_VALUE.toInt())

    /** The frame's depth, copied into a texture the shaft's shader samples as `Sampler0`. */
    private object SceneDepth : AbstractTexture() {

        private var registered = false

        val ready: Boolean get() = textureView != null

        fun capture() {
            val source = Minecraft.getInstance().gameRenderer.mainRenderTarget().depthTexture ?: return
            val width = source.getWidth(0)
            val height = source.getHeight(0)
            val device = RenderSystem.getDevice()
            val copy = texture?.takeIf { !it.isClosed && it.getWidth(0) == width && it.getHeight(0) == height }
                ?: run {
                    releaseTextures()
                    device.createTexture("Light shaft depth", COPY_USAGE, source.format, width, height, 1, 1).also {
                        texture = it
                        textureView = device.createTextureView(it)
                    }
                }
            device.createCommandEncoder().copyTextureToTexture(source, copy, 0, 0, 0, 0, 0, width, height)
            if (!registered) {
                Minecraft.getInstance().textureManager.register(DEPTH, this)
                registered = true
            }
        }

        /** What vanilla gives a depth target: a copy is written by blitting into it, so it must be attachable. */
        private const val COPY_USAGE = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
    }

    private const val MIN_LENGTH = 0.05
    /** Blocks per segment of the strip. */
    private const val SEGMENT = 3.0
    /** How wide a shaft is at its source, as a share of its width. */
    private const val SOURCE_WIDTH = 0.25
    /** How much light the plain shaft adds along its axis: lightning adds up, and a pack brightens it further. */
    private const val PLAIN_ALPHA = 0.55f
}
