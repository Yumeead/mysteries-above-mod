package me.vangoo.domain.market

/**
 * Ціна на ринку: предметна вимога (обмін) та/або гроші (буст). Мінімум одне з двох присутнє.
 * Чисте правило. Комісія організатора береться лише з ЧИСТО грошових угод; будь-яка предметна
 * складова робить угоду бартером без комісії.
 */
data class Consideration(val item: ItemDemand?, val money: PoundMoney) {

    /** Скільки й чого треба віддати як предметну частину ціни (amount > 0). */
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

    /** Комісія: нуль для бартеру, ceil(гроші×rate) для чисто грошової угоди. */
    fun commission(rate: Double): PoundMoney =
        if (isBarter) PoundMoney.ofCoppets(0) else money.commission(rate)

    companion object {
        /** Чисто грошова ціна (як було до бартера). */
        fun money(money: PoundMoney): Consideration = Consideration(null, money)

        /** Предметна ціна з опційним грошовим бустом ([boot] може бути 0). */
        fun of(item: ItemDemand, boot: PoundMoney): Consideration = Consideration(item, boot)
    }
}
