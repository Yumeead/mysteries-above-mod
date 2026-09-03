package me.vangoo.domain.valueobjects

import me.vangoo.domain.PathwayBranding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * A palette of one hue.
 *
 * What is checked here is exactly the reason the conversion goes through HSV rather than
 * channel multiplication: **the hue does not move**. Spotting the break by eye in game is
 * next to impossible — a blue that drifted into teal at the light end of the palette just
 * looks like "a slightly different blue".
 */
class RgbColorTest {

    @Test
    @DisplayName("Зсув світлості не рухає відтінок — для жодного з 22 шляхів")
    fun shadingKeepsTheHue() {
        PathwayBranding.names.forEach { pathway ->
            val base = PathwayBranding.liquidOf(pathway)
            if (base.isGray()) return@forEach

            listOf(-0.6, -0.35, 0.35, 0.6).forEach { amount ->
                val shifted = base.shade(amount)
                assertTrue(
                    hueGap(base, shifted) < HUE_TOLERANCE,
                    "$pathway $base → $shifted при $amount: відтінок поїхав на ${hueGap(base, shifted)}°",
                )
            }
        }
    }

    @Test
    @DisplayName("Краї палітри — це чорний і білий, а нуль нічого не змінює")
    fun shadingReachesBothEnds() {
        val blue = PathwayBranding.liquidOf("Door")

        assertEquals(RgbColor(0x000000), blue.shade(-1.0))
        assertEquals(RgbColor(0xFFFFFF), blue.shade(1.0))
        assertEquals(blue, blue.shade(0.0))
    }

    @Test
    @DisplayName("Палітра впорядкована від темного до світлого")
    fun paletteRunsDarkToLight() {
        val shades = PathwayBranding.liquidOf("Door").shades(5, spread = 0.4)

        assertEquals(5, shades.size)
        // The ordering is what makes the palette a gradient rather than noise: a shape puts
        // the light where its light is.
        assertTrue(
            shades.zipWithNext().all { (darker, lighter) -> lighter.brightness() > darker.brightness() },
            "палітра не зростає за яскравістю: $shades",
        )
    }

    @Test
    @DisplayName("Палітра з одного кольору — це сам колір")
    fun singletonPaletteIsTheColourItself() {
        val sun = PathwayBranding.liquidOf("Sun")
        assertEquals(listOf(sun), sun.shades(1))
    }

    private fun RgbColor.isGray(): Boolean = red == green && green == blue

    private fun RgbColor.brightness(): Int = maxOf(red, green, blue)

    /** Distance between hues around the circle: 350° and 10° differ by 20°, not by 340°. */
    private fun hueGap(first: RgbColor, second: RgbColor): Double {
        val gap = abs(hueOf(first) - hueOf(second))
        return minOf(gap, 360.0 - gap)
    }

    private fun hueOf(color: RgbColor): Double {
        val r = color.red / 255.0
        val g = color.green / 255.0
        val b = color.blue / 255.0
        val top = maxOf(r, g, b)
        val span = top - minOf(r, g, b)
        if (span < 1.0e-9) return 0.0
        val raw = when (top) {
            r -> (g - b) / span
            g -> 2.0 + (b - r) / span
            else -> 4.0 + (r - g) / span
        } * 60.0
        return if (raw < 0) raw + 360.0 else raw
    }

    private companion object {
        /**
         * All that is left after HSV is the rounding to whole channels, and its price is the
         * higher the paler the colour: in the Error's greyish blue (`#6699A1`, saturation
         * ~0.37) the channel spread is small, so half a quantum per channel weighs more than
         * a degree. Three degrees is the ceiling of this effect; breaking the model would
         * cost tens.
         */
        const val HUE_TOLERANCE = 3.0
    }
}
