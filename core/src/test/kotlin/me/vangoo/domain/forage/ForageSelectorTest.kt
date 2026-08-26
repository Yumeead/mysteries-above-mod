package me.vangoo.domain.forage

import me.vangoo.domain.brewing.RecipeDefinition
import me.vangoo.domain.creatures.ConvergenceBias
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ForageSelectorTest {

    @Test
    fun picksFromKnownBiome() {
        val s = ForageSelector(mapOf("PLAINS" to listOf(ForageEntry("elf_flower_petals", 50))))
        assertEquals("elf_flower_petals", s.pickForBiome("PLAINS", 0.5))
    }

    @Test
    fun unknownBiomeNull() {
        val s = ForageSelector(mapOf("PLAINS" to listOf(ForageEntry("x", 50))))
        assertNull(s.pickForBiome("DESERT", 0.5))
    }

    @Test
    fun nullBiomeNull() {
        val s = ForageSelector(mapOf("PLAINS" to listOf(ForageEntry("x", 50))))
        assertNull(s.pickForBiome(null, 0.5))
    }

    @Test
    fun emptyEntriesNull() {
        val s = ForageSelector(mapOf("PLAINS" to emptyList()))
        assertNull(s.pickForBiome("PLAINS", 0.5))
    }

    @Test
    fun weightedSegmentsByRoll() {
        // сума 80; a:[0,50) b:[50,80)
        val s = ForageSelector(mapOf("PLAINS" to listOf(ForageEntry("a", 50), ForageEntry("b", 30))))
        assertEquals("a", s.pickForBiome("PLAINS", 0.1)) // target 8  -> a
        assertEquals("a", s.pickForBiome("PLAINS", 0.6)) // target 48 -> a
        assertEquals("b", s.pickForBiome("PLAINS", 0.7)) // target 56 -> b
        assertEquals("b", s.pickForBiome("PLAINS", 0.99)) // target ~79 -> b
    }

    @Test
    fun zeroWeightIgnored() {
        val s = ForageSelector(mapOf("PLAINS" to listOf(ForageEntry("zero", 0), ForageEntry("real", 10))))
        assertEquals("real", s.pickForBiome("PLAINS", 0.0))
    }

    /** Рецепти: Error 6 потребує "next", Error 7 — "current", Visionary 6 — "other". */
    private val recipes = mapOf(
        "Error" to mapOf(
            6 to RecipeDefinition(emptyList(), listOf("next")),
            7 to RecipeDefinition(emptyList(), listOf("current")),
        ),
        "Visionary" to mapOf(
            6 to RecipeDefinition(emptyList(), listOf("other")),
        ),
    )

    private fun biased() = ForageSelector(
        mapOf(
            "PLAINS" to listOf(
                ForageEntry("next", 10),
                ForageEntry("current", 10),
                ForageEntry("other", 10),
            ),
        ),
        recipes,
    )

    @Test
    fun convergenceFavoursNextSequenceOfOwnPathway() {
        // Посл. 7 гравця Error: "next" (Посл. 6) x4 = 40, "current" (Посл. 7) x2 = 20, "other" x1 = 10
        val s = biased()
        val bias = ConvergenceBias("Error", 7)
        assertEquals("next", s.pickForBiome("PLAINS", bias, 0.5)) // target 35 -> next [0,40)
        assertEquals("current", s.pickForBiome("PLAINS", bias, 0.7)) // target 49 -> current [40,60)
        assertEquals("other", s.pickForBiome("PLAINS", bias, 0.95)) // target 66.5 -> other [60,70)
    }

    @Test
    fun convergenceNeverDropsForeignIngredients() {
        val s = biased()
        val bias = ConvergenceBias("Error", 7)
        val seen = (0 until 1000).mapNotNull { s.pickForBiome("PLAINS", bias, it / 1000.0) }.toSet()
        assertEquals(setOf("next", "current", "other"), seen)
    }

    @Test
    fun nullBiasKeepsPlainBiomeWeights() {
        val s = biased()
        assertEquals(s.pickForBiome("PLAINS", 0.5), s.pickForBiome("PLAINS", null, 0.5))
        assertEquals("current", s.pickForBiome("PLAINS", 0.5)) // рівні ваги: 10/10/10
    }

    @Test
    fun pathwayMatchIsCaseInsensitive() {
        val s = biased()
        assertEquals(
            s.pickForBiome("PLAINS", ConvergenceBias("Error", 7), 0.5),
            s.pickForBiome("PLAINS", ConvergenceBias("error", 7), 0.5),
        )
    }

    @Test
    fun mainIngredientsAlsoCarryConvergenceWeight() {
        val s = ForageSelector(
            mapOf("PLAINS" to listOf(ForageEntry("mainNext", 10), ForageEntry("plain", 10))),
            mapOf("Error" to mapOf(6 to RecipeDefinition(listOf("mainNext"), emptyList()))),
        )
        // mainNext: x4 = 40, plain: x1 = 10; сума 50, mainNext у [0,40)
        assertEquals("mainNext", s.pickForBiome("PLAINS", ConvergenceBias("Error", 7), 0.79))
        assertEquals("plain", s.pickForBiome("PLAINS", ConvergenceBias("Error", 7), 0.81))
    }
}
