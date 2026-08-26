package me.vangoo.domain.rituals

/**
 * Чистий рецепт ритуалу: вимоги до вівтаря й інгредієнтів + гейт послідовності.
 * Інгредієнти — рядкові ключі предметів (домен не імпортує Minecraft); резолвить їх
 * шар поведінки.
 */
data class RitualRecipe(
    val type: RitualType,
    val displayName: String,
    val description: String,
    val minSequence: Int,
    val candlesRequired: Int,
    val ingredients: Map<String, Int>,
    val requiresHandSacrifice: Boolean,
) {
    fun availableAt(sequenceLevel: Int): Boolean = sequenceLevel <= minSequence
}
