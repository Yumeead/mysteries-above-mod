package me.vangoo.domain.rituals

/**
 * A line of the Book of Revelation: what the ingredient is called and where it comes from.
 * [source] is null if the origin is unknown (an ingredient found only in chests).
 */
data class IngredientHint(
    val displayName: String,
    val source: IngredientSourceIndex.Source?,
)
