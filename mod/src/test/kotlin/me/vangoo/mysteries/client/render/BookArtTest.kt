package me.vangoo.mysteries.client.render

import me.vangoo.domain.PathwayBranding
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.pathways.ModPathways
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO
import kotlin.math.pow

class BookArtTest {
    @Test
    fun everyPathwayLabelKeepsReadableContrastOnPaper() {
        fun luminance(rgb: Int): Double {
            val channels = listOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255).map {
                val c = it / 255.0
                if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
            }
            return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
        }
        for (pathway in PathwayBranding.names) {
            val color = GrimoireSkin.readableAccent(PathwayBranding.liquidOf(pathway).packed)
            val contrast = (luminance(color) + .05) / (luminance(0x191519) + .05)
            assertTrue(contrast >= 4.5, "$pathway: $contrast")
        }
    }

    @Test
    fun everyPathwayHasTransparentCenteredArtAtEveryActualDisplaySize() {
        for (pathway in PathwayBranding.names) for (size in listOf(24, 48, 64, 96, 128)) {
            val name = "${pathway.lowercase()}_$size.png"
            val resource = javaClass.getResource("/assets/mysteries_above/textures/gui/sprites/grimoire/emblem/official/$name")
            assertNotNull(resource, name)
            val image = ImageIO.read(resource)
            assertEquals(size, image.width, name)
            assertEquals(size, image.height, name)
            val points = (0 until size).flatMap { y -> (0 until size).filter { x -> image.getRGB(x, y) ushr 24 == 255 }.map { x -> x to y } }
            assertTrue(points.isNotEmpty(), name)
            assertTrue(points.minOf { it.first } > 0 && points.minOf { it.second } > 0, name)
            assertTrue(kotlin.math.abs(points.minOf { it.first } + points.maxOf { it.first } - size + 1) <= 1, name)
            assertTrue(kotlin.math.abs(points.minOf { it.second } + points.maxOf { it.second } - size + 1) <= 1, name)
            assertTrue((0 until size).all { y -> (0 until size).all { x -> image.getRGB(x, y) ushr 24 in listOf(0, 255) } }, name)
        }
    }

    @Test
    fun abilityPictogramsAreCenteredOnTheSameFifteenPixelGrid() {
        for ((name, rows) in BookAbilityIcons.patterns) {
            assertEquals(15, rows.size, name)
            assertTrue(rows.all { it.length == 15 && it.all { c -> c in ".#+" } }, name)
            val points = rows.flatMapIndexed { y, row -> row.indices.filter { row[it] != '.' }.map { x -> x to y } }
            assertTrue(kotlin.math.abs(points.minOf { it.first } + points.maxOf { it.first } - 14) <= 1, name)
            assertTrue(kotlin.math.abs(points.minOf { it.second } + points.maxOf { it.second } - 14) <= 1, name)
        }
        assertEquals(BookAbilityIcons.patterns.size, BookAbilityIcons.patterns.values.distinct().size)
    }

    @Test
    fun everyExistingPowerHasAnExplicitPictogram() {
        GameRegistries.bootstrap()
        val missing = buildSet {
            for (pathway in ModPathways.all) for (level in 0..9) for (ability in pathway.abilitiesFor(level)) {
                val id = ability.identity.id
                if (id !in BookAbilityIcons.abilities && !id.startsWith("error_conceptual_")) add(id)
                assertTrue(BookAbilityIcons.symbol(id) in BookAbilityIcons.patterns, id)
            }
        }
        assertTrue(missing.isEmpty(), missing.joinToString())
        assertEquals("scroll", BookAbilityIcons.symbol("door_recorded_sun_blessing"))
    }
}
