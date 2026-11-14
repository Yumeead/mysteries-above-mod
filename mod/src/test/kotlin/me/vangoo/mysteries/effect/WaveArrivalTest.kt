package me.vangoo.mysteries.effect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A wave's front, as the powers that time themselves by it see it: whatever it touches flares on
 * the tick [waveArrival] names, so that tick must be when the light really gets there.
 */
class WaveArrivalTest {

    @Test
    fun `the front reaches the edge while the shortest-lived light still burns`() {
        for (radius in listOf(6.0, 10.0, 30.0)) {
            assertEquals(LIGHT_MIN_LIFETIME, waveArrival(radius, radius), "радіус $radius")
        }
    }

    @Test
    fun `the farther away, the later — and never before the light could have flown there`() {
        val radius = 20.0
        // Where the light really is after t ticks: it was flung at the wave's speed and brakes every tick.
        val flown = (0..LIGHT_MIN_LIFETIME).map { t -> 1.0 + speedFor(radius) * (1 - Math.pow(LIGHT_FRICTION, t.toDouble())) / (1 - LIGHT_FRICTION) }
        var last = 0
        for (step in 0..40) {
            val distance = radius * step / 40
            val tick = waveArrival(distance, radius)
            assertTrue(tick >= last, "фронт повернув назад на $distance бл.")
            assertTrue(flown[tick] >= distance - 1e-9, "на тіку $tick світло ще не долетіло до $distance бл.")
            last = tick
        }
    }

    private fun speedFor(radius: Double) =
        (radius - 1.0) * (1 - LIGHT_FRICTION) / (1 - Math.pow(LIGHT_FRICTION, LIGHT_MIN_LIFETIME.toDouble()))

    private companion object {
        const val LIGHT_FRICTION = ModParticles.LIGHT_FRICTION
        const val LIGHT_MIN_LIFETIME = ModParticles.LIGHT_MIN_LIFETIME
    }
}
