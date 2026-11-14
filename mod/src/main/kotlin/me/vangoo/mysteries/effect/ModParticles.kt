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

    /**
     * Spirit light: a hard core with a halo stepped in rings of pixels — and it **adds** to what is
     * behind it rather than covering it.
     *
     * ## Why the mote cannot glow
     *
     * The mote is `FULL_BRIGHT`, so it does not darken in the shade, but it is blended as
     * translucent: two motes on top of each other are exactly as bright as one. Glow is the
     * opposite — where light is thick it whitens — and that takes additive blending, the same the
     * vanilla lightning bolt uses. So a beam made of this light has a white-hot core without
     * anyone drawing one: the core is the overlap.
     *
     * ## It flies exactly as it was sent
     *
     * The velocity is the packet's deltas as they are (the `count = 0` form), with no random
     * spread: a beam, a shell or a wave is a shape, and a shape must not fray.
     */
    val SPIRIT_LIGHT: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /**
     * A steady light that rides its owner: [SPIRIT_GLYPH]'s latching, [SPIRIT_LIGHT]'s glow.
     *
     * A halo, a small sun over the shoulder, a fist that burns — light that belongs to a body
     * rather than to a point in the world. The plain glyph rides too, but it is translucent: two
     * of them on one spot are as bright as one, and a halo of them reads as paint, not as light.
     * Sent the glyph's way — `count = 0`, the crown as the position, the offset in the deltas.
     */
    val SPIRIT_LIGHT_GLYPH: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /**
     * A scrap of paper: it flutters down, turning as it goes, settles and fades — the Fool's matter.
     *
     * Not light and not spirit: it takes the light of where it falls, as a page would. The tint is the
     * paper's shade — white, ash, or the red of a wound taken off a body.
     */
    val PAPER_SCRAP: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /** Forces the class to initialise before the registries close. */
    fun register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_mote"), SPIRIT_MOTE)
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_glyph"), SPIRIT_GLYPH)
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_light"), SPIRIT_LIGHT)
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_light_glyph"), SPIRIT_LIGHT_GLYPH)
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("paper_scrap"), PAPER_SCRAP)
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

    /** Light that glows: additive, so it whitens where it is thick. The alpha is ours just the same. */
    fun light(color: RgbColor): ColorParticleOption =
        ColorParticleOption.create(SPIRIT_LIGHT, OPAQUE or color.packed)

    /** Light that rides its owner. The alpha is ours just the same. */
    fun lightGlyph(color: RgbColor): ColorParticleOption =
        ColorParticleOption.create(SPIRIT_LIGHT_GLYPH, OPAQUE or color.packed)

    /** A scrap of paper in the given shade. The alpha is ours just the same. */
    fun scrap(color: RgbColor = PAPER): ColorParticleOption =
        ColorParticleOption.create(PAPER_SCRAP, OPAQUE or color.packed)

    /** Plain paper: a warm white, never the pure white of light. */
    val PAPER: RgbColor = RgbColor.of(240, 236, 224)

    /**
     * How [SPIRIT_LIGHT] brakes in flight: its speed is multiplied by this every tick, so it carries
     * `1 / (1 − f)` ≈ seven times its speed in all. The server times shapes by it, the client flies them.
     */
    const val LIGHT_FRICTION = 0.86

    /** The shortest [SPIRIT_LIGHT] lives, in ticks: a shape that must arrive somewhere arrives by then. */
    const val LIGHT_MIN_LIFETIME = 8

    private const val OPAQUE = 0xFF shl 24
}
