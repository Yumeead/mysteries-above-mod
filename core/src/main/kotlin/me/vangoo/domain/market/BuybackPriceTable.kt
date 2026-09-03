package me.vangoo.domain.market

/**
 * The organiser's buyback price list (per unit, in coppets). Deliberately below market
 * expectations — a price "floor" and the source of coin emission.
 *
 * Every category scales from the item's sequence (9 = weakest … 0 = strongest): an ingredient
 * by the sequence of the recipe it is used in, a recipe book and a Characteristic by their own
 * sequence. An ingredient with no sequence (part of no recipe) gets the flat
 * [ingredientFallbackCoppets].
 */
data class BuybackPriceTable(
    val ingredientCoppetsBySeq: Map<Int, Int>,
    val ingredientFallbackCoppets: Int,
    val recipeBookCoppetsBySeq: Map<Int, Int>,
    val characteristicCoppetsBySeq: Map<Int, Int>,
    val overridesByItemKey: Map<String, Int>,
) {
    fun unitPriceFor(category: MarketItemCategory, sequenceOrMinus1: Int, itemKey: String): PoundMoney {
        overridesByItemKey[itemKey]?.let { return PoundMoney.ofCoppets(it) }

        val coppets = when (category) {
            MarketItemCategory.INGREDIENT ->
                ingredientCoppetsBySeq[sequenceOrMinus1] ?: ingredientFallbackCoppets

            MarketItemCategory.RECIPE_BOOK -> recipeBookCoppetsBySeq[sequenceOrMinus1] ?: 0

            MarketItemCategory.CHARACTERISTIC -> characteristicCoppetsBySeq[sequenceOrMinus1] ?: 0
        }
        return PoundMoney.ofCoppets(coppets)
    }
}
