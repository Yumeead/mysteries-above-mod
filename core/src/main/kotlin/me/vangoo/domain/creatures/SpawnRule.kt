package me.vangoo.domain.creatures

/**
 * A creature's appearance rules. A natural spawn is a replacement of a vanilla mob
 * ([naturalReplaces]) in its own biome; a structure spawn is a weight in the "a chest was
 * opened" lottery ([structureChance] > 0 means "turns up near structures"). A structure spawn
 * has no loot table keys: ANY structure works, and who exactly turns up is decided by the
 * player's sequence ([CreatureSelector.pickForStructure]).
 */
data class SpawnRule(
    val naturalBiomes: List<String>,
    val naturalReplaces: List<String>,
    val naturalChance: Double,
    val structureChance: Double,
)
