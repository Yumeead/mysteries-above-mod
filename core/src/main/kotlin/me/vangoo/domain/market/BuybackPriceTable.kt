package me.vangoo.domain.market

/**
 * Прайс скупки організатором (за одиницю, в коппетах). Свідомо нижче ринкових очікувань —
 * «підлога» цін і джерело емісії монет.
 *
 * Усі категорії скейляться від послідовності предмета (9 = найслабша … 0 = найсильніша):
 * інгредієнт — за послідовністю рецепта, де він ужитий, книга рецептів і Характеристика — за
 * власною послідовністю. Інгредієнт без послідовності (не входить у жоден рецепт) отримує
 * плоский [ingredientFallbackCoppets].
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
