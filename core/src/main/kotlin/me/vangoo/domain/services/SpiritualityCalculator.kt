package me.vangoo.domain.services

import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.Sequence
import kotlin.math.max
import kotlin.math.roundToInt

class SpiritualityCalculator {

    /** Calculate maximum spirituality based on sequence and mastery */
    fun calculateMaximumSpirituality(sequence: Sequence, mastery: Mastery): Int {
        if (sequence.level < 0) {
            return 0
        }

        val idx = 9 - minOf(9, sequence.level)

        val min = MIN_VALUES[idx]
        // Ensure max is not less than min
        val maxValue = maxOf(MAX_VALUES[idx], min)

        // Interpolate between min and max based on mastery percentage
        val t = mastery.value / 100.0

        return (min + (maxValue - min) * t).roundToInt()
    }

    fun calculateRegenerationRate(sequence: Sequence, mastery: Mastery): Int {
        val base = REGEN_RATES[sequence.level.coerceIn(0, 9)]

        val masteryFactor = mastery.value.coerceIn(0.0, 100.0) / 100.0
        val multiplier = 1.0 + masteryFactor * 0.5
        val regen = (base * multiplier).roundToInt()

        return max(1, regen)
    }

    private companion object {
        val MIN_VALUES = intArrayOf(100, 300, 600, 1000, 1600, 3000, 6000, 10000, 12000, 14000)
        val MAX_VALUES = intArrayOf(200, 500, 900, 1500, 2000, 5000, 8000, 11000, 13000, 15000)

        // Regeneration base rates per sequence (points per second)
        // Sequence 0 = fastest, Sequence 9 = slowest
        val REGEN_RATES = intArrayOf(100, 40, 20, 15, 12, 6, 4, 3, 2, 1)
    }
}
