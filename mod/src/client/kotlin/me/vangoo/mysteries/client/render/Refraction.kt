package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Bent air: a disc that shows the frame behind it displaced — the material of anything made of air
 * squeezed hard enough to bend light. The Fool's air bullet first; the Tyrant's wind blades and the
 * Door's opening are to take the same discs.
 *
 * ## Why the frame is copied
 *
 * A pass may not read the target it draws into, so the colour of the frame is copied ([captureColor])
 * once the solid entities are in it and before the translucent ones are drawn — which is when these
 * discs are. The shader reads the copy a little off from its own pixel; where the disc thins out it
 * lets the real frame through, so its edge never shows.
 *
 * ## Two shapes
 *
 * A [Kind.LENS] is the round itself: the scene behind it swells from its centre, as through a drop of
 * water, and a thin pale rim catches the light at its edge — what still shows where the scene behind
 * is even (open sky, a smooth wall). A [Kind.WAVE] is a ring of bent air running outwards — the shot
 * leaving the finger, the burst where it strikes: the disc's own [front] says how far out the ring is.
 *
 * Smooth, not pixel-snapped — the one exception to the mod's pixel rule besides the light shaft: bent
 * air has no edge to put on the grid. Under a shader pack this shader is never run ([ShaderPacks]);
 * a caller draws the rim alone there instead.
 */
object Refraction {

    /**
     * The vanilla layouts of a fogged world pass plus one sampler for the frame's colour, a shader of
     * our own, plain translucency, both faces, depth tested but not written — reversed, as on all 26.x.
     */
    private val PIPELINE: RenderPipeline = RenderPipeline.builder()
        .withLocation(modId("pipeline/refraction"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader(modId("core/refraction"))
        .withFragmentShader(modId("core/refraction"))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .build()

    private val COLOR = modId("refraction_scene")

    /** `RenderType.create` is package-private; the access widener opens it. */
    val TYPE: RenderType = RenderType.create(
        "mysteries_above_refraction",
        RenderSetup.builder(PIPELINE)
            .withTexture("Sampler0", COLOR) { RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) }
            .sortOnUpload()
            .createRenderSetup(),
    )

    private val SceneColor = FrameCopy(COLOR, "Refraction scene") { Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture }

    /** Whether a colour copy exists: before the first one the texture has no view, and binding it would throw. */
    val ready: Boolean get() = SceneColor.ready

    /** Copies the frame's colour as it stands — after the solid entities, before the discs are drawn. */
    fun captureColor() = SceneColor.capture()

    enum class Kind { LENS, WAVE }

    /**
     * Writes one disc of bent air [radius] blocks across at [center], turned to the camera, bent with
     * [strength] (0 → 1) and rimmed in [rim]. For a [Kind.WAVE], [front] is how far out its ring has run,
     * as a share of [radius]. Positions go in relative to [camera].
     */
    fun disc(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        camera: Vec3,
        center: Vec3,
        radius: Double,
        kind: Kind,
        strength: Float,
        rim: Int,
        front: Float = 1f,
    ) {
        if (strength <= 0f || radius <= 0.0) return
        val (side, up) = facing(center, camera, radius)
        val at = center.subtract(camera)
        val color = (strength.coerceIn(0f, 1f) * 255).toInt() shl 24 or (rim and 0xFFFFFF)
        val shape = (front.coerceIn(0f, 1f) * FRONT_STEPS).toInt()
        for ((u, v) in CORNERS) {
            val p = at.add(side.scale(u.toDouble())).add(up.scale(v.toDouble()))
            buffer.addVertex(pose, p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
                .setColor(color)
                .setUv(u, v)
                .setUv1(kind.ordinal, shape)
                .setUv2(0, 0)
                .setNormal(0f, 0f, 1f)
        }
    }

    /**
     * The rim of a disc alone, a hairline ring of light — what is left of the bent air where this shader
     * cannot run. For [net.minecraft.client.renderer.rendertype.RenderTypes.lightning].
     */
    fun rimOnly(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, center: Vec3, radius: Double, argb: Int) {
        val (side, up) = facing(center, camera, radius)
        val steps = RIM_STEPS
        var last = center.add(up)
        for (i in 1..steps) {
            val angle = i * 2 * Math.PI / steps
            val next = center.add(side.scale(Math.sin(angle))).add(up.scale(Math.cos(angle)))
            pixelLine(pose, buffer, camera, last, next, argb)
            last = next
        }
    }

    /** Two axes of a disc [radius] blocks across at [center], square to the line from the camera. */
    private fun facing(center: Vec3, camera: Vec3, radius: Double): Pair<Vec3, Vec3> {
        val view = center.subtract(camera)
        var side = view.cross(UP)
        if (side.lengthSqr() < 1e-8) side = view.cross(if (abs(view.normalize().x) < 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 0.0, 1.0))
        side = side.normalize()
        val up = side.cross(view).normalize()
        return side.scale(radius) to up.scale(radius)
    }

    private val UP = Vec3(0.0, 1.0, 0.0)
    private val CORNERS = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

    /** How finely a wave's front is sent: the shader divides by the same number. */
    private const val FRONT_STEPS = 1024
    private const val RIM_STEPS = 24
}
