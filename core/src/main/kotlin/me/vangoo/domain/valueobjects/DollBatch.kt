package me.vangoo.domain.valueobjects

import kotlin.math.floor
import kotlin.math.min

/**
 * Балансні числа паперових ляльок-замін (Seq 7 Фокусника шляху Блазня).
 *
 * Каст створює партію ляльок (базово 3, до 6 зі силою), списуючи папір.
 * Ляльки накопичуються до стелі й поглинають важкі удари. Чиста математика.
 */
object DollBatch {

    /** Скільки паперу коштує одна лялька в партії. */
    const val PAPER_PER_DOLL = 3

    /** Поріг «важкого» удару, який лялька поглинає (HP). */
    const val DAMAGE_THRESHOLD = 4.0

    /** Радіус випадкового телепорту після поглинання (блоки). */
    const val TELEPORT_RADIUS = 6

    private const val BASE_DOLLS = 3
    private const val MAX_DOLLS_PER_CAST = 6

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /** Скільки ляльок створює один каст за поточною послідовністю (3…6). */
    fun dollsPerCast(sequence: Sequence): Int {
        val dolls = BASE_DOLLS + floor(power(sequence) * 0.75).toInt()
        return min(MAX_DOLLS_PER_CAST, dolls)
    }

    /** Скільки паперу коштує один каст. */
    fun paperCost(sequence: Sequence): Int = dollsPerCast(sequence) * PAPER_PER_DOLL

    /** Стеля накопичення ляльок (можна відкласти більше однієї партії). */
    fun maxStored(sequence: Sequence): Int = dollsPerCast(sequence) + 3
}
