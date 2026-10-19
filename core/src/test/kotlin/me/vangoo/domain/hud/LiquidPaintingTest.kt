package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LiquidPaintingTest {
    private fun art(level: Float, surface: Int, color: Int) = LiquidPainting(level, surface,
        List(8) { y -> IntArray(5) { x -> if (y < surface) 0 else color + x + y } })

    @Test fun illustratedLevelsKeepEveryOriginalPixelIncludingHighlightsAndDarkTones() {
        val paintings = listOf(art(.005f, 7, 0xFF102030.toInt()), art(.1f, 6, 0xFF203040.toInt()),
            art(.33f, 4, 0xFFA050C0.toInt()), art(1f, 1, 0xFF30E0FA.toInt()))
        for (original in paintings) {
            val blend = LiquidPaintingBlend.at(paintings, original.level, 7)!!
            for (y in 0..7) for (x in 0..4) assertEquals(original.pixels[y][x], blend.color(x, y, 7))
        }
        assertNull(LiquidPaintingBlend.at(paintings, 0f, 7))
        assertEquals(7, LiquidPaintingBlend.at(paintings, .000001f, 7)!!.surface)
    }

    @Test fun intermediateLevelsRiseMonotonicallyAndPreserveColourAtTransparentEdges() {
        val lower = art(.33f, 4, 0xFF203040.toInt())
        val upper = art(1f, 1, 0xFFA0D0F0.toInt())
        var previous = 7
        for (step in 1..100) {
            val blend = LiquidPaintingBlend.at(listOf(lower, upper), step / 100f, 7)!!
            assertTrue(blend.surface <= previous)
            previous = blend.surface
        }
        val clear = LiquidPainting(.33f, 4, List(8) { IntArray(5) })
        val solid = LiquidPainting(1f, 1, List(8) { IntArray(5) { 0xFFFF80C0.toInt() } })
        val blend = LiquidPaintingBlend.at(listOf(clear, solid), .665f, 7)!!
        assertEquals(0xFF80C0, blend.color(2, 6, 7) and 0xFFFFFF)
        assertTrue(blend.color(2, 6, 7) ushr 24 in 127..128)
    }
}
