package me.vangoo.domain.market

import kotlin.math.min

/**
 * Інструкція оплати фізичними монетами з розміном: скільки фунтів/коппетів зняти
 * і скільки здачі повернути коппетами. Жадібно: спершу коппети, потім фунти.
 */
data class CoinChange(val takePounds: Int, val takeCoppets: Int, val changeCoppets: Int) {

    /** Фактично сплачена вартість у коппетах (зняте мінус здача). */
    val paidCoppets: Int
        get() = takePounds * PoundMoney.COPPETS_PER_POUND + takeCoppets - changeCoppets

    companion object {
        /** @return null, якщо в гаманці не вистачає на ціну. */
        fun make(poundsHeld: Int, coppetsHeld: Int, price: PoundMoney): CoinChange? {
            require(poundsHeld >= 0 && coppetsHeld >= 0) { "Гаманець не може бути від'ємним" }

            val takeCoppets = min(coppetsHeld, price.coppets)
            val remaining = price.coppets - takeCoppets
            val takePounds =
                (remaining + PoundMoney.COPPETS_PER_POUND - 1) / PoundMoney.COPPETS_PER_POUND
            if (takePounds > poundsHeld) return null

            val change = takePounds * PoundMoney.COPPETS_PER_POUND - remaining
            return CoinChange(takePounds, takeCoppets, change)
        }
    }
}
