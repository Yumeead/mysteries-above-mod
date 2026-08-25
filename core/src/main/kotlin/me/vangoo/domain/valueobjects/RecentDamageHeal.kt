package me.vangoo.domain.valueobjects

import kotlin.math.min

/**
 * Балансна формула зцілення на основі недавно отриманої шкоди
 * (Seq 7 Фокусника шляху Блазня — реворк «Перенос шкоди»).
 *
 * Лікує частку шкоди, отриманої за вікно, не більше стелі, що зростає зі
 * силою. НЕ лікує до фулл — це «загоєння» половини рани. Чиста математика.
 */
object RecentDamageHeal {

    /** Вікно, за яке рахується недавня шкода. */
    const val DAMAGE_WINDOW_MS = 10_000L

    private const val HEAL_FRACTION = 0.5
    private const val BASE_CEILING = 8

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /** Стеля разового зцілення (HP) за поточною послідовністю. */
    fun healCeiling(sequence: Sequence): Double = (BASE_CEILING + power(sequence)).toDouble()

    /** Скільки HP відновлює здібність із [recentDamage], отриманих за вікно. */
    fun healAmount(recentDamage: Double, sequence: Sequence): Double {
        if (recentDamage <= 0) return 0.0
        return min(recentDamage * HEAL_FRACTION, healCeiling(sequence))
    }
}
