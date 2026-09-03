package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.Registry
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries

/**
 * The mod's particles. Declaring them here **is** registering them, as in `ModItems` and
 * `ModChannels`.
 *
 * ## The mod's first own texture
 *
 * Until this phase the mod carried none: the potion is a vanilla bottle with a tint, the icons
 * are vanilla shards. The spirit mote breaks that, and deliberately: the vanilla `DUST` is a
 * blurred square blob, and twenty-two pathways told apart only by the colour of that blob would
 * look like one power in twenty-two shades.
 *
 * ## Why a colour rather than a `SimpleParticleType`
 *
 * The whole vocabulary of shapes is built around the pathway colour, so a colourless particle
 * would be of no use in it. No colour carrier needs writing: the vanilla [ColorParticleOption]
 * already exists, and both codecs with it. An options class of our own would add exactly nothing.
 */
object ModParticles {

    /**
     * The spirit mote: a diamond with hard edges that flares and fades.
     *
     * The form is generic on purpose — "a crumb of living matter" — and what it is, the colour
     * says: the Sailor's water, the Sun's light, the Death's ash.
     */
    val SPIRIT_MOTE: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /**
     * The spirit glyph: the same texture, but **steady** — it does not flare, does not change
     * size, does not drift and lives a fixed handful of ticks.
     *
     * ## Why a second particle when the texture is one
     *
     * [SPIRIT_MOTE] is designed as a **spark**: random lifetime, random size, a flare and a
     * fade, negative gravity. That is exactly what a scatter, an aura and a trail need — and
     * exactly what destroys a **steady shape**. A halo made of motes read as a rising cloud: at
     * any moment five to seven generations hung in the air, each at its own phase of the flare
     * and already risen.
     *
     * Vanilla does not cover this need. `DUST` does not move, but it lives 20–100 ticks, so a
     * shape meant to **follow the player** would leave a trail of rings behind them.
     *
     * Hence: a short fixed lifetime (so there is no trail), zero motion and zero animation (so
     * there is a form). Such a shape is drawn more often than a single mote lives, and there are
     * never more than two generations at once.
     */
    val SPIRIT_GLYPH: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /** Forces the class to initialise before the registries close. */
    fun register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_mote"), SPIRIT_MOTE)
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_glyph"), SPIRIT_GLYPH)
    }

    /**
     * A mote in the given colour.
     *
     * The alpha is set here: [RgbColor.packed] carries only RGB, and a zero in the high byte
     * would make the particle transparent — that is, invisible, and with no error in the log.
     */
    fun mote(color: RgbColor): ColorParticleOption =
        ColorParticleOption.create(SPIRIT_MOTE, OPAQUE or color.packed)

    /** A steady mote for shapes that must hold their form. The alpha is ours just the same. */
    fun glyph(color: RgbColor): ColorParticleOption =
        ColorParticleOption.create(SPIRIT_GLYPH, OPAQUE or color.packed)

    private const val OPAQUE = 0xFF shl 24
}
