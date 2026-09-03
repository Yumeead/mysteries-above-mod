package me.vangoo.mysteries.client.render

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.SpriteSet
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.RandomSource

/**
 * The spirit mote: it flares, floats up and fades.
 *
 * ## What it does that the vanilla `DUST` cannot
 *
 * | `DUST_COLOR_TRANSITION` | the spirit mote |
 * |---|---|
 * | a blurred square — a form shared by every mod | its own diamond with hard edges |
 * | the colour goes from A to B, the brightness is constant | a flare at the start, a fade at the end |
 * | a constant size | it draws in as it fades |
 * | hangs in place, only drifts | it has its own rise and friction |
 *
 * Vanilla stays where it already looks at home: `SPLASH` for spray, `BUBBLE` for water. A
 * particle of our own is needed exactly where the mod speaks in **its own** voice — when someone
 * else's power is on show.
 */
class SpiritMoteParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    xd: Double,
    yd: Double,
    zd: Double,
    options: ColorParticleOption,
    sprite: TextureAtlasSprite,
) : SingleQuadParticle(level, x, y, z, xd, yd, zd, sprite) {

    /** The size at the peak of the flare: the current one is computed from it, as it is also the largest. */
    private val peakSize: Float

    init {
        setColor(options.red, options.green, options.blue)
        lifetime = level.random.nextIntBetweenInclusive(MIN_LIFETIME, MAX_LIFETIME)
        gravity = GRAVITY
        friction = FRICTION
        hasPhysics = false
        peakSize = BASE_SIZE * (0.8f + level.random.nextFloat() * 0.4f)
        quadSize = peakSize
        alpha = 0f
    }

    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT

    /** Glows on its own: a mote of another's power must not darken in the shade. */
    override fun getLightCoords(partialTick: Float): Int = LightCoordsUtil.FULL_BRIGHT

    override fun tick() {
        super.tick()
        if (!isAlive) return

        // The lifetime splits into a flare and a fade: the power showed itself, then melted away.
        val life = age.toFloat() / lifetime
        val flare = if (life < FLARE_SHARE) life / FLARE_SHARE else 1f - (life - FLARE_SHARE) / (1f - FLARE_SHARE)

        alpha = flare
        // The size only falls in the second half: a flare grows in brightness, not in size.
        quadSize = peakSize * (MIN_SHARE + (1f - MIN_SHARE) * flare)
    }

    /**
     * One provider for every colour: the colour arrives in a [ColorParticleOption] rather than
     * in a separately registered type per pathway.
     *
     * **It holds the set, not a sprite.** Providers are registered in the `ParticleResources`
     * constructor, i.e. **before** the particle atlas is assembled: the `SpriteSet` is empty at
     * that moment, and any `first()` there fails with a `NullPointerException` on the splash
     * screen. The sprite is taken in [createParticle] — when the atlas is already there.
     */
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
        ): Particle = SpiritMoteParticle(level, x, y, z, xd, yd, zd, options, sprites.get(random))
    }

    private companion object {
        const val MIN_LIFETIME = 16
        const val MAX_LIFETIME = 28
        const val BASE_SIZE = 0.09f

        /** Negative gravity: spirit matter floats up rather than settling. */
        const val GRAVITY = -0.006f
        const val FRICTION = 0.92f

        /** The share of the lifetime spent on the flare. Short: a power shows itself faster than it melts. */
        const val FLARE_SHARE = 0.25f

        /** The minimum size share — so that a mote fades rather than vanishing into a point. */
        const val MIN_SHARE = 0.35f
    }
}
