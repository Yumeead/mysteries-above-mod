package me.vangoo.domain.brewing

/** Підбирає перший рецепт із набору, що збігається з наданими інгредієнтами. */
class BrewMatcher {

    fun findMatch(recipes: Collection<BrewRecipe>, provided: Map<String, Int>): BrewRecipe? =
        recipes.firstOrNull { it.matches(provided) }
}
