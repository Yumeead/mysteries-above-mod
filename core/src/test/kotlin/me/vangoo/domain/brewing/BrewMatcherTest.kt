package me.vangoo.domain.brewing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BrewMatcherTest {

    // Fool Seq 5: main = puppeteer_heartstring; aux = spirit_thread_spool, wraithhold_essence
    private fun foolSeq5() = BrewRecipe(
        "Fool", 5,
        mapOf("custom:puppeteer_heartstring" to 1),
        mapOf("custom:spirit_thread_spool" to 1, "custom:wraithhold_essence" to 1),
    )

    private fun counts(vararg keys: String): Map<String, Int> = keys.groupingBy { it }.eachCount()

    @Test
    fun classicExactMatchSucceeds() {
        assertTrue(
            foolSeq5().matches(
                counts(
                    "custom:puppeteer_heartstring",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun missingAuxiliaryFails() {
        assertFalse(
            foolSeq5().matches(counts("custom:puppeteer_heartstring", "custom:spirit_thread_spool")),
        )
    }

    @Test
    fun extraUnknownItemFails() {
        assertFalse(
            foolSeq5().matches(
                counts(
                    "custom:puppeteer_heartstring",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                    "vanilla:DIRT",
                ),
            ),
        )
    }

    @Test
    fun wrongCountFails() {
        assertFalse(
            foolSeq5().matches(
                counts(
                    "custom:puppeteer_heartstring",
                    "custom:puppeteer_heartstring",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun characteristicReplacesAllMainsSucceeds() {
        assertTrue(
            foolSeq5().matches(
                counts(
                    "characteristic:Fool:5",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun characteristicWrongPathwayFails() {
        assertFalse(
            foolSeq5().matches(
                counts(
                    "characteristic:Door:5",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun characteristicWrongSequenceFails() {
        assertFalse(
            foolSeq5().matches(
                counts(
                    "characteristic:Fool:6",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun characteristicPlusLeftoverMainFails() {
        assertFalse(
            foolSeq5().matches(
                counts(
                    "characteristic:Fool:5",
                    "custom:puppeteer_heartstring",
                    "custom:spirit_thread_spool",
                    "custom:wraithhold_essence",
                ),
            ),
        )
    }

    @Test
    fun characteristicMissingAuxiliaryFails() {
        assertFalse(
            foolSeq5().matches(counts("characteristic:Fool:5", "custom:spirit_thread_spool")),
        )
    }

    @Test
    fun recipeWithNoAuxiliarySingleCharacteristicSucceedsDoubleFails() {
        val noAux = BrewRecipe("Error", 9, mapOf("custom:sphinx_brain" to 1), emptyMap())
        assertTrue(noAux.matches(counts("characteristic:Error:9")))
        assertFalse(noAux.matches(mapOf("characteristic:Error:9" to 2)))
    }

    @Test
    fun matcherReturnsFirstMatchingRecipe() {
        val match = BrewMatcher().findMatch(
            listOf(foolSeq5()),
            counts(
                "custom:puppeteer_heartstring",
                "custom:spirit_thread_spool",
                "custom:wraithhold_essence",
            ),
        )
        assertNotNull(match)
        assertEquals("Fool", match!!.pathwayName)
        assertEquals(5, match.sequence)
    }

    @Test
    fun matcherReturnsNullWhenNothingMatches() {
        assertNull(BrewMatcher().findMatch(listOf(foolSeq5()), counts("vanilla:DIRT")))
    }

    @Test
    fun characteristicItemKeyMatchesRecipeKey() {
        assertEquals(foolSeq5().characteristicKey(), Characteristic("Fool", 5).itemKey())
    }
}
