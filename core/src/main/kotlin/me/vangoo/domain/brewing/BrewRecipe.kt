package me.vangoo.domain.brewing

/**
 * A recipe matching rule (pure, no Minecraft). Ingredients are string keys in the form
 * `custom:<id>` / `vanilla:<MATERIAL>`; a Characteristic is `characteristic:<pathway>:<seq>`.
 *
 * NOT a data class: the constructor makes defensive copies of the maps, and the generated
 * `copy()` would go around them.
 */
class BrewRecipe(
    val pathwayName: String,
    val sequence: Int,
    mainCounts: Map<String, Int>,
    auxCounts: Map<String, Int>,
) {
    val mainCounts: Map<String, Int> = mainCounts.toMap()
    val auxCounts: Map<String, Int> = auxCounts.toMap()

    /** The Characteristic key of this (pathway, seq). */
    fun characteristicKey(): String = characteristicKey(pathwayName, sequence)

    /** Whether the given key set matches this recipe (either classically OR via the Characteristic). */
    fun matches(provided: Map<String, Int>): Boolean =
        matchesClassic(provided) || matchesViaCharacteristic(provided)

    private fun matchesClassic(provided: Map<String, Int>): Boolean {
        val required = HashMap(mainCounts)
        auxCounts.forEach { (k, v) -> required.merge(k, v, Int::plus) }
        return provided == required
    }

    private fun matchesViaCharacteristic(provided: Map<String, Int>): Boolean {
        // 1× Characteristic instead of ALL the main ones + the same auxiliaries, nothing extra.
        // A Characteristic key cannot collide with an aux key.
        val required = HashMap(auxCounts).apply { put(characteristicKey(), 1) }
        return provided == required
    }

    override fun equals(other: Any?): Boolean =
        other is BrewRecipe &&
            other.pathwayName == pathwayName &&
            other.sequence == sequence &&
            other.mainCounts == mainCounts &&
            other.auxCounts == auxCounts

    override fun hashCode(): Int =
        ((pathwayName.hashCode() * 31 + sequence) * 31 + mainCounts.hashCode()) * 31 + auxCounts.hashCode()

    override fun toString(): String =
        "BrewRecipe(pathwayName=$pathwayName, sequence=$sequence, mainCounts=$mainCounts, auxCounts=$auxCounts)"
}
