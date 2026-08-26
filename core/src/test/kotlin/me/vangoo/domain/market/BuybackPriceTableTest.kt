package me.vangoo.domain.market

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BuybackPriceTableTest {

    private val ingredients = mapOf(9 to 3, 8 to 5, 7 to 8, 6 to 13, 5 to 20, 4 to 32, 3 to 50, 2 to 75, 1 to 105, 0 to 140)
    private val recipeBooks = mapOf(9 to 120, 8 to 170, 7 to 240, 6 to 330, 5 to 450, 4 to 600, 3 to 780, 2 to 980, 1 to 1200, 0 to 1500)
    private val characteristics = mapOf(9 to 80, 8 to 130, 7 to 220, 6 to 360, 5 to 540, 4 to 780, 3 to 1100, 2 to 1500, 1 to 2000, 0 to 2600)

    private val table = BuybackPriceTable(
        ingredients,
        2, // фолбек для інгредієнта без послідовності
        recipeBooks,
        characteristics,
        mapOf("custom:stellar_aqua_crystal" to 5), // точковий override
    )

    @Test
    fun ingredientPricedBySequence() {
        assertEquals(3, table.unitPriceFor(MarketItemCategory.INGREDIENT, 9, "custom:sphinx_brain").coppets)
        assertEquals(140, table.unitPriceFor(MarketItemCategory.INGREDIENT, 0, "custom:puppeteer_heartstring").coppets)
    }

    @Test
    fun ingredientWithoutSequenceUsesFallback() {
        // seq = -1 (не входить у жоден рецепт) → плоский фолбек
        assertEquals(2, table.unitPriceFor(MarketItemCategory.INGREDIENT, -1, "custom:loot_only_thing").coppets)
    }

    @Test
    fun recipeBookPricedBySequence() {
        assertEquals(120, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 9, "recipe:Fool:9").coppets)
        assertEquals(1500, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 0, "recipe:Door:0").coppets)
    }

    @Test
    fun characteristicPricedBySequence() {
        assertEquals(80, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 9, "characteristic:Fool:9").coppets)
        assertEquals(2600, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 0, "characteristic:Door:0").coppets)
    }

    @Test
    fun unknownSequenceFallsBackToZeroForPricedCategories() {
        // seq без запису в таблиці (окрім інгредієнта з його фолбеком) → 0 к
        assertEquals(0, table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, 42, "recipe:Fool:42").coppets)
        assertEquals(0, table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, 42, "characteristic:Fool:42").coppets)
    }

    @Test
    fun exactOverrideWinsOverCategoryDefault() {
        assertEquals(5, table.unitPriceFor(MarketItemCategory.INGREDIENT, 9, "custom:stellar_aqua_crystal").coppets)
    }

    @Test
    fun completeTablePricesEverySequenceForAllCategories() {
        for (seq in 0..9) {
            assertTrue(
                table.unitPriceFor(MarketItemCategory.INGREDIENT, seq, "custom:x").coppets > 0,
                "ingredient seq $seq must have a non-zero buyback price",
            )
            assertTrue(
                table.unitPriceFor(MarketItemCategory.RECIPE_BOOK, seq, "recipe:Fool:$seq").coppets > 0,
                "recipe book seq $seq must have a non-zero buyback price",
            )
            assertTrue(
                table.unitPriceFor(MarketItemCategory.CHARACTERISTIC, seq, "characteristic:Fool:$seq").coppets > 0,
                "characteristic seq $seq must have a non-zero buyback price",
            )
        }
    }
}
