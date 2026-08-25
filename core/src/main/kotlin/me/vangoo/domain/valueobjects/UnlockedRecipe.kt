package me.vangoo.domain.valueobjects

/** Domain Value Object: Represents an unlocked potion recipe for a player */
data class UnlockedRecipe(val pathwayName: String, val sequence: Int) {

    init {
        require(pathwayName.isNotBlank()) { "Pathway name cannot be empty" }
        require(sequence in 0..9) { "Sequence must be between 0 and 9, got: $sequence" }
    }

    /** Get unique identifier for this recipe */
    fun getRecipeId(): String = "${pathwayName}_$sequence"

    override fun toString(): String = "$pathwayName Sequence $sequence"

    companion object {
        /** Create unlocked recipe */
        fun of(pathwayName: String, sequence: Int): UnlockedRecipe =
            UnlockedRecipe(pathwayName.trim(), sequence)
    }
}
