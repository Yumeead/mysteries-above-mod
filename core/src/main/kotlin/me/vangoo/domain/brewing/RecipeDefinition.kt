package me.vangoo.domain.brewing

/**
 * A raw recipe description from the config: ids of the main and auxiliary ingredients.
 *
 * NOT a data class: the constructor makes defensive copies of the lists, and the generated
 * `copy()` would go around them — the same reason as in `CustomItem`.
 */
class RecipeDefinition(mainIds: List<String>, auxIds: List<String>) {
    val mainIds: List<String> = mainIds.toList()
    val auxIds: List<String> = auxIds.toList()

    override fun equals(other: Any?): Boolean =
        other is RecipeDefinition && other.mainIds == mainIds && other.auxIds == auxIds

    override fun hashCode(): Int = mainIds.hashCode() * 31 + auxIds.hashCode()

    override fun toString(): String = "RecipeDefinition(mainIds=$mainIds, auxIds=$auxIds)"
}
