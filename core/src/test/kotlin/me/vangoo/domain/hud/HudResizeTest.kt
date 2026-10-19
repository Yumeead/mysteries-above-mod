package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudResizeTest {
    private val start = HudRect(100, 100, 80, 40)

    @Test fun detectsEveryEdgeAndCornerIncludingOutsideTheBorder() {
        for (edge in HudEdge.entries) {
            val x = start.x + (edge.horizontal + 1) * start.width / 2
            val y = start.y + (edge.vertical + 1) * start.height / 2
            assertEquals(edge, HudEdge.at(start, x, y))
            assertEquals(edge, HudEdge.at(start, x + edge.horizontal * 2, y + edge.vertical * 2))
        }
        assertNull(HudEdge.at(start, 140, 120))
        assertNull(HudEdge.at(start, 96, 120))
        assertNull(HudEdge.at(start, 140, 96))
    }

    @Test fun resizesFromEveryEdgeWithOppositeEdgeOrCenterFixed() {
        for (edge in HudEdge.entries) {
            val resize = HudResize(start, 1f, edge)
            for (direction in listOf(-1, 1)) {
                val (rect, scale) = resize.resized(edge.horizontal * 40 * direction,
                    edge.vertical * 20 * direction, 400, 300)
                assertEquals(if (direction == 1) 1.5f else .5f, scale, .0001f)
                assertEquals((start.width * scale).toInt(), rect.width)
                assertEquals((start.height * scale).toInt(), rect.height)
                when (edge.horizontal) {
                    -1 -> assertEquals(start.right, rect.right)
                    1 -> assertEquals(start.x, rect.x)
                    else -> assertEquals(start.x + start.width / 2, rect.x + rect.width / 2)
                }
                when (edge.vertical) {
                    -1 -> assertEquals(start.bottom, rect.bottom)
                    1 -> assertEquals(start.y, rect.y)
                    else -> assertEquals(start.y + start.height / 2, rect.y + rect.height / 2)
                }
            }
        }
    }

    @Test fun limitsSizeAndKeepsAllEdgesInsideScreen() {
        for (edge in HudEdge.entries) {
            val resize = HudResize(start, 1f, edge)
            val (large, largeScale) = resize.resized(edge.horizontal * 10000, edge.vertical * 10000, 320, 200)
            assertTrue(largeScale <= HudSettings.MAX_SCALE)
            assertTrue(large.x >= 0 && large.y >= 0 && large.right <= 320 && large.bottom <= 200)
            val (_, smallScale) = resize.resized(-edge.horizontal * 10000, -edge.vertical * 10000, 320, 200)
            assertEquals(HudSettings.MIN_SCALE, smallScale)
        }
        val (_, scale) = HudResize(start, 1f, HudEdge.BOTTOM_RIGHT).resized(10000, 10000, 2000, 2000)
        assertEquals(HudSettings.MAX_SCALE, scale)
    }

    @Test fun scalesDefaultAndSanitizeIndependently() {
        val settings = HudSettings(scales = mapOf(HudPart.FLASK to 2f, HudPart.BAR to Float.NaN,
            HudPart.ABILITY to -1f, HudPart.MARIONETTES to 99f))
        assertEquals(1f, HudSettings().scale(HudPart.FLASK))
        assertEquals(2f, settings.scale(HudPart.FLASK))
        assertEquals(1f, settings.scale(HudPart.BAR))
        assertEquals(.5f, settings.scale(HudPart.ABILITY))
        assertEquals(3f, settings.scale(HudPart.MARIONETTES))
        assertEquals(1f, HudSettings.validScale(Float.POSITIVE_INFINITY))
    }
}
