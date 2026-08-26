package me.vangoo.domain.rituals

/**
 * Рядок Книги одкровення: як інгредієнт зветься і звідки береться.
 * [source] — null, якщо джерело невідоме (інгредієнт лише зі скринь).
 */
data class IngredientHint(
    val displayName: String,
    val source: IngredientSourceIndex.Source?,
)
