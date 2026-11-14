package me.vangoo.mysteries.client.render

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.RandomSource
import kotlin.math.sin

/**
 * A scrap of paper: it flies off as it was thrown, catches the air, flutters down turning, settles
 * on the ground and fades — the Fool's matter, where the others' powers are light.
 *
 * ## It flies the velocity it was given
 *
 * The vanilla constructor with a velocity mixes a random spread into it and renormalises it
 * (pitfall 6 in CLAUDE.md), so a burst of scraps aimed outward would come out as a shapeless puff.
 * The velocity is set back to exactly what was asked, and the only randomness is the scrap's own:
 * its sway, its spin, its life.
 *
 * ## It is matter, not light
 *
 * Lit by the place it falls in, like a page — the opposite of the spirit mote, which glows on its
 * own. A scrap in a dark corridor is a dark scrap.
 */
class PaperScrapParticle private constructor(
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

    private val sway = level.random.nextFloat() * SWAY_PHASES
    private var spin = (level.random.nextFloat() - 0.5f) * MAX_SPIN

    init {
        setColor(options.red, options.green, options.blue)
        this.xd = xd
        this.yd = yd
        this.zd = zd
        lifetime = level.random.nextIntBetweenInclusive(MIN_LIFETIME, MAX_LIFETIME)
        gravity = GRAVITY
        friction = FRICTION
        hasPhysics = true
        quadSize = MIN_SIZE + level.random.nextFloat() * (MAX_SIZE - MIN_SIZE)
        roll = level.random.nextFloat() * FULL_TURN
        oRoll = roll
    }

    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT

    override fun tick() {
        oRoll = roll
        super.tick()
        if (!isAlive) return

        if (onGround) {
            // Settled: it lies where it fell and only fades.
            xd = 0.0
            zd = 0.0
            spin = 0f
        } else {
            // A sheet does not fall, it glides: side to side, slower the more air it has caught.
            val drift = sin(age * SWAY_RATE + sway) * SWAY
            xd += drift
            zd += drift * 0.6
            if (yd < TERMINAL) yd = TERMINAL
            roll += spin
        }
        val left = lifetime - age
        alpha = if (left < FADE_TICKS) left.toFloat() / FADE_TICKS else 1f
    }

    /** One provider for every shade; the sprite is taken when the atlas is there (pitfall 5). */
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
        ): Particle = PaperScrapParticle(level, x, y, z, xd, yd, zd, options, sprites.get(random))
    }

    private companion object {
        const val MIN_LIFETIME = 50
        const val MAX_LIFETIME = 80
        const val FADE_TICKS = 16
        const val MIN_SIZE = 0.06f
        const val MAX_SIZE = 0.1f
        const val GRAVITY = 0.04f
        const val FRICTION = 0.88f
        /** A sheet's terminal speed: it never falls faster than this, however long the drop. */
        const val TERMINAL = -0.06
        const val SWAY = 0.006
        const val SWAY_RATE = 0.35f
        const val SWAY_PHASES = 6.28f
        const val MAX_SPIN = 0.5f
        const val FULL_TURN = 6.28f
    }
}
