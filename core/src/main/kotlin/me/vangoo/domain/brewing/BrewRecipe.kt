package me.vangoo.domain.brewing

/**
 * Правило зіставлення рецепта (чисте, без Minecraft). Інгредієнти — рядкові ключі у форматі
 * `custom:<id>` / `vanilla:<MATERIAL>`; Характеристика — `characteristic:<шлях>:<seq>`.
 *
 * НЕ data class: конструктор робить захисні копії мап, а згенерований `copy()` пройшов би
 * повз них.
 */
class BrewRecipe(
    val pathwayName: String,
    val sequence: Int,
    mainCounts: Map<String, Int>,
    auxCounts: Map<String, Int>,
) {
    val mainCounts: Map<String, Int> = mainCounts.toMap()
    val auxCounts: Map<String, Int> = auxCounts.toMap()

    /** Ключ Характеристики цього (шлях, seq). */
    fun characteristicKey(): String = characteristicKey(pathwayName, sequence)

    /** Чи відповідає наданий набір ключів цьому рецепту (класично АБО через Характеристику). */
    fun matches(provided: Map<String, Int>): Boolean =
        matchesClassic(provided) || matchesViaCharacteristic(provided)

    private fun matchesClassic(provided: Map<String, Int>): Boolean {
        val required = HashMap(mainCounts)
        auxCounts.forEach { (k, v) -> required.merge(k, v, Int::plus) }
        return provided == required
    }

    private fun matchesViaCharacteristic(provided: Map<String, Int>): Boolean {
        // 1× Характеристика замість УСІХ основних + ті самі допоміжні, нічого зайвого.
        // Ключ Характеристики не може збігтися з aux-ключем.
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
