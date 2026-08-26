package me.vangoo.domain.forage

import me.vangoo.domain.brewing.RecipeDefinition
import me.vangoo.domain.creatures.ConvergenceBias
import java.util.Locale

/**
 * Чисте правило вибору допоміжного інгредієнта для фореджу за біомом (аналог `CreatureSelector`).
 * Зважений детермінований вибір при поданому [roll] ∈ [0,1). Без стану.
 *
 * Закон конвергенції діє тут так само, як на спавні істот: інгредієнт із рецепта шляху гравця
 * важить більше — найбільше той, що потрібен для НАСТУПНОЇ послідовності. Це ВАГОВИЙ ухил у межах
 * таблиці біому, а не фільтр: чужі інгредієнти далі випадають, і загальна ймовірність появи ноди
 * не змінюється (її вирішує спавнер до виклику).
 */
class ForageSelector(
    byBiome: Map<String, List<ForageEntry>>,
    recipesByPathway: Map<String, Map<Int, RecipeDefinition>> = emptyMap(),
) {
    private val byBiome: Map<String, List<ForageEntry>> = byBiome.mapValues { it.value.toList() }

    /** ingredientId -> {"pathway:sequence"} усіх рецептів, де він потрібен (шлях у нижньому регістрі). */
    private val recipeSlots: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        recipesByPathway.forEach { (pathway, bySequence) ->
            bySequence.forEach { (sequence, def) ->
                val slot = slot(pathway, sequence)
                (def.mainIds + def.auxIds).forEach { id ->
                    getOrPut(id) { HashSet() }.add(slot)
                }
            }
        }
    }

    fun pickForBiome(biome: String?, roll: Double): String? = pickForBiome(biome, null, roll)

    fun pickForBiome(biome: String?, bias: ConvergenceBias?, roll: Double): String? {
        if (biome == null) return null
        val entries = byBiome[biome]?.filter { it.weight > 0 } ?: return null
        if (entries.isEmpty()) return null

        val weights = entries.map { it.weight * multiplier(it.ingredientId, bias) }
        val sum = weights.sum()

        val target = roll * sum
        var cumulative = 0.0
        for (i in entries.indices) {
            cumulative += weights[i]
            if (target < cumulative) return entries[i].ingredientId
        }
        return entries.last().ingredientId
    }

    private fun multiplier(ingredientId: String, bias: ConvergenceBias?): Double {
        val pathway = bias?.pathway ?: return 1.0
        val slots = recipeSlots[ingredientId] ?: return 1.0
        return when {
            slot(pathway, bias.sequenceLevel - 1) in slots -> NEXT_NEEDED_WEIGHT
            slot(pathway, bias.sequenceLevel) in slots -> CURRENT_WEIGHT
            else -> 1.0
        }
    }

    private companion object {
        const val NEXT_NEEDED_WEIGHT = 4.0 // sequenceLevel - 1
        const val CURRENT_WEIGHT = 2.0 // sequenceLevel

        fun slot(pathway: String, sequence: Int): String =
            "${pathway.lowercase(Locale.ROOT)}:$sequence"
    }
}
