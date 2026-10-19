package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudSettingsTest {
    @Test fun migratesOldSettingsAndRejectsUnknownModes() {
        assertFalse(HudSettings.migrate(null, null, null).flaskBacking)
        assertEquals(SpiritualityMode.OFF, HudSettings.migrate(null, null, false).spirituality)
        assertEquals(HudSettings(), HudSettings.migrate(null, null, true))
        assertEquals(HudSettings(), HudSettings.migrate("future", "future", null))
        assertEquals(SpiritualityMode.BAR, HudSettings.migrate("bar", "never", false).spirituality)
        assertEquals(SpiritualityNumbers.NEVER, HudSettings.migrate("bar", "never", false).numbers)
    }

    @Test fun positionsRoundTripAndKeepTheirAnchorOnResize() {
        for (x in listOf(4, 150, 270)) for (y in listOf(4, 80, 170)) {
            val rect = HudRect(x, y, 32, 24)
            val position = HudPosition.of(rect, 320, 200)
            assertEquals(rect, position.resolve(32, 24, 320, 200))
        }
        val right = HudPosition.of(HudRect(280, 170, 32, 24), 320, 200)
        assertEquals(HudRect(600, 370, 32, 24), right.resolve(32, 24, 640, 400))
        assertEquals(HudRect(0, 0, 32, 24), right.resolve(32, 24, 0, 0))
        val clamped = right.resolve(32, 24, 40, 30)
        assertTrue(clamped.x >= 0 && clamped.right <= 40 && clamped.y >= 0 && clamped.bottom <= 30)
    }

    @Test fun snapsToEdgesAndOtherElementsWithoutDistantJumps() {
        val target = HudRect(100, 100, 50, 20)
        assertEquals(154, HudLayout.snap(HudRect(157, 40, 20, 20), 320, 200, listOf(target)).x)
        assertEquals(4, HudLayout.snap(HudRect(2, 40, 20, 20), 320, 200, emptyList()).x)
        assertEquals(40, HudLayout.snap(HudRect(40, 40, 20, 20), 320, 200, emptyList()).x)
        assertFalse(target.overlaps(HudRect(150, 100, 20, 20)))
        assertTrue(target.overlaps(HudRect(149, 100, 20, 20)))
    }

    @Test fun defaultLayoutAvoidsVanillaAndOtherHudBlocksInSmallWindows() {
        for (width in listOf(320, 480, 640)) {
            val vanilla = HudRect(width / 2 - 122, 166, 244, 74)
            val flask = HudLayout.avoid(HudRect(width - 74, 152, 70, 84), width, 240, listOf(vanilla))
            val ability = HudLayout.avoid(HudRect(6, 105, 130, 32), width, 240, listOf(vanilla, flask))
            val puppets = HudLayout.avoid(HudRect((width - 258) / 2, 124, 258, 34), width, 240, listOf(vanilla, flask, ability))
            val all = listOf(vanilla, flask, ability, puppets)
            all.forEachIndexed { i, rect -> assertTrue(all.drop(i + 1).none(rect::overlaps), "$width $all") }
        }
    }
}
