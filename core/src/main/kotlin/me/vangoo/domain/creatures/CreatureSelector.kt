package me.vangoo.domain.creatures

/**
 * The pure rule for picking a creature to spawn (the counterpart of `BrewMatcher`).
 * Deterministic for a given roll. Each candidate occupies the segment
 * `[cumulative, cumulative+chance)` on the [0,1) axis; a roll past the sum of all chances
 * means "no spawn".
 */
class CreatureSelector(creatures: Collection<CreatureDefinition>) {

    private val creatures: List<CreatureDefinition> = creatures.toList()

    fun pickForBiome(
        biome: String?,
        baseEntityType: String?,
        roll: Double,
        bias: ConvergenceBias? = null,
    ): CreatureDefinition? {
        val candidates = creatures.filter {
            it.spawn.naturalChance > 0.0 &&
                biome in it.spawn.naturalBiomes &&
                baseEntityType in it.spawn.naturalReplaces
        }
        val totalChance = candidates.sumOf { it.spawn.naturalChance }
        if (candidates.isEmpty() || totalChance <= 0.0) return null

        val weights = candidates.map { it.spawn.naturalChance * multiplier(it, bias) }
        val sumWeights = weights.sum()
        // Renormalise so that the segments still sum to totalChance — P(spawn) is unchanged.
        val scale = if (sumWeights > 0.0) totalChance / sumWeights else 0.0

        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i] * scale
            if (roll < cumulative) return candidates[i]
        }
        return null
    }

    /**
     * Picking a creature for an ambient spawn: among all creatures whose natural biome matches
     * the current one, regardless of pathway. The Law of Convergence stays a WEIGHTED bias
     * (one's own pathway and the "next needed" sequence weigh more through [multiplier]),
     * not a hard filter. There is no "no-spawn threshold" — the decision to spawn has already
     * been made by the scheduler; this method only picks WHO. [roll] is in [0,1).
     */
    fun pickForAmbient(biome: String?, bias: ConvergenceBias?, roll: Double): CreatureDefinition? {
        if (bias == null) return null
        val candidates = creatures.filter { it.spawn.naturalChance > 0.0 && biome in it.spawn.naturalBiomes }
        return weightedPick(candidates, { it.spawn.naturalChance }, bias, roll)
    }

    /**
     * Picking a creature for a structure spawn — that is, "a chest was opened". Loot table keys
     * take NO part in the choice: any structure works (vanilla, datapack, modded), because
     * otherwise vanilla chests would yield nobody but a wandering spirit.
     *
     * The pool is a window of TWO sequences around the one who opened it: his own and the next,
     * stronger one (`seq` and `seq - 1`). Seq. 9 sees 9–8, Seq. 8 sees 8–7, …, Seq. 6 sees 6–5.
     * This also replaces [ApexGate] on this path: Seq. 5 is available only to a Seq. 6 or Seq. 5
     * player, which is exactly the same rule, only expressed as a window.
     *
     * The decision "whether to spawn at all" is not made by this method but by the caller from
     * the config — here it is only WHO, with the Law of Convergence bias, as in ambient.
     * [roll] is in [0,1).
     */
    fun pickForStructure(playerSequence: Int, bias: ConvergenceBias?, roll: Double): CreatureDefinition? {
        val candidates = creatures.filter {
            it.spawn.structureChance > 0.0 &&
                (it.sequence == playerSequence || it.sequence == playerSequence - 1)
        }
        return weightedPick(candidates, { it.spawn.structureChance }, bias, roll)
    }

    private fun multiplier(def: CreatureDefinition, bias: ConvergenceBias?): Double {
        val biasPathway = bias?.pathway ?: return 1.0
        if (def.pathway == null || !def.pathway.equals(biasPathway, ignoreCase = true)) return 1.0
        return when (def.sequence) {
            bias.sequenceLevel - 1 -> NEXT_NEEDED_WEIGHT
            bias.sequenceLevel -> CURRENT_WEIGHT
            else -> 1.0
        }
    }

    /**
     * A weighted pick with no "no-spawn threshold": the decision to spawn has been made, all
     * that is left is choosing whom. Shared by ambient and structures — they differ only in
     * the pool and the weight field.
     */
    private fun weightedPick(
        candidates: List<CreatureDefinition>,
        weightOf: (CreatureDefinition) -> Double,
        bias: ConvergenceBias?,
        roll: Double,
    ): CreatureDefinition? {
        if (candidates.isEmpty()) return null

        val weights = candidates.map { weightOf(it) * multiplier(it, bias) }
        val sumWeights = weights.sum()
        if (sumWeights <= 0.0) return null

        val target = roll * sumWeights
        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (target < cumulative) return candidates[i]
        }
        return candidates.last()
    }

    private companion object {
        const val NEXT_NEEDED_WEIGHT = 4.0 // sequenceLevel - 1
        const val CURRENT_WEIGHT = 2.0 // sequenceLevel
    }
}
