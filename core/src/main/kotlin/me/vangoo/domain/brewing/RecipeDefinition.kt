package me.vangoo.domain.brewing

/**
 * Сирий опис рецепта з конфіга: id основних та допоміжних інгредієнтів.
 *
 * НЕ data class: конструктор робить захисні копії списків, а згенерований `copy()`
 * пройшов би повз них — та сама причина, що й у `CustomItem`.
 */
class RecipeDefinition(mainIds: List<String>, auxIds: List<String>) {
    val mainIds: List<String> = mainIds.toList()
    val auxIds: List<String> = auxIds.toList()

    override fun equals(other: Any?): Boolean =
        other is RecipeDefinition && other.mainIds == mainIds && other.auxIds == auxIds

    override fun hashCode(): Int = mainIds.hashCode() * 31 + auxIds.hashCode()

    override fun toString(): String = "RecipeDefinition(mainIds=$mainIds, auxIds=$auxIds)"
}
