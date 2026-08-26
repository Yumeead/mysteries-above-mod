package me.vangoo.domain.market

import java.util.UUID
import kotlin.random.Random

/**
 * Агрегат одного збору: учасники з псевдонімами, лоти, замовлення, торги, комісія.
 *
 * Чисте правило: предмети представлені `itemKey` (`custom:<id>` /
 * `characteristic:<pathway>:<seq>` / `recipe:<pathway>:<seq>`), реальні стаки тримає
 * ескроу-сховище в шарі застосунку за `escrowRef` (lotId / negotiationId).
 */
class MarketSession(
    private val commissionRate: Double,
    private val random: Random,
) {
    init {
        require(commissionRate >= 0.0 && commissionRate < 1.0) {
            "Commission rate must be in [0,1): $commissionRate"
        }
    }

    /** Порушення правила ринку; [message] українською, показується гравцеві як є. */
    class MarketException(message: String) : RuntimeException(message)

    data class Lot(
        val lotId: UUID,
        val sellerId: UUID,
        val itemKey: String,
        val amount: Int,
        val price: Consideration,
        val sold: Boolean,
    )

    /** Команда застосунку: хто платить, хто отримує, скільки згорає, який ескроу передати. */
    data class Settlement(
        val payerId: UUID,
        val payeeId: UUID,
        val price: Consideration,
        val commissionPaid: PoundMoney,
        val sellerProceeds: PoundMoney,
        val payerCharge: CoinChange,
        val escrowRef: UUID,
        val itemKey: String,
        val amount: Int,
    )

    /** Команда застосунку: повернути ескроу-стак власникові. */
    data class Refund(val ownerId: UUID, val escrowRef: UUID)

    data class BuyOrder(
        val orderId: UUID,
        val buyerId: UUID,
        val itemKey: String,
        val amount: Int,
        val fulfilled: Boolean,
    )

    enum class NegotiationState { OPEN, ACCEPTED, WITHDRAWN }

    /** Проєкція торгу для UI (без мутабельного стану назовні). */
    data class NegotiationView(
        val negotiationId: UUID,
        val orderId: UUID,
        val sellerId: UUID,
        val buyerId: UUID,
        val currentPrice: Consideration,
        val turnOf: UUID,
        val state: NegotiationState,
        val itemKey: String,
        val amount: Int,
    )

    data class AcceptResult(val settlement: Settlement, val releasedEscrows: List<Refund>)

    private class Negotiation(
        val negotiationId: UUID,
        val orderId: UUID,
        val sellerId: UUID,
        val buyerId: UUID,
        var currentPrice: Consideration,
        /** Хто ОТРИМАВ останню ціну — тільки він може прийняти або дати зустрічну. */
        var turnOf: UUID,
    ) {
        var state: NegotiationState = NegotiationState.OPEN
    }

    private val aliases = LinkedHashMap<UUID, Int>()
    private val lots = LinkedHashMap<UUID, Lot>()
    private val orders = LinkedHashMap<UUID, BuyOrder>()
    private val negotiations = LinkedHashMap<UUID, Negotiation>()
    private var closed = false

    /** Реєструє учасника; повертає його номер-псевдонім (ідемпотентно). */
    fun registerParticipant(playerId: UUID): Int {
        aliases[playerId]?.let { return it }

        // Діапазон розширюється разом із кількістю учасників, щоб щонайменше половина
        // кандидатів завжди була вільна — інакше зі 100-м учасником цикл зависав би назавжди.
        val bound = maxOf(99, (aliases.size + 1) * 2)
        val taken = aliases.values.toSet()
        var alias: Int
        do {
            alias = 1 + random.nextInt(bound)
        } while (alias in taken)

        aliases[playerId] = alias
        return alias
    }

    fun isParticipant(playerId: UUID): Boolean = playerId in aliases

    fun aliasOf(playerId: UUID): String {
        val alias = aliases[playerId] ?: throw MarketException("Ви не учасник збору")
        return "Незнайомець №$alias"
    }

    fun listLot(sellerId: UUID, itemKey: String, amount: Int, price: Consideration): UUID {
        ensureOpen()
        ensureParticipant(sellerId)
        if (amount <= 0) throw MarketException("Кількість має бути додатною")
        // Інваріант Consideration уже гарантує непорожню ціну (гроші > 0 або предметна вимога).

        val lotId = UUID.randomUUID()
        lots[lotId] = Lot(lotId, sellerId, itemKey, amount, price, false)
        return lotId
    }

    fun activeLots(): List<Lot> = lots.values.filterNot { it.sold }

    fun buyLot(buyerId: UUID, lotId: UUID, buyerPounds: Int, buyerCoppets: Int): Settlement {
        ensureOpen()
        ensureParticipant(buyerId)

        val lot = lots[lotId]?.takeUnless { it.sold } ?: throw MarketException("Лот уже недоступний")
        if (lot.sellerId == buyerId) throw MarketException("Це ваш власний лот")

        val charge = CoinChange.make(buyerPounds, buyerCoppets, lot.price.money)
            ?: throw MarketException("Недостатньо монет: потрібно ${lot.price.money.format()}")

        lots[lotId] = lot.copy(sold = true)
        val commission = lot.price.commission(commissionRate)
        return Settlement(
            buyerId, lot.sellerId, lot.price, commission,
            lot.price.money - commission, charge, lotId, lot.itemKey, lot.amount,
        )
    }

    fun placeOrder(buyerId: UUID, itemKey: String, amount: Int, allowedKeys: Set<String>): UUID {
        ensureOpen()
        ensureParticipant(buyerId)
        if (amount <= 0) throw MarketException("Кількість має бути додатною")
        if (itemKey !in allowedKeys) {
            throw MarketException("Можна замовляти лише інгредієнти відомих вам рецептів")
        }

        val orderId = UUID.randomUUID()
        orders[orderId] = BuyOrder(orderId, buyerId, itemKey, amount, false)
        return orderId
    }

    fun openOrders(): List<BuyOrder> = orders.values.filterNot { it.fulfilled }

    /** Оферта продавця. Застосунок кладе предмет у ескроу за повернутим negotiationId. */
    fun offerOnOrder(sellerId: UUID, orderId: UUID, price: Consideration): UUID {
        ensureOpen()
        ensureParticipant(sellerId)

        val order = orders[orderId]?.takeUnless { it.fulfilled }
            ?: throw MarketException("Замовлення вже недоступне")
        if (order.buyerId == sellerId) throw MarketException("Це ваше власне замовлення")

        val negotiationId = UUID.randomUUID()
        // Покупець отримав першу ціну — хід його.
        negotiations[negotiationId] =
            Negotiation(negotiationId, orderId, sellerId, order.buyerId, price, order.buyerId)
        return negotiationId
    }

    fun counter(actorId: UUID, negotiationId: UUID, newPrice: Consideration) {
        ensureOpen()
        val negotiation = openNegotiation(negotiationId)
        if (negotiation.turnOf != actorId) throw MarketException("Зараз не ваш хід у цьому торзі")

        negotiation.currentPrice = newPrice
        negotiation.turnOf =
            if (actorId == negotiation.buyerId) negotiation.sellerId else negotiation.buyerId
    }

    /**
     * Акцепт того, чий зараз хід. Платить ЗАВЖДИ покупець, тому гаманець
     * ([buyerPounds]/[buyerCoppets]) — покупця, незалежно від того, хто приймає.
     */
    fun accept(actorId: UUID, negotiationId: UUID, buyerPounds: Int, buyerCoppets: Int): AcceptResult {
        ensureOpen()
        val negotiation = openNegotiation(negotiationId)
        if (negotiation.turnOf != actorId) {
            throw MarketException("Прийняти може лише той, хто отримав останню ціну")
        }

        val order = orders.getValue(negotiation.orderId)
        val charge = CoinChange.make(buyerPounds, buyerCoppets, negotiation.currentPrice.money)
            ?: throw MarketException(
                "У покупця недостатньо монет: потрібно ${negotiation.currentPrice.money.format()}",
            )

        negotiation.state = NegotiationState.ACCEPTED
        orders[order.orderId] = order.copy(fulfilled = true)

        // Решта торгів на це замовлення програли — ескроу продавців повертається.
        val released = negotiations.values
            .filter { it.orderId == negotiation.orderId && it.state == NegotiationState.OPEN }
            .map {
                it.state = NegotiationState.WITHDRAWN
                Refund(it.sellerId, it.negotiationId)
            }

        val commission = negotiation.currentPrice.commission(commissionRate)
        val settlement = Settlement(
            negotiation.buyerId, negotiation.sellerId, negotiation.currentPrice, commission,
            negotiation.currentPrice.money - commission, charge, negotiationId,
            order.itemKey, order.amount,
        )
        return AcceptResult(settlement, released)
    }

    /** Відмова будь-якої сторони; ескроу повертається продавцеві. */
    fun withdraw(actorId: UUID, negotiationId: UUID): Refund {
        ensureOpen()
        val negotiation = openNegotiation(negotiationId)
        if (actorId != negotiation.sellerId && actorId != negotiation.buyerId) {
            throw MarketException("Ви не сторона цього торгу")
        }

        negotiation.state = NegotiationState.WITHDRAWN
        return Refund(negotiation.sellerId, negotiationId)
    }

    fun negotiationsOf(playerId: UUID): List<NegotiationView> = negotiations.values
        .filter {
            it.state == NegotiationState.OPEN && (it.sellerId == playerId || it.buyerId == playerId)
        }
        .map {
            val order = orders.getValue(it.orderId)
            NegotiationView(
                it.negotiationId, it.orderId, it.sellerId, it.buyerId,
                it.currentPrice, it.turnOf, it.state, order.itemKey, order.amount,
            )
        }

    /** Закриває сесію: усі непродані лоти й відкриті торги → повернення власникам. */
    fun close(): List<Refund> {
        if (closed) return emptyList()
        closed = true

        val lotRefunds = lots.values.filterNot { it.sold }.map { Refund(it.sellerId, it.lotId) }
        val negotiationRefunds = negotiations.values
            .filter { it.state == NegotiationState.OPEN }
            .map {
                it.state = NegotiationState.WITHDRAWN
                Refund(it.sellerId, it.negotiationId)
            }
        return lotRefunds + negotiationRefunds
    }

    private fun openNegotiation(negotiationId: UUID): Negotiation =
        negotiations[negotiationId]?.takeIf { it.state == NegotiationState.OPEN }
            ?: throw MarketException("Цей торг уже завершено")

    private fun ensureOpen() {
        if (closed) throw MarketException("Ринок уже закрито")
    }

    private fun ensureParticipant(playerId: UUID) {
        if (playerId !in aliases) throw MarketException("Ви не учасник збору")
    }
}
