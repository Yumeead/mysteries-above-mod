package me.vangoo.domain.rituals

/**
 * A pure ritual recipe: the altar and ingredient requirements + the sequence gate.
 * Ingredients are string item keys (the domain does not import Minecraft); the behaviour layer
 * resolves them.
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
