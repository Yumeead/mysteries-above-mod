package me.vangoo.domain.creatures

import me.vangoo.domain.valueobjects.LootTableData

/**
 * A creature's spawn/loot rules. The creature's content (stats, looks, skills) lives in the
 * creature engine's data; [id] is its key (the join key), [baseEntityType] is the base vanilla
 * type (it duplicates the type in the data; the pure domain needs it for the aquatic logic of
 * ambient spawning).
 */
data class CreatureDefinition(
    val id: String,
    val baseEntityType: String,
    val tier: CreatureTier,
    val loot: LootTableData,
    val spawn: SpawnRule,
    val pathway: String?,
    val sequence: Int,
)
