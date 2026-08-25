package me.vangoo.domain.valueobjects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.EnumSet

internal class LootTableDataTest {

    @Test
    fun filterKeepsOnlyAllowedTier() {
        val base = LootItem("a", 1, 1, 1, LootTier.BASE)
        val rare = LootItem("b", 1, 1, 1, LootTier.RARE)
        val t = LootTableData(listOf(base, rare), 1, 2)
        val onlyBase = t.filterByTier(EnumSet.of(LootTier.BASE))
        assertEquals(1, onlyBase.items.size)
        assertEquals("a", onlyBase.items[0].itemId)
    }

    @Test
    fun filterBothTiersKeepsAll() {
        val t = LootTableData(
            listOf(
                LootItem("a", 1, 1, 1, LootTier.BASE),
                LootItem("b", 1, 1, 1, LootTier.RARE),
            ),
            1, 2,
        )
        assertEquals(2, t.filterByTier(EnumSet.allOf(LootTier::class.java)).items.size)
    }

    @Test
    fun defaultConstructorIsBase() {
        assertEquals(LootTier.BASE, LootItem("a", 1, 1, 1).tier)
    }

    @Test
    fun filterPreservesMinMax() {
        val t = LootTableData(listOf(LootItem("a", 1, 1, 1, LootTier.BASE)), 3, 5)
        val f = t.filterByTier(EnumSet.of(LootTier.BASE))
        assertEquals(3, f.minItems)
        assertEquals(5, f.maxItems)
    }

    @Test
    fun emptyAllowedYieldsEmpty() {
        val t = LootTableData(listOf(LootItem("a", 1, 1, 1, LootTier.BASE)), 1, 2)
        assertTrue(t.filterByTier(EnumSet.noneOf(LootTier::class.java)).items.isEmpty())
    }
}
