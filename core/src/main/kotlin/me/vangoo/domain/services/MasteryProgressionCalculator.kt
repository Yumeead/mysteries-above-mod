package me.vangoo.domain.services

import me.vangoo.domain.valueobjects.Sequence

object MasteryProgressionCalculator {

    // The amount of spirituality needed to gain 1.0 (one whole) point of Mastery.
    // That is, 1% of full mastery.
    private val SPIRITUALITY_PER_ONE_PERCENT = doubleArrayOf(
        10000.0, // Sequence 0 - God (hardest)
        5000.0,  // Sequence 1 - Angel
        3500.0,  // Sequence 2 - Angel
        2500.0,  // Sequence 3 - Demigod
        1800.0,  // Sequence 4 - Demigod
        1200.0,  // Sequence 5 - Mid-Sequence
        800.0,   // Sequence 6
        500.0,   // Sequence 7
        300.0,   // Sequence 8
        60.0     // Sequence 9
    )

    /** Returns the cost of 1 point (1%) of Mastery for the given sequence. */
    fun getSpiritualityPerOnePercent(sequence: Sequence): Double =
        SPIRITUALITY_PER_ONE_PERCENT[sequence.level.coerceIn(0, 9)]

    /**
     * Computes the Mastery gain (as a fractional number) from the spirituality spent.
     * Returns a double, for example 0.83, 1.5, 0.05 and so on.
     */
    fun calculateMasteryGain(spiritualitySpent: Int, sequence: Sequence): Double {
        if (spiritualitySpent <= 0) {
            return 0.0
        }
        // Example: cost=60, spent=30 -> result 0.5 (half a percent)
        return spiritualitySpent / getSpiritualityPerOnePercent(sequence)
    }

    /** Returns the total spirituality needed to go from 0 to 100%. */
    fun getTotalSpiritualityForFullMastery(sequence: Sequence): Double =
        getSpiritualityPerOnePercent(sequence) * 100.0
}
