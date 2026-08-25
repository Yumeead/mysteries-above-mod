package me.vangoo.domain.services

import me.vangoo.domain.valueobjects.Sequence

object MasteryProgressionCalculator {

    // Кількість духовності, необхідна для отримання 1.0 (одного цілого) пункту Mastery.
    // Тобто 1% від повного засвоєння.
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

    /** Повертає вартість 1 пункту (1%) Mastery для вказаної послідовності. */
    fun getSpiritualityPerOnePercent(sequence: Sequence): Double =
        SPIRITUALITY_PER_ONE_PERCENT[sequence.level.coerceIn(0, 9)]

    /**
     * Розраховує приріст Mastery (у дробових числах) на основі витраченої духовності.
     * Повертає double, наприклад 0.83, 1.5, 0.05 тощо.
     */
    fun calculateMasteryGain(spiritualitySpent: Int, sequence: Sequence): Double {
        if (spiritualitySpent <= 0) {
            return 0.0
        }
        // Приклад: cost=60, spent=30 -> результат 0.5 (піввідсотка)
        return spiritualitySpent / getSpiritualityPerOnePercent(sequence)
    }

    /** Повертає загальну кількість духовності для проходження від 0 до 100%. */
    fun getTotalSpiritualityForFullMastery(sequence: Sequence): Double =
        getSpiritualityPerOnePercent(sequence) * 100.0
}
