package me.vangoo.domain.rituals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SacrificeAppraiserTest {

    @Test
    fun pathwayIngredientIsTheBestSacrifice() {
        assertEquals(300, SacrificeAppraiser.spiritualityFor(SacrificeKind.PATHWAY_INGREDIENT))
    }

    @Test
    fun valueStrictlyDecreasesByKind() {
        val values = SacrificeKind.entries.map { SacrificeAppraiser.spiritualityFor(it) }
        assertEquals(values.sortedDescending(), values, "порядок enum-а мусить іти від найціннішої жертви")
        assertTrue(values.zipWithNext().all { (a, b) -> a > b })
        assertTrue(values.last() > 0)
    }
}
