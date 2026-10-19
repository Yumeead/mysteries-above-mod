package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlaskLiquidTest {
    @Test fun bubblesAndCurrentsMoveOnlyInsidePaintedLiquid() {
        val rows = List(16) { listOf(2..13) }
        val pixels = List(16) { y -> IntArray(16) { x ->
            if (x in 8..10 && y in 6..10) 0 else -1
        } }
        val painting = LiquidPaintingBlend.at(listOf(LiquidPainting(1f, 2, pixels)), 1f, 15)!!
        for (flow in ParticleFlow.entries) {
            fun highlights(time: Float) = FlaskLiquid.highlights(rows, painting, 15, time, 120, 5, flow)
            assertNotEquals(highlights(0f), highlights(20f), "$flow is frozen")
            for (time in 0..240 step 7) {
                val points = highlights(time.toFloat())
                assertFalse(points.isEmpty())
                for (point in points) {
                    assertTrue(point.x in 2..13 && point.y in 2..15)
                    assertTrue(point.opacity > 0f && point.opacity <= 1f)
                    assertTrue(painting.color(point.x, point.y, 15) ushr 24 > 0, "$flow reached dry glass")
                }
            }
        }
        assertTrue(FlaskLiquid.highlights(rows, null, 15, 0f, 120, 5, ParticleFlow.RISE).isEmpty())
        val lastDrop = LiquidPaintingBlend.at(listOf(LiquidPainting(.005f, 15, pixels)), .005f, 15)
        assertTrue(FlaskLiquid.highlights(rows, lastDrop, 15, 0f, 120, 5, ParticleFlow.RISE).isEmpty())
    }

    @Test fun heightIsLinearAndAnyPositiveReserveHasAPixel() {
        assertEquals(0, FlaskLiquid.filledRows(32, 0f))
        assertEquals(1, FlaskLiquid.filledRows(32, .000001f))
        assertEquals(16, FlaskLiquid.filledRows(32, .5f))
        assertEquals(32, FlaskLiquid.filledRows(32, 1f))
        assertEquals(32, FlaskLiquid.filledRows(32, 2f))
        assertThrows(IllegalArgumentException::class.java) { FlaskLiquid.filledRows(32, Float.NaN) }
    }

    @Test fun everyMotionStaysInsideItsNormalizedLiquidBounds() {
        for (flow in ParticleFlow.entries) for (i in 0..8) for (frame in -20..200) {
            val (x, y) = FlaskLiquid.particle(flow, frame / 47f, i)
            assertTrue(x in 0f..1f && y in 0f..1f, "$flow: $x, $y")
        }
    }

    @Test fun shadesAreDiscreteAndSurfaceStaysBrighterThanTheBottom() {
        for (time in listOf(0f, 25f, 160f)) {
            for (x in 13..48) for (y in 18..49)
                assertTrue(FlaskLiquid.shade(x, y, 18, 49, time, 6) in 0..5)
            assertTrue(FlaskLiquid.shade(31, 18, 18, 49, time, 6) >
                FlaskLiquid.shade(31, 49, 18, 49, time, 6))
        }
    }
}
