package me.vangoo.domain.forage

import me.vangoo.domain.brewing.RecipeDefinition
import me.vangoo.domain.creatures.ConvergenceBias
import java.util.Locale

/**
 * The pure rule for picking an auxiliary forage ingredient by biome (the counterpart of
 * `CreatureSelector`). A weighted deterministic choice for a given [roll] ∈ [0,1). Stateless.
 *
 * The Law of Convergence works here just as it does on creature spawns: an ingredient from the
 * player's pathway recipe weighs more — most of all the one needed for the NEXT sequence. This
 * is a WEIGHTED bias within the biome table, not a filter: other pathways' ingredients still
 * drop, and the overall probability of a node appearing does not change (the spawner decides
 * that before the call).
 */
class ForageSelector(
    byBiome: Map<String, List<ForageEntry>>,
    recipesByPathway: Map<String, Map<Int, RecipeDefinition>> = emptyMap(),
) {
    private val byBiome: Map<String, List<ForageEntry>> = byBiome.mapValues { it.value.toList() }

    /** ingredientId -> {"pathway:sequence"} of every recipe that needs it (pathway in lower case). */
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
