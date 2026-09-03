package me.vangoo.domain.rituals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RitualCatalogTest {

    @Test
    fun catalogHasSevenRituals() {
        assertEquals(7, RitualCatalog.ALL.size)
    }

    @Test
    fun sequenceGatingIsThreeFiveSeven() {
        assertEquals(3, RitualCatalog.availableFor(9).size)
        assertEquals(5, RitualCatalog.availableFor(8).size)
        assertEquals(7, RitualCatalog.availableFor(7).size)
        assertEquals(7, RitualCatalog.availableFor(0).size) // below that, everything stays
    }

    @Test
    fun seqSevenRitualsNeedFiveCandlesOthersThree() {
        for (r in RitualCatalog.ALL) {
            val expected = if (r.minSequence == 7) 5 else 3
            assertEquals(expected, r.candlesRequired, r.displayName)
        }
    }

    /**
     * Exactly two rituals take a sacrifice from the hand, and those are the two with no
     * material list: Sacrifice (burns anything) and Bestowment (pays with an ingredient of
     * another pathway, which cannot be expressed as an item key).
     */
    @Test
    fun handSacrificeRitualsHaveNoMaterialListAndViceVersa() {
        for (r in RitualCatalog.ALL) {
            if (r.type == RitualType.SACRIFICE || r.type == RitualType.BESTOWMENT) {
                assertTrue(r.requiresHandSacrifice, r.displayName)
                assertTrue(r.ingredients.isEmpty(), r.displayName)
            } else {
                assertFalse(r.requiresHandSacrifice, r.displayName)
                assertFalse(r.ingredients.isEmpty(), r.displayName)
            }
        }
    }

    @Test
    fun everyRitualResolvableByType() {
        for (type in RitualType.entries) {
            assertEquals(type, RitualCatalog.of(type).type)
        }
    }

    @Test
    fun namesAndDescriptionsAreUkrainianNonEmpty() {
        for (r in RitualCatalog.ALL) {
            assertFalse(r.displayName.isBlank())
            assertFalse(r.description.isBlank())
        }
    }
}
