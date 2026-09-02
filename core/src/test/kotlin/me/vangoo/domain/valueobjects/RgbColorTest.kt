package me.vangoo.domain.valueobjects

import me.vangoo.domain.PathwayBranding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Палітра одного відтінку.
 *
 * Перевіряється тут рівно те, заради чого перетворення взагалі йде через HSV, а не
 * множенням каналів: **відтінок не рухається**. Побачити злам оком у грі майже
 * неможливо — синій, що поїхав у бірюзовий на світлому краю палітри, виглядає просто
 * як «трохи інший синій».
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
        // Саме впорядкованість робить палітру градієнтом, а не шумом: фігура кладе
        // світле там, де в неї світло.
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

    /** Відстань між відтінками по колу: 350° і 10° різняться на 20°, а не на 340°. */
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
         * Усе, що лишається після HSV, — округлення до цілих каналів, і ціна його тим
         * вища, чим блякліший колір: у сірувато-синього Помилки (`#6699A1`, насиченість
         * ~0,37) розкид каналів малий, тож півкванта на канал важить більше за градус.
         * Три градуси — стеля цього ефекту; злам моделі коштував би десятки.
         */
        const val HUE_TOLERANCE = 3.0
    }
}
