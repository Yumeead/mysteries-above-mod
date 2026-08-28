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
 * Порошинка духу: спалахує, спливає й гасне.
 *
 * ## Що вона робить такого, чого не вміє ванільний `DUST`
 *
 * | `DUST_COLOR_TRANSITION` | порошинка духу |
 * |---|---|
 * | розмитий квадрат — форма спільна для всіх модів | власний ромб із твердими краями |
 * | колір іде від A до B, яскравість стала | спалах на початку, згасання в кінці |
 * | розмір сталий | стягується, доки гасне |
 * | висить на місці, тільки дрейфує | має власний підйом і тертя |
 *
 * Ванілька лишається там, де вона вже виглядає своєю: `SPLASH` для бризок,
 * `BUBBLE` для води. Своя частинка потрібна саме там, де мод говорить **своїм**
 * голосом — коли видно чужу силу.
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

    /** Розмір на піку спалаху: від нього рахується і поточний, бо він же й найбільший. */
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

    /** Світиться сама: порошинка чужої сили не має темніти в тіні. */
    override fun getLightCoords(partialTick: Float): Int = LightCoordsUtil.FULL_BRIGHT

    override fun tick() {
        super.tick()
        if (!isAlive) return

        // Життя ділиться на спалах і згасання: сила проявилась, потім розтанула.
        val life = age.toFloat() / lifetime
        val flare = if (life < FLARE_SHARE) life / FLARE_SHARE else 1f - (life - FLARE_SHARE) / (1f - FLARE_SHARE)

        alpha = flare
        // Розмір падає лише в другій половині: спалах росте яскравістю, не розміром.
        quadSize = peakSize * (MIN_SHARE + (1f - MIN_SHARE) * flare)
    }

    /**
     * Один провайдер на всі кольори: колір приїжджає в [ColorParticleOption], а не
     * в окремому зареєстрованому типі на кожен шлях.
     *
     * **Тримає набір, а не спрайт.** Провайдери реєструються в конструкторі
     * `ParticleResources`, тобто **до** того, як атлас частинок зібрано: `SpriteSet` у цю
     * мить порожній, і будь-який `first()` там падає `NullPointerException` ще на
     * заставці. Спрайт береться в [createParticle] — коли атлас уже є.
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

        /** Від'ємна гравітація: речовина духу спливає, а не осідає. */
        const val GRAVITY = -0.006f
        const val FRICTION = 0.92f

        /** Частка життя на спалах. Коротка: сила проявляється швидше, ніж тане. */
        const val FLARE_SHARE = 0.25f

        /** Мінімальна частка розміру — щоб порошинка гасла, а не зникала в точку. */
        const val MIN_SHARE = 0.35f
    }
}
