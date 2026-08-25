package me.vangoo.domain.valueobjects

data class LootItem(
    val itemId: String,
    val weight: Int,
    val minAmount: Int,
    val maxAmount: Int,
    /** Без указаного тіру предмет = BASE. */
    val tier: LootTier = LootTier.BASE,
)
