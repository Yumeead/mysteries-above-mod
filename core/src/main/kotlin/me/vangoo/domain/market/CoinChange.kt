package me.vangoo.domain.market

import kotlin.math.min

/**
 * A payment instruction with physical coins and change: how many pounds/coppets to take and
 * how much change to give back in coppets. Greedy: coppets first, then pounds.
 */
data class CoinChange(val takePounds: Int, val takeCoppets: Int, val changeCoppets: Int) {

    /** The value actually paid, in coppets (taken minus change). */
    val paidCoppets: Int
        get() = takePounds * PoundMoney.COPPETS_PER_POUND + takeCoppets - changeCoppets

    companion object {
        /** @return null if the wallet does not cover the price. */
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
