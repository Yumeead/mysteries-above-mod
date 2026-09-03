package me.vangoo.domain.valueobjects

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A colour as packed RGB. It exists so that `core` can hold pathway branding without knowing
 * about `net.minecraft.util.ARGB` or `TextColor` — the mod unpacks it on its own side.
 *
 * ## One hue, different lightness
 *
 * A shape drawn in a **single** pathway colour reads as a blob of paint: twenty motes of the
 * same blue have neither depth nor form. Hence the colour has [shade] — "the same colour, a
 * little darker or lighter" — and shapes take a palette from it ([shades]) instead of mixing
 * in white or black by hand.
 *
 * This works for any pathway, not just the blue Door: the conversion goes through **HSV**,
 * so the hue stays the same number and only the value moves (and, towards light, the
 * saturation). Multiplying the RGB channels, the first thing that suggests itself, cannot do
 * this: it is fine for darkening, but "lightening" that way means hitting the 255 ceiling in
 * different channels at different moments — and the hue drifts.
 */
@JvmInline
value class RgbColor(val packed: Int) {

    init {
        require(packed in 0..0xFFFFFF) { "RGB out of range: $packed" }
    }

    val red: Int get() = (packed shr 16) and 0xFF
    val green: Int get() = (packed shr 8) and 0xFF
    val blue: Int get() = packed and 0xFF

    /**
     * The same hue, a different lightness.
     *
     * @param amount −1 — towards black, 0 — this very colour, +1 — towards white.
     */
    fun shade(amount: Double): RgbColor {
        require(amount in -1.0..1.0) { "Зсув світлості поза межами [-1, 1]: $amount" }
        if (amount == 0.0) return this

        val r = red / 255.0
        val g = green / 255.0
        val b = blue / 255.0
        val top = maxOf(r, g, b)
        val bottom = minOf(r, g, b)

        val saturation = if (top == 0.0) 0.0 else (top - bottom) / top
        return if (amount < 0) {
            // Darker is just a lower value: hue and saturation do not move.
            fromHsv(hueOf(r, g, b, top, bottom), saturation, top * (1 + amount))
        } else {
            // Lighter is the road to white, and white has zero saturation in HSV.
            fromHsv(hueOf(r, g, b, top, bottom), saturation * (1 - amount), top + (1 - top) * amount)
        }
    }

    /**
     * A palette of one hue: [count] colours from darker to lighter.
     *
     * A palette, not a random offset per particle: randomness gives noise, while an ordered
     * list lets a shape put the light where its "light" is (the top of an arc, the crest of a
     * wave) and the dark where the shadow is. The difference between noise and a gradient is
     * the difference between mottled and three-dimensional.
     *
     * @param spread how far the edges of the palette spread out from this colour
     */
    fun shades(count: Int, spread: Double = 0.35): List<RgbColor> {
        require(count > 0) { "Палітра не може бути порожньою" }
        if (count == 1) return listOf(this)
        return List(count) { i -> shade(-spread + 2 * spread * i / (count - 1)) }
    }

    override fun toString(): String = "#%06X".format(packed)

    companion object {
        fun of(r: Int, g: Int, b: Int): RgbColor {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB out of range: $r,$g,$b" }
            return RgbColor((r shl 16) or (g shl 8) or b)
        }

        /** Hue in degrees [0, 360). Grey has no hue — zero for it. */
        private fun hueOf(r: Double, g: Double, b: Double, top: Double, bottom: Double): Double {
            val span = top - bottom
            if (span < 1.0e-9) return 0.0
            val raw = when (top) {
                r -> (g - b) / span
                g -> 2.0 + (b - r) / span
                else -> 4.0 + (r - g) / span
            } * 60.0
            return if (raw < 0) raw + 360.0 else raw
        }

        private fun fromHsv(hue: Double, saturation: Double, value: Double): RgbColor {
            val s = saturation.coerceIn(0.0, 1.0)
            val v = value.coerceIn(0.0, 1.0)
            val sector = hue / 60.0
            val chroma = v * s
            val second = chroma * (1 - abs(sector % 2.0 - 1))
            val (r, g, b) = when (sector.toInt()) {
                0 -> Triple(chroma, second, 0.0)
                1 -> Triple(second, chroma, 0.0)
                2 -> Triple(0.0, chroma, second)
                3 -> Triple(0.0, second, chroma)
                4 -> Triple(second, 0.0, chroma)
                else -> Triple(chroma, 0.0, second)
            }
            val base = v - chroma
            return of(
                ((r + base) * 255).roundToInt().coerceIn(0, 255),
                ((g + base) * 255).roundToInt().coerceIn(0, 255),
                ((b + base) * 255).roundToInt().coerceIn(0, 255),
            )
        }
    }
}
