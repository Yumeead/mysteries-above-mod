package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import kotlin.math.sqrt

/**
 * Spirit light: the mod's first particle that **glows** rather than merely not darkening.
 *
 * ## Additive, like lightning
 *
 * The mote and the glyph are `FULL_BRIGHT` but blended as translucent: whatever overlaps them, a
 * pixel is as bright as the top one. Here the blend is `SRC_ALPHA, ONE` — vanilla's own
 * [BlendFunction.LIGHTNING] — so a pixel **adds** to the one behind it. Ten lights on one spot
 * are ten times brighter, and in a pathway colour that means a white-hot core with a coloured
 * fringe: the look of glow, with no bloom pass and nothing that a shader pack would have to know
 * about.
 *
 * Depth is tested but not written, as for any additive geometry: otherwise the nearer halo would
 * hide the farther core, and a beam seen end-on would be a single dot.
 *
 * ## The halo is pixels, not a gradient
 *
 * The texture is a hard core and three rings, each a step dimmer. The vanilla particle shader
 * discards anything below a tenth of alpha, so as the light fades its rings drop out **one at a
 * time** from the outside in — the halo shrinks in steps, the way everything else in the game
 * changes, rather than blurring away.
 *
 * ## It flies exactly as sent
 *
 * Built through the constructor **without** velocity (see pitfall 6 in `CLAUDE.md`): the vanilla
 * one mixes a random spread into the velocity, and a shell or a beam would fray. The velocity is
 * the packet's deltas as they are.
 */
class SpiritLightParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    xd: Double,
    yd: Double,
    zd: Double,
    options: ColorParticleOption,
    sprite: TextureAtlasSprite,
) : SingleQuadParticle(level, x, y, z, sprite) {

    init {
        setColor(options.red, options.green, options.blue)
        this.xd = xd
        this.yd = yd
        this.zd = zd
        lifetime = level.random.nextIntBetweenInclusive(ModParticles.LIGHT_MIN_LIFETIME, MAX_LIFETIME)
        gravity = 0f
        friction = ModParticles.LIGHT_FRICTION.toFloat()
        hasPhysics = false
        quadSize = SIZE
        alpha = 0f
    }

    override fun getLayer(): SingleQuadParticle.Layer = LAYER

    override fun getLightCoords(partialTick: Float): Int = LightCoordsUtil.FULL_BRIGHT

    /** How much of its size the light keeps this frame — see [nearScale]. */
    private var near = 1f

    override fun extract(state: QuadParticleRenderState, camera: Camera, partialTick: Float) {
        near = nearScale(camera, x, y, z)
        super.extract(state, camera, partialTick)
    }

    override fun getQuadSize(partialTick: Float): Float = super.getQuadSize(partialTick) * near

    override fun tick() {
        super.tick()
        if (!isAlive) return
        // A flash and a fade, and the fade is what drops the rings one by one.
        val life = age.toFloat() / lifetime
        alpha = if (life < FLARE_SHARE) life / FLARE_SHARE else 1f - (life - FLARE_SHARE) / (1f - FLARE_SHARE)
    }

    /** It holds the set, not a sprite: the atlas is not assembled when providers are registered (pitfall 5). */
    class Provider(private val sprites: SpriteSet) : ParticleProvider<ColorParticleOption> {
        override fun createParticle(
            options: ColorParticleOption,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            xd: Double,
            yd: Double,
            zd: Double,
            random: RandomSource,
        ): Particle = SpiritLightParticle(level, x, y, z, xd, yd, zd, options, sprites.get(random))
    }

    companion object {
        private const val MAX_LIFETIME = 14
        /** Half the side of the quad: the halo needs room the mote does not. */
        private const val SIZE = 0.22f
        private const val FLARE_SHARE = 0.2f

        /** The same light riding a body ([ModParticles.SPIRIT_LIGHT_GLYPH]): smaller, a point of a shape rather than a spark. */
        const val GLYPH_SIZE = 0.07f

        /** Closer than this a light stops growing on screen. */
        private const val NEAR = 4.0

        /**
         * The vanilla particle pipeline with one change — the blend — and the depth write off.
         * Its snippets are private in `RenderPipelines`, so the same layouts are listed here by
         * hand; the shader is the vanilla `core/particle`, untouched. Under a shader pack it is the
         * pack's translucent particles: without that it would not be drawn at all.
         */
        private val PIPELINE: RenderPipeline = RenderPipeline.builder()
            .withLocation(modId("pipeline/spirit_light"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/particle")
            .withFragmentShader("core/particle")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            // Reversed depth, as everywhere on 26.x: the vanilla default is "greater or equal, write".
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            .build()
            .let { ShaderPacks.assign(it, "PARTICLES_TRANSLUCENT") }

        /** Shared with the riding light: one blend, one pipeline. */
        val LAYER = SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, PIPELINE)

        /**
         * The share of its size a light at [x], [y], [z] keeps on screen: within [NEAR] blocks of
         * the camera it shrinks with its distance, so it is no larger than it would be [NEAR] away.
         *
         * Perspective makes a light at arm's length a disc that fills a fifth of the view — and a
         * power cast from the chest sends its first lights right past the caster's own eyes, so
         * every pulse and every wave filled their screen with blobs. Up close, light is a spark,
         * not a lamp held to the eye.
         */
        fun nearScale(camera: Camera, x: Double, y: Double, z: Double): Float =
            (sqrt(camera.position().distanceToSqr(x, y, z)) / NEAR).coerceAtMost(1.0).toFloat()
    }
}
