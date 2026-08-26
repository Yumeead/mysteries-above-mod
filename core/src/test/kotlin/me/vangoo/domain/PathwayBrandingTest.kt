package me.vangoo.domain

import me.vangoo.domain.valueobjects.RgbColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathwayBrandingTest {

    private val all = listOf(
        "Error", "Visionary", "Door", "Justiciar", "WhiteTower", "Fool",
        "Sun", "Tyrant", "HangedMan", "Darkness", "Death", "TwilightGiant",
        "RedPriest", "Demoness", "Hermit", "Paragon", "Mother", "Moon",
        "Abyss", "Chained", "BlackEmperor", "WheelOfFortune",
    )

    @Test
    fun `all twenty two pathways are branded`() {
        assertEquals(22, PathwayBranding.names.size)
        all.forEach { assertTrue(it in PathwayBranding.names, "missing branding for $it") }
    }

    @Test
    fun `unknown and null fall back to gray`() {
        val gray = RgbColor(0xAAAAAA)
        assertEquals(gray, PathwayBranding.textOf("Nonexistent"))
        assertEquals(gray, PathwayBranding.textOf(null))
        assertEquals(RgbColor.of(128, 128, 128), PathwayBranding.liquidOf(null))
    }

    @Test
    fun `liquid colour survives the round trip through packed rgb`() {
        val error = PathwayBranding.liquidOf("Error")
        assertEquals(102, error.red)
        assertEquals(153, error.green)
        assertEquals(161, error.blue)
    }

    @Test
    fun `black text colour is packed as zero, not as absent`() {
        assertEquals(RgbColor(0x000000), PathwayBranding.textOf("Abyss"))
    }
}
