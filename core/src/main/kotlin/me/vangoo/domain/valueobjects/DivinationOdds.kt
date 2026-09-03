package me.vangoo.domain.valueobjects

import kotlin.math.min

/**
 * A pure balance rule: the final success chance of divination against a target that
 * resists (Anti-Divination).
 *
 * Takes the "raw" chance from [SequenceBasedSuccessChance] (the Sequence difference) and
 * applies further corrections:
 * - if the caster has the advantage — a small penalty, but never below the floor;
 * - if the caster is weaker — a dynamic drop, plus a separate cap on a large gap.
 *
 * No RNG and no messages — this is deterministic math. The roll itself
 * (`rng < probability`) and printing the percentage stay in the effect layer.
 */
data class DivinationOdds(val casterSequence: Int, val targetSequence: Int) {

    /** Success probability in the range [0.0, 1.0]. */
    fun successProbability(): Double {
        val base = SequenceBasedSuccessChance(casterSequence, targetSequence)
        val baseChance = base.calculateChance()
        val diff = base.getSequenceDifference()

        val finalChance = if (base.isCasterAdvantaged()) {
            val penalty = min(ADVANTAGED_MAX_PENALTY, diff * ADVANTAGED_PENALTY_PER_STEP)
            maxOf(ADVANTAGED_FLOOR, baseChance * (1.0 - penalty))
        } else {
            val dynamic = 1.0 - DISADVANTAGED_DROP_PER_STEP * diff
            val dropped = baseChance * maxOf(DISADVANTAGED_MIN_FACTOR, dynamic)
            if (base.isLargeDifference()) {
                min(dropped, LARGE_DIFFERENCE_CAP_FACTOR * baseChance)
            } else {
                dropped
            }
        }

        return finalChance.coerceIn(0.0, 1.0)
    }

    /** Display-ready percentage (for example `"94%"`). */
    fun formattedPercent(): String = "%.0f%%".format(successProbability() * 100)

    private companion object {
        const val ADVANTAGED_FLOOR = 0.75
        const val ADVANTAGED_MAX_PENALTY = 0.2
        const val ADVANTAGED_PENALTY_PER_STEP = 0.03

        const val DISADVANTAGED_MIN_FACTOR = 0.05
        const val DISADVANTAGED_DROP_PER_STEP = 0.35
        const val LARGE_DIFFERENCE_CAP_FACTOR = 0.5
    }
}
