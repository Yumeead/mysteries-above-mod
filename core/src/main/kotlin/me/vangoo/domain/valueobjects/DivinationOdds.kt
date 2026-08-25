package me.vangoo.domain.valueobjects

import kotlin.math.min

/**
 * Чисте правило балансу: підсумковий шанс успіху гадання проти цілі з опором (Anti-Divination).
 *
 * Бере «сирий» шанс із [SequenceBasedSuccessChance] (різниця Sequence) і накладає
 * додаткові коригування:
 * - якщо кастер у перевазі — невеликий штраф, але не нижче підлоги;
 * - якщо кастер слабший — динамічне падіння, плюс окреме обмеження на велику різницю.
 *
 * Жодного RNG чи повідомлень — це детермінована математика. Сам кидок
 * (`rng < probability`) і вивід відсотка лишаються в шарі ефектів.
 */
data class DivinationOdds(val casterSequence: Int, val targetSequence: Int) {

    /** Імовірність успіху в діапазоні [0.0, 1.0]. */
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

    /** Готовий до показу відсоток (наприклад `"94%"`). */
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
