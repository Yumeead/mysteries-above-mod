package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlaskLiquidTest {
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
