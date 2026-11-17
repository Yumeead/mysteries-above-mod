package me.vangoo.mysteries.client.render

import me.vangoo.domain.PathwayBranding
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.pathways.ModPathways
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO
import kotlin.math.pow

class BookArtTest {
    @Test
    fun paintedIconsExistAsSeparateUniqueSpritesForRegisteredPowers() {
        GameRegistries.bootstrap()
        val registered = ModPathways.all.flatMap { pathway ->
            (0..9).flatMap { pathway.abilitiesFor(it) }.map { it.identity.id }
        }.toSet()
        val painted = listOf(
            BookAbilityIcons.textures, BookAbilityIcons.foolOverrides, BookAbilityIcons.doorOverrides,
            BookAbilityIcons.sunOverrides, BookAbilityIcons.justiciarOverrides, BookAbilityIcons.deathOverrides,
            BookAbilityIcons.errorOverrides, BookAbilityIcons.whiteTowerOverrides,
        )
        assertTrue(painted.flatMap { it.keys }.all { it in registered })
        val pixels = painted.flatMap { it.values }.map { texture ->
            val identity = texture.path
            val resource = javaClass.getResource("/assets/${texture.namespace}/textures/gui/sprites/${texture.path}.png")
            assertNotNull(resource, identity)
            val image = ImageIO.read(resource)
            assertEquals(32, image.width, identity)
            assertEquals(32, image.height, identity)
            image.getRGB(0, 0, 32, 32, null, 0, 32).toList()
        }
        assertTrue(pixels.isNotEmpty())
        assertEquals(pixels.size, pixels.distinct().size)
    }

    @Test
    fun everyFoolPowerHasItsOwnIconAndSharedPurpleVariantsStayInFool() {
        GameRegistries.bootstrap()
        val fool = ModPathways.byName("Fool")!!
        val identities = (0..9).flatMap { fool.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Fool").also { assertNotNull(it, identity) }
        }
        assertEquals(18, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        for (identity in BookAbilityIcons.foolOverrides.keys) {
            for (pathway in listOf("Sun", "WhiteTower", "Door", "Justiciar", "Death", "Error", null)) {
                assertNotEquals(BookAbilityIcons.foolOverrides[identity], BookAbilityIcons.texture(identity, pathway), "$identity in $pathway")
            }
        }
    }

    @Test
    fun everyDoorPowerHasItsOwnIconWithoutRecoloringFoolOrJusticiar() {
        GameRegistries.bootstrap()
        val door = ModPathways.byName("Door")!!
        val identities = (0..9).flatMap { door.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Door").also { assertNotNull(it, identity) }
        }
        assertEquals(14, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(BookAbilityIcons.foolOverrides["door_burning"], BookAbilityIcons.texture("door_burning", "Fool"))
        assertEquals(BookAbilityIcons.justiciarOverrides["door_anti_divination"], BookAbilityIcons.texture("door_anti_divination", "Justiciar"))
        assertNull(BookAbilityIcons.texture("door_anti_divination", null))
    }

    @Test
    fun everySunPowerHasItsOwnIconAndGoldenRitualStaysInSun() {
        GameRegistries.bootstrap()
        val sun = ModPathways.byName("Sun")!!
        val identities = (0..9).flatMap { sun.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Sun").also { assertNotNull(it, identity) }
        }
        assertEquals(18, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(BookAbilityIcons.sunOverrides["common_ritual_magic"], BookAbilityIcons.texture("common_ritual_magic", "Sun"))
        assertEquals(BookAbilityIcons.foolOverrides["common_ritual_magic"], BookAbilityIcons.texture("common_ritual_magic", "Fool"))
        assertEquals(BookAbilityIcons.whiteTowerOverrides["common_ritual_magic"], BookAbilityIcons.texture("common_ritual_magic", "WhiteTower"))
        assertNull(BookAbilityIcons.texture("common_ritual_magic", null))
    }

    @Test
    fun everyJusticiarPowerHasItsOwnIconAndSharedVariantsKeepTheirPathway() {
        GameRegistries.bootstrap()
        val justiciar = ModPathways.byName("Justiciar")!!
        val identities = (0..9).flatMap { justiciar.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Justiciar").also { assertNotNull(it, identity) }
        }
        assertEquals(16, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(BookAbilityIcons.justiciarOverrides["common_combat_proficiency"], BookAbilityIcons.texture("common_combat_proficiency", "Justiciar"))
        assertNotEquals(BookAbilityIcons.texture("door_anti_divination", "Door"), BookAbilityIcons.texture("door_anti_divination", "Justiciar"))
        assertEquals(BookAbilityIcons.doorOverrides["door_anti_divination"], BookAbilityIcons.texture("door_anti_divination", "Door"))
        assertEquals(BookAbilityIcons.errorOverrides["common_combat_proficiency"], BookAbilityIcons.texture("common_combat_proficiency", "Error"))
        for (pathway in listOf("Fool", "Sun", "WhiteTower", "Death", null)) {
            for (identity in BookAbilityIcons.justiciarOverrides.keys) {
                assertNotEquals(BookAbilityIcons.justiciarOverrides[identity], BookAbilityIcons.texture(identity, pathway), "$identity in $pathway")
            }
        }
    }

    @Test
    fun everyDeathPowerHasItsOwnIconAndGreenRitualStaysInDeath() {
        GameRegistries.bootstrap()
        val death = ModPathways.byName("Death")!!
        val identities = (0..9).flatMap { death.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Death").also { assertNotNull(it, identity) }
        }
        assertEquals(20, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(BookAbilityIcons.deathOverrides["common_ritual_magic"], BookAbilityIcons.texture("common_ritual_magic", "Death"))
        val ritualVariants = listOf("Death", "Fool", "Sun", "WhiteTower").map { BookAbilityIcons.texture("common_ritual_magic", it) }
        assertEquals(4, ritualVariants.toSet().size)
        for (pathway in listOf("Justiciar", "Door", "Error", null)) {
            assertNull(BookAbilityIcons.texture("common_ritual_magic", pathway))
        }
    }

    @Test
    fun everyErrorPowerIncludingConceptualTheftsHasItsOwnIcon() {
        GameRegistries.bootstrap()
        val error = ModPathways.byName("Error")!!
        val identities = (0..9).flatMap { error.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Error").also { assertNotNull(it, identity) }
        }
        assertEquals(19, identities.size)
        assertEquals(6, identities.count { it.startsWith("error_conceptual_") })
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(BookAbilityIcons.errorOverrides["common_combat_proficiency"], BookAbilityIcons.texture("common_combat_proficiency", "Error"))
        assertNotEquals(BookAbilityIcons.texture("common_combat_proficiency", "Justiciar"), BookAbilityIcons.texture("common_combat_proficiency", "Error"))
        for (pathway in listOf("Fool", "Sun", "Death", "Door", null)) {
            assertNull(BookAbilityIcons.texture("common_combat_proficiency", pathway))
        }
        assertNull(BookAbilityIcons.texture("door_anti_divination", "Error"))
        assertNull(BookAbilityIcons.texture("common_ritual_magic", "Error"))
    }

    @Test
    fun everyTyrantPowerHasItsOwnIconIncludingSharedTierUpgrades() {
        GameRegistries.bootstrap()
        val tyrant = ModPathways.byName("Tyrant")!!
        val identities = (0..9).flatMap { tyrant.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "Tyrant").also { assertNotNull(it, identity) }
        }
        assertEquals(31, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        for (identity in listOf("tyrant_physique", "tyrant_stance")) {
            assertTrue(tyrant.abilitiesFor(9).any { it.identity.id == identity })
            assertTrue(tyrant.abilitiesFor(6).any { it.identity.id == identity })
        }
        assertNull(BookAbilityIcons.texture("common_combat_proficiency", "Tyrant"))
        assertNull(BookAbilityIcons.texture("common_ritual_magic", "Tyrant"))
    }

    @Test
    fun everyWhiteTowerPowerHasItsOwnIconAndSharedVariantsStayDistinct() {
        GameRegistries.bootstrap()
        val tower = ModPathways.byName("WhiteTower")!!
        val identities = (0..9).flatMap { tower.abilitiesFor(it) }.map { it.identity.id }.toSet()
        val icons = identities.map { identity ->
            BookAbilityIcons.texture(identity, "WhiteTower").also { assertNotNull(it, identity) }
        }
        assertEquals(10, identities.size)
        assertEquals(icons.size, icons.toSet().size)
        for ((identity, icon) in BookAbilityIcons.whiteTowerOverrides) {
            assertEquals(icon, BookAbilityIcons.texture(identity, "WhiteTower"))
            for (pathway in listOf("Fool", "Door", "Sun", "Death", "Justiciar", "Error", "Tyrant", "Visionary", null)) {
                assertNotEquals(icon, BookAbilityIcons.texture(identity, pathway), "$identity in $pathway")
            }
        }
        assertEquals(3, listOf("WhiteTower", "Justiciar", "Error").map {
            BookAbilityIcons.texture("common_combat_proficiency", it)
        }.toSet().size)
        assertNull(BookAbilityIcons.texture("door_anti_divination", "WhiteTower"))
    }

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
