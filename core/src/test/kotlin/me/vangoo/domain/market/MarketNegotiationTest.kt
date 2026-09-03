package me.vangoo.domain.market

import me.vangoo.domain.market.MarketSession.MarketException
import me.vangoo.domain.market.MarketSession.Refund
import me.vangoo.domain.market.MarketSession.Settlement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

internal class MarketNegotiationTest {

    private lateinit var session: MarketSession
    private val buyer: UUID = UUID.randomUUID()
    private val sellerA: UUID = UUID.randomUUID()
    private val sellerB: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        session = MarketSession(0.10, Random(7))
        session.registerParticipant(buyer)
        session.registerParticipant(sellerA)
        session.registerParticipant(sellerB)
    }

    private fun coppets(amount: Int) = Consideration.money(PoundMoney.ofCoppets(amount))

    @Test
    fun orderAllowsOnlyKnownIngredients() {
        assertThrows(MarketException::class.java) {
            session.placeOrder(buyer, "custom:unknown_root", 1, KNOWN)
        }
        assertThrows(MarketException::class.java) {
            session.placeOrder(buyer, "custom:crimson_star", 0, KNOWN)
        }
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 2, KNOWN)
        assertEquals(1, session.openOrders().size)
        assertEquals(orderId, session.openOrders()[0].orderId)
    }

    @Test
    fun turnAlternatesAndOnlyReceiverAccepts() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, coppets(20))
        // The seller named a price → the buyer's move; the seller cannot accept
        assertThrows(MarketException::class.java) { session.accept(sellerA, negId, 99, 99) }
        // The buyer counters → the seller's move; the buyer cannot accept
        session.counter(buyer, negId, coppets(14))
        assertThrows(MarketException::class.java) { session.accept(buyer, negId, 99, 99) }
        assertThrows(MarketException::class.java) { session.counter(buyer, negId, coppets(1)) }
        // The seller accepts the counter; the buyer ALWAYS pays
        val s = session.accept(sellerA, negId, 1, 0).settlement // the BUYER's wallet: 1 pound
        assertEquals(buyer, s.payerId)
        assertEquals(sellerA, s.payeeId)
        assertEquals(14, s.price.money.coppets)
        assertEquals(2, s.commissionPaid.coppets) // ceil(14×0.10)
        assertEquals(12, s.sellerProceeds.coppets)
        assertEquals(negId, s.escrowRef)
        assertTrue(session.openOrders().isEmpty())
    }

    @Test
    fun ownOrderCannotBeOfferedOn() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        assertThrows(MarketException::class.java) { session.offerOnOrder(buyer, orderId, coppets(20)) }
    }

    @Test
    fun negotiationViewIsVisibleToBothPartiesOnly() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 3, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, coppets(20))

        val view = session.negotiationsOf(buyer).single()
        assertEquals(negId, view.negotiationId)
        assertEquals(buyer, view.turnOf)
        assertEquals("custom:crimson_star", view.itemKey)
        assertEquals(3, view.amount)
        assertEquals(MarketSession.NegotiationState.OPEN, view.state)

        assertEquals(1, session.negotiationsOf(sellerA).size)
        assertTrue(session.negotiationsOf(sellerB).isEmpty())
    }

    @Test
    fun firstAcceptClosesOrderAndReleasesSiblings() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negA = session.offerOnOrder(sellerA, orderId, coppets(20))
        val negB = session.offerOnOrder(sellerB, orderId, coppets(18))
        val result = session.accept(buyer, negB, 1, 0)
        // The parallel negotiation A is cancelled automatically and its escrow returned
        assertEquals(listOf(Refund(sellerA, negA)), result.releasedEscrows)
        // A second accept on a dead negotiation is impossible
        assertThrows(MarketException::class.java) { session.accept(buyer, negA, 9, 9) }
        assertThrows(MarketException::class.java) { session.offerOnOrder(sellerA, orderId, coppets(5)) }
    }

    @Test
    fun acceptWithoutBuyerFundsKeepsNegotiationOpen() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, coppets(30))
        assertThrows(MarketException::class.java) { session.accept(buyer, negId, 1, 5) } // 25 < 30
        // the negotiation is alive — the buyer may keep bargaining
        session.counter(buyer, negId, coppets(25))
    }

    @Test
    fun withdrawByEitherPartyRefundsSeller() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, coppets(30))
        assertEquals(Refund(sellerA, negId), session.withdraw(buyer, negId))
        assertThrows(MarketException::class.java) { session.counter(buyer, negId, coppets(1)) }
    }

    @Test
    fun outsiderCannotWithdrawSomeoneElsesNegotiation() {
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, coppets(30))
        assertThrows(MarketException::class.java) { session.withdraw(sellerB, negId) }
    }

    @Test
    fun closeRefundsUnsoldLotsAndOpenNegotiations() {
        val lotId = session.listLot(sellerA, "custom:crimson_star", 1, coppets(10))
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerB, orderId, coppets(30))
        val refunds = session.close()
        assertTrue(Refund(sellerA, lotId) in refunds)
        assertTrue(Refund(sellerB, negId) in refunds)
        assertEquals(2, refunds.size)
        // after closing, every operation is rejected
        assertThrows(MarketException::class.java) {
            session.listLot(sellerA, "custom:crimson_star", 1, coppets(1))
        }
    }

    @Test
    fun closeIsIdempotentAndNeverRefundsLotTwice() {
        val lotId = session.listLot(sellerA, "custom:crimson_star", 1, coppets(10))
        assertEquals(listOf(Refund(sellerA, lotId)), session.close())
        // A second close (e.g. the scheduled end + the shutdown path) does not return the lot twice
        assertTrue(session.close().isEmpty())
    }

    /**
     * The conservation invariant: after any scenario, "what came in = what was handed out +
     * what was returned", both for escrowed items and for money (paid = proceeds + commission).
     */
    @Test
    fun conservationInvariantHolds() {
        val lotId = session.listLot(sellerA, "custom:crimson_star", 2, coppets(30))
        val orderId = session.placeOrder(buyer, "custom:dimensional_wanderer_eye", 1, KNOWN)
        val negA = session.offerOnOrder(sellerA, orderId, coppets(20))
        val negB = session.offerOnOrder(sellerB, orderId, coppets(25))
        // escrow came in: lotId, negA, negB
        val lotSale = session.buyLot(buyer, lotId, 2, 0)
        val acceptA = session.accept(buyer, negA, 1, 0)
        val closeRefunds = session.close()

        // Items: each of the 3 escrows has exactly one way out
        val settled = setOf(lotSale.escrowRef, acceptA.settlement.escrowRef)
        val refunded = (acceptA.releasedEscrows + closeRefunds).map { it.escrowRef }.toSet()
        assertEquals(setOf(lotId, negA), settled)
        assertEquals(setOf(negB), refunded)

        // Money: what the buyer paid = the sellers' proceeds + the commission
        for (s: Settlement in listOf(lotSale, acceptA.settlement)) {
            assertEquals(s.price.money.coppets, s.sellerProceeds.coppets + s.commissionPaid.coppets)
            assertEquals(s.price.money.coppets, s.payerCharge.paidCoppets)
        }
    }

    @Test
    fun barterOfferChargesNoCommissionAndEchoesDemand() {
        val demand = Consideration.ItemDemand("characteristic:Door:7", 1)
        val orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN)
        val negId = session.offerOnOrder(sellerA, orderId, Consideration.of(demand, PoundMoney.ofCoppets(0)))
        val s = session.accept(buyer, negId, 0, 0).settlement // a pure barter needs no money
        assertTrue(s.price.isBarter)
        assertEquals(demand, s.price.item)
        assertEquals(0, s.commissionPaid.coppets)
        assertEquals(0, s.sellerProceeds.coppets)
        assertEquals(0, s.payerCharge.paidCoppets)
    }

    @Test
    fun barterWithBootMovesBootWithoutCommission() {
        val demand = Consideration.ItemDemand("custom:crimson_star", 2)
        val lotId = session.listLot(
            sellerA, "custom:dimensional_wanderer_eye", 1,
            Consideration.of(demand, PoundMoney.ofCoppets(40)),
        )
        val s = session.buyLot(buyer, lotId, 2, 0) // 2 pounds = 40 coppets of boot
        assertTrue(s.price.isBarter)
        assertEquals(0, s.commissionPaid.coppets) // barter: no commission even on the boot
        assertEquals(40, s.sellerProceeds.coppets) // the whole boot goes to the seller
        assertEquals(40, s.payerCharge.paidCoppets)
    }

    private companion object {
        val KNOWN = setOf("custom:dimensional_wanderer_eye", "custom:crimson_star")
    }
}
