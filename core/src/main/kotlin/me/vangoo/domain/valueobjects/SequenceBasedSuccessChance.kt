package me.vangoo.domain.valueobjects

import kotlin.math.abs
import kotlin.random.Random

/**
 * Value Object: Represents success chance based on sequence difference.
 *
 * Rules from Lord of Mysteries universe:
 * - Same sequence: 100% success
 * - Higher attacking lower: 90-100% success
 * - Lower attacking higher: 10-50% success
 * - Large difference (3+): very hard/impossible for weaker
 */
data class SequenceBasedSuccessChance(val casterSequence: Int, val targetSequence: Int) {

    init {
        require(casterSequence in 0..9) { "Caster sequence must be 0-9, got: $casterSequence" }
        require(targetSequence in 0..9) { "Target sequence must be 0-9, got: $targetSequence" }
    }

    /**
     * Calculate success chance percentage (0.0 to 1.0)
     *
     * @return Probability of success
     */
    fun calculateChance(): Double {
        val difference = casterSequence - targetSequence

        // Same sequence - always succeed
        if (difference == 0) {
            return SAME_SEQUENCE_CHANCE
        }

        // Caster is weaker (higher sequence number)
        if (difference > 0) {
            return calculateWeakerAttackingStronger(difference)
        }

        // Caster is stronger (lower sequence number)
        return 1.0
    }

    /**
     * Calculate chance when weaker attacks stronger.
     * Lower chance with bigger difference.
     */
    private fun calculateWeakerAttackingStronger(difference: Int): Double {
        // difference is 1, 2, 3, 4, ...
        // Map to 50% - 10% range (decreasing)
        if (difference >= LARGE_DIFFERENCE_THRESHOLD) {
            return LOWER_TO_HIGHER_MIN // 10% for 3+ difference
        }

        // Linear interpolation between 50% and 10%
        // diff=1: 50%, diff=2: 30%, diff=3: 10%
        val range = LOWER_TO_HIGHER_BASE - LOWER_TO_HIGHER_MIN
        val step = range / LARGE_DIFFERENCE_THRESHOLD

        return LOWER_TO_HIGHER_BASE - step * difference
    }

    /**
     * Roll for success based on calculated chance
     *
     * @return true if successful, false if failed
     */
    fun rollSuccess(): Boolean = Random.nextDouble() < calculateChance()

    /**
     * Get formatted chance percentage for display
     *
     * @return Percentage string (e.g., "85%")
     */
    fun getFormattedChance(): String = "%.0f%%".format(calculateChance() * 100)

    /**
     * Check if this represents an advantageous situation for caster
     *
     * @return true if caster is stronger or equal
     */
    fun isCasterAdvantaged(): Boolean = casterSequence <= targetSequence

    /** Get the absolute sequence difference */
    fun getSequenceDifference(): Int = abs(casterSequence - targetSequence)

    /** Check if difference is considered "large" (3+) */
    fun isLargeDifference(): Boolean = getSequenceDifference() >= LARGE_DIFFERENCE_THRESHOLD

    private companion object {
        const val SAME_SEQUENCE_CHANCE = 1.0  // 100%
        const val LOWER_TO_HIGHER_MIN = 0.10  // 10%
        const val LOWER_TO_HIGHER_BASE = 0.50 // 50%
        const val LARGE_DIFFERENCE_THRESHOLD = 3
    }
}
