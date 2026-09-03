package me.vangoo.domain.market

/**
 * A price on the market: an item requirement (a swap) and/or money (a boot). At least one of
 * the two is present. A pure rule. The organiser's commission is taken only from PURELY
 * monetary deals; any item component makes the deal a barter with no commission.
 */
data class Consideration(val item: ItemDemand?, val money: PoundMoney) {

    /** How much of what must be given as the item part of the price (amount > 0). */
    data class ItemDemand(val itemKey: String, val amount: Int) {
        init {
            require(itemKey.isNotEmpty()) { "itemKey не може бути порожнім" }
            require(amount > 0) { "Кількість має бути додатною: $amount" }
        }
    }

    init {
        require(item != null || !money.isZero) {
            "Ціна не може бути порожньою: потрібні гроші або предмет"
        }
    }

    val isBarter: Boolean get() = item != null

    val hasMoney: Boolean get() = !money.isZero

    /** Commission: zero for a barter, ceil(money×rate) for a purely monetary deal. */
    fun commission(rate: Double): PoundMoney =
        if (isBarter) PoundMoney.ofCoppets(0) else money.commission(rate)

    companion object {
        /** A purely monetary price (as it was before barter). */
        fun money(money: PoundMoney): Consideration = Consideration(null, money)

        /** An item price with an optional monetary boot ([boot] may be 0). */
        fun of(item: ItemDemand, boot: PoundMoney): Consideration = Consideration(item, boot)
    }
}
