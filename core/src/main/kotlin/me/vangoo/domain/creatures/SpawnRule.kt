package me.vangoo.domain.creatures

/**
 * Правила появи істоти. Природний спавн — заміна ванільного моба ([naturalReplaces]) у своєму
 * біомі; структурний — вага в лотереї «скриню відкрито» ([structureChance] > 0 означає
 * «трапляється біля структур»). Ключів лут-таблиць структурний спавн не має: працює БУДЬ-ЯКА
 * структура, а кого саме приведе — вирішує послідовність гравця
 * ([CreatureSelector.pickForStructure]).
 */
data class SpawnRule(
    val naturalBiomes: List<String>,
    val naturalReplaces: List<String>,
    val naturalChance: Double,
    val structureChance: Double,
)
