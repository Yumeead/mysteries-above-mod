package me.vangoo.mysteries.client.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class WheelLayoutTest {

    private val dead = 0.28f

    /** A cursor at [degrees] clockwise from straight up, well outside the dead zone. */
    private fun at(degrees: Double, onPage: Int): Int {
        val r = Math.toRadians(degrees)
        return WheelLayout.pick(sin(r) * 200, -cos(r) * 200, onPage, dead)
    }

    @Test
    fun pagesSplitByTwelve() {
        assertEquals(1, WheelLayout.pages(0))
        assertEquals(1, WheelLayout.pages(1))
        assertEquals(1, WheelLayout.pages(12))
        assertEquals(2, WheelLayout.pages(13))
        assertEquals(3, WheelLayout.pages(30))
        assertEquals(12, WheelLayout.countOn(30, 1))
        assertEquals(6, WheelLayout.countOn(30, 2))
        assertEquals(1, WheelLayout.countOn(13, 1))
    }

    @Test
    fun sectorBoundariesOnEight() {
        assertEquals(0, at(0.0, 8))
        assertEquals(0, at(22.0, 8))
        assertEquals(1, at(23.0, 8))
        assertEquals(2, at(90.0, 8))
        assertEquals(4, at(180.0, 8))
        assertEquals(7, at(315.0, 8))
        // Just left of straight up wraps to the first card, not to a ninth one.
        assertEquals(0, at(359.0, 8))
        assertEquals(0, at(338.0, 8))
    }

    @Test
    fun oneAbilityTakesTheWholeRing() {
        for (degrees in listOf(0.0, 90.0, 179.0, 270.0, 359.9)) assertEquals(0, at(degrees, 1))
    }

    @Test
    fun deadZoneCancels() {
        assertEquals(-1, WheelLayout.pick(0.0, 0.0, 8, dead))
        assertEquals(-1, WheelLayout.pick(0.0, -WheelLayout.RADIUS * dead + 1.0, 8, dead))
        assertEquals(-1, WheelLayout.pick(5.0, 5.0, 0, dead))
    }

    @Test
    fun cardsSitOnTheRingAndPickBackToThemselves() {
        for (onPage in listOf(1, 8, 9, 12)) {
            for (index in 0 until onPage) {
                val (dx, dy) = WheelLayout.offset(index, onPage)
                assertEquals(index, WheelLayout.pick(dx.toDouble(), dy.toDouble(), onPage, dead))
            }
        }
        assertEquals(WheelLayout.WIDE_RADIUS, -WheelLayout.offset(0, 12)[1])
        assertEquals(WheelLayout.RADIUS, -WheelLayout.offset(0, 8)[1])
    }

    @Test
    fun modeColumnStandsAwayFromTheCentre() {
        val right = WheelLayout.modeColumn(80, 0, 24, intArrayOf(40, 30))
        assertEquals(80 + 12 + WheelLayout.MODE_OFFSET, right[0][0])
        // Two pills of 12 with a gap of 2: 26 tall, centred on the card.
        assertEquals(-13, right[0][1])
        assertEquals(-13 + 14, right[1][1])

        val left = WheelLayout.modeColumn(-80, 0, 24, intArrayOf(40))
        assertEquals(-80 - 12 - WheelLayout.MODE_OFFSET - 40, left[0][0])
    }

    @Test
    fun modePickAndCorridor() {
        val pills = WheelLayout.modeColumn(80, 0, 24, intArrayOf(40, 40))
        assertEquals(0, WheelLayout.pickMode(110.0, -8.0, pills))
        assertEquals(1, WheelLayout.pickMode(110.0, 6.0, pills))
        assertEquals(-1, WheelLayout.pickMode(60.0, 0.0, pills))
        // Between the card and its pills: still the same card.
        assertEquals(true, WheelLayout.inCorridor(95.0, 0.0, 80, 0, 24, pills))
        assertEquals(false, WheelLayout.inCorridor(0.0, -80.0, 80, 0, 24, pills))
        assertEquals(false, WheelLayout.inCorridor(0.0, 0.0, 80, 0, 24, emptyList()))
    }

    @Test
    fun hourMarkStandsOutsideTheCard() {
        // Straight up: above the card, half a line clear of it.
        assertEquals(listOf(0, -80 - 12 - 4 - 3), WheelLayout.hourMark(0, -80, 24, 9).toList())
        // Straight down: one more line further, past the price.
        assertEquals(listOf(0, 80 + 12 + 4 + 3 + 9), WheelLayout.hourMark(0, 80, 24, 9).toList())
        assertEquals(listOf(80 + 12 + 4 + 3, 0), WheelLayout.hourMark(80, 0, 24, 9).toList())
    }
}
