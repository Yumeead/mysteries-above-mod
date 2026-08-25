package me.vangoo.domain.valueobjects

/**
 * НЕ data class: конструктор нормалізує межі (від'ємний minItems → 0, maxItems не менший
 * за minItems), а згенерований `copy()` пройшов би повз нормалізацію — та сама причина,
 * що й у [Mastery].
 */
class LootTableData(
    val items: List<LootItem>,
    minItems: Int = 3,
    maxItems: Int = 8,
) {
    val minItems: Int = maxOf(0, minItems)
    val maxItems: Int = maxOf(this.minItems, maxItems)

    /** Підмножина таблиці лише з предметами дозволених тірів (зберігає minItems/maxItems). */
    fun filterByTier(allowed: Set<LootTier>): LootTableData =
        LootTableData(items.filter { it.tier in allowed }, minItems, maxItems)

    override fun equals(other: Any?): Boolean =
        other is LootTableData &&
            other.items == items &&
            other.minItems == minItems &&
            other.maxItems == maxItems

    override fun hashCode(): Int = (items.hashCode() * 31 + minItems) * 31 + maxItems

    override fun toString(): String = "LootTableData(items=$items, minItems=$minItems, maxItems=$maxItems)"
}
