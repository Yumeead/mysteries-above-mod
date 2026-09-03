package me.vangoo.domain.market

import java.util.UUID
import kotlin.random.Random

/**
 * The aggregate of one gathering: participants with aliases, lots, orders, negotiations,
 * commission.
 *
 * A pure rule: items are represented by an `itemKey` (`custom:<id>` /
 * `characteristic:<pathway>:<seq>` / `recipe:<pathway>:<seq>`), the real stacks are held by
 * the escrow storage in the application layer under `escrowRef` (lotId / negotiationId).
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

    /** A breach of a market rule; [message] is Ukrainian and is shown to the player as is. */
    class MarketException(message: String) : RuntimeException(message)

    data class Lot(
        val lotId: UUID,
        val sellerId: UUID,
        val itemKey: String,
        val amount: Int,
        val price: Consideration,
        val sold: Boolean,
    )

    /** An application command: who pays, who receives, how much burns, which escrow to hand over. */
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

    /** An application command: return the escrowed stack to its owner. */
    data class Refund(val ownerId: UUID, val escrowRef: UUID)

    data class BuyOrder(
        val orderId: UUID,
        val buyerId: UUID,
        val itemKey: String,
        val amount: Int,
        val fulfilled: Boolean,
    )

    enum class NegotiationState { OPEN, ACCEPTED, WITHDRAWN }

    /** A projection of a negotiation for the UI (no mutable state leaks out). */
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
        /** Who RECEIVED the last price — only he may accept it or counter. */
        var turnOf: UUID,
    ) {
        var state: NegotiationState = NegotiationState.OPEN
    }

    private val aliases = LinkedHashMap<UUID, Int>()
    private val lots = LinkedHashMap<UUID, Lot>()
    private val orders = LinkedHashMap<UUID, BuyOrder>()
    private val negotiations = LinkedHashMap<UUID, Negotiation>()
    private var closed = false

    /** Registers a participant; returns his alias number (idempotent). */
    fun registerParticipant(playerId: UUID): Int {
        aliases[playerId]?.let { return it }

        // The range widens together with the number of participants so that at least half the
        // candidates are always free — otherwise the loop would hang forever on participant 100.
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
        // The Consideration invariant already guarantees a non-empty price (money > 0 or an item requirement).

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

    /** A seller's offer. The application puts the item into escrow under the returned negotiationId. */
    fun offerOnOrder(sellerId: UUID, orderId: UUID, price: Consideration): UUID {
        ensureOpen()
        ensureParticipant(sellerId)

        val order = orders[orderId]?.takeUnless { it.fulfilled }
            ?: throw MarketException("Замовлення вже недоступне")
        if (order.buyerId == sellerId) throw MarketException("Це ваше власне замовлення")

        val negotiationId = UUID.randomUUID()
        // The buyer received the first price — it is his move.
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
     * Acceptance by whoever is to move. The buyer ALWAYS pays, so the wallet
     * ([buyerPounds]/[buyerCoppets]) is the buyer's, regardless of who accepts.
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

        // The remaining negotiations for this order lost — the sellers' escrow comes back.
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

    /** A refusal by either side; the escrow goes back to the seller. */
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

    /** Closes the session: every unsold lot and open negotiation → returned to its owner. */
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
