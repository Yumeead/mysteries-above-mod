package me.vangoo.domain.market

import kotlin.math.ceil

/**
 * Грошова сума підпільного ринку. Внутрішнє представлення — коппети.
 * 1 золотий фунт = [COPPETS_PER_POUND] коппетів.
 */
data class PoundMoney(val coppets: Int) {

    init {
        require(coppets >= 0) { "Сума не може бути від'ємною: $coppets" }
    }

    val wholePounds: Int get() = coppets / COPPETS_PER_POUND

    val remainderCoppets: Int get() = coppets % COPPETS_PER_POUND

    val isZero: Boolean get() = coppets == 0

    operator fun plus(other: PoundMoney): PoundMoney = PoundMoney(coppets + other.coppets)

    /** Кидає [IllegalArgumentException], якщо результат від'ємний (через `init`). */
    operator fun minus(other: PoundMoney): PoundMoney = PoundMoney(coppets - other.coppets)

    /** Множить суму на кількість одиниць (напр. ціна за одиницю × стак); [factor] не може бути від'ємним. */
    operator fun times(factor: Int): PoundMoney {
        require(factor >= 0) { "Множник не може бути від'ємним: $factor" }
        return PoundMoney(coppets * factor)
    }

    /** Комісія організатора: ceil(coppets × rate). */
    fun commission(rate: Double): PoundMoney = PoundMoney(ceil(coppets * rate).toInt())

    /** «2 ф 15 к» / «2 ф» / «15 к» / «0 к». */
    fun format(): String {
        val p = wholePounds
        val c = remainderCoppets
        return when {
            p > 0 && c > 0 -> "$p ф $c к"
            p > 0 -> "$p ф"
            else -> "$c к"
        }
    }

    companion object {
        const val COPPETS_PER_POUND = 20

        fun ofCoppets(coppets: Int): PoundMoney = PoundMoney(coppets)

        fun of(pounds: Int, coppets: Int): PoundMoney {
            require(pounds >= 0 && coppets >= 0) { "Сума не може бути від'ємною: $pounds ф $coppets к" }
            return PoundMoney(pounds * COPPETS_PER_POUND + coppets)
        }
    }
}
