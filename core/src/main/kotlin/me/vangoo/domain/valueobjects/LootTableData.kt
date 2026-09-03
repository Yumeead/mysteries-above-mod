package me.vangoo.domain.valueobjects

/**
 * NOT a data class: the constructor normalises the bounds (a negative minItems → 0, maxItems
 * never below minItems), and the generated `copy()` would go around the normalisation — the
 * same reason as in [Mastery].
 */
class LootTableData(
    val items: List<LootItem>,
    minItems: Int = 3,
    maxItems: Int = 8,
) {
    val minItems: Int = maxOf(0, minItems)
    val maxItems: Int = maxOf(this.minItems, maxItems)

    /** A subset of the table with items of the allowed tiers only (keeps minItems/maxItems). */
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
