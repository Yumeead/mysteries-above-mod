package me.vangoo.domain.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProjectileForecastTest {

    @Test
    fun `a straight shot arrives when its distance says`() {
        // 20 blocks at 3 a tick, no gravity, no drag: inside the 1-block body on the seventh tick.
        val ticks = ProjectileForecast.ticksToReach(0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 20.0, 0.0, 0.0, 1.0, 40, drag = 1.0)
        assertEquals(7, ticks)
    }

    @Test
    fun `an arrow aimed level falls short of a far target`() {
        // Fired flat at 3 blocks a tick, an arrow drops well over a block across 40 blocks.
        assertNull(ProjectileForecast.ticksToReach(0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.05, 40.0, 0.0, 0.0, 1.0, 40))
    }

    @Test
    fun `a lobbed arrow comes down on the target`() {
        // A skeleton's arc: up and over, landing 20 blocks away.
        val ticks = ProjectileForecast.ticksToReach(0.0, 0.0, 0.0, 1.6, 0.4, 0.0, 0.05, 20.0, 0.0, 0.0, 1.5, 60)
        assertEquals(true, ticks != null && ticks in 10..20)
    }

    @Test
    fun `a shot that passes wide never arrives`() {
        assertNull(ProjectileForecast.ticksToReach(0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 20.0, 0.0, 4.0, 1.5, 40))
    }
}
