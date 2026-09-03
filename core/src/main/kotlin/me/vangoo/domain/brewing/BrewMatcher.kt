package me.vangoo.domain.brewing

/** Picks the first recipe in the set that matches the ingredients supplied. */
class BrewMatcher {

    fun findMatch(recipes: Collection<BrewRecipe>, provided: Map<String, Int>): BrewRecipe? =
        recipes.firstOrNull { it.matches(provided) }
}
