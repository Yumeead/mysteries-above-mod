package me.vangoo.domain.forage

/** One forage candidate: the id of an auxiliary ingredient + its weight in the biome table. */
data class ForageEntry(val ingredientId: String, val weight: Int)
