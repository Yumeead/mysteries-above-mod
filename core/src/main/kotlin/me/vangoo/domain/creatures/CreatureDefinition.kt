package me.vangoo.domain.creatures

import me.vangoo.domain.valueobjects.LootTableData

/**
 * Правила спавну/луту істоти. Контент істоти (стати, вигляд, скіли) живе в даних рушія істот;
 * [id] — його ключ (join-ключ), [baseEntityType] — базовий ванільний тип (дублює тип у даних;
 * потрібен чистому домену для aquatic-логіки ambient-спавну).
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
