package me.vangoo.domain.valueobjects

data class LootItem(
    val itemId: String,
    val weight: Int,
    val minAmount: Int,
    val maxAmount: Int,
    /** With no tier given, the item is BASE. */
    val tier: LootTier = LootTier.BASE,
)
