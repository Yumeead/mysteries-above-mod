package me.vangoo.mysteries.client.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun arcRowsNeverShareALineNorReachACard() {
        val half = 118
        val arrow = -16..16
        for (onPage in listOf(2, 5, 8, 12)) {
            val reach = WheelLayout.band(onPage) + WheelLayout.LABEL_GAP
            for (index in 0 until onPage) {
                val (dx, dy) = WheelLayout.offset(index, onPage)
                for (count in 1..10) {
                    val arc = WheelLayout.arc(dx, dy, onPage, count, half, arrow)
                    assertEquals(count, arc.size)
                    val tops = arc.map { it.y }.sorted()
                    tops.zipWithNext { a, b -> assertTrue(b - a >= WheelLayout.ROW) { "rows overlap: $tops" } }
                    arc.forEach { slice ->
                        assertTrue(slice.y >= -half && slice.y + WheelLayout.ROW <= half) { "row off screen: ${slice.y}" }
                        // The label's corner nearest the centre stays on the label circle, outside every card.
                        val near = if (slice.y < 0 && slice.y + WheelLayout.ROW > 0) 0 else minOf(kotlin.math.abs(slice.y), kotlin.math.abs(slice.y + WheelLayout.ROW))
                        assertTrue(kotlin.math.hypot(slice.x.toDouble(), near.toDouble()) >= reach - 1) { "label inside the ring: $index/$onPage" }
                        if (slice.right) assertTrue(slice.y >= arrow.last || slice.y + WheelLayout.ROW <= arrow.first) { "row on the arrow card: ${slice.y}" }
                        assertTrue(slice.from < slice.to)
                    }
                }
            }
        }
    }

    @Test
    fun arcOpensOnTheCardsSideAndGrowsOutOfIt() {
        // The top card opens to the right, away from the panel, and its band reaches back up to it.
        val top = WheelLayout.arc(0, -80, 8, 4, 118, null)
        assertTrue(top.all { it.right })
        assertEquals(0.0, top.minOf { it.start })
        assertTrue(top.minOf { it.from } > 0.0)
        // A card on the left opens to the left; one at three o'clock is centred on its own height.
        assertTrue(WheelLayout.arc(-80, 0, 8, 3, 118, null).none { it.right })
        val side = WheelLayout.arc(80, 0, 8, 2, 118, null)
        assertEquals(listOf(-11, 0), side.map { it.y })
    }

    @Test
    fun arcIsReadPastTheRingByRow() {
        val arc = WheelLayout.arc(80, 0, 8, 2, 118, null)
        // On the label itself, far out: its row.
        assertEquals(0, WheelLayout.pickArc(160.0, -5.0, 8, 24, arc))
        assertEquals(1, WheelLayout.pickArc(160.0, 5.0, 8, 24, arc))
        // On the card, inside the ring: the cards, not the arc.
        assertEquals(-1, WheelLayout.pickArc(85.0, 0.0, 8, 24, arc))
        // The other side of the wheel, and well above the rows.
        assertEquals(-1, WheelLayout.pickArc(-160.0, 0.0, 8, 24, arc))
        assertEquals(-1, WheelLayout.pickArc(160.0, -60.0, 8, 24, arc))
        // Close to the band just above the rows of the top card: the stretch back to the card.
        val top = WheelLayout.arc(0, -80, 8, 4, 118, null)
        assertEquals(0, WheelLayout.pickArc(4.0, -100.0, 8, 24, top))
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
