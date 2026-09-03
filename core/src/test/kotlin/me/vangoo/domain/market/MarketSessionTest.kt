package me.vangoo.domain.market

import me.vangoo.domain.market.MarketSession.MarketException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.random.Random

internal class MarketSessionTest {

    private lateinit var session: MarketSession
    private val seller: UUID = UUID.randomUUID()
    private val buyer: UUID = UUID.randomUUID()
    private val stranger: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        session = MarketSession(RATE, Random(42))
        session.registerParticipant(seller)
        session.registerParticipant(buyer)
    }

    @Test
    fun commissionRateMustBeAFraction() {
        assertThrows(IllegalArgumentException::class.java) { MarketSession(-0.01, Random(1)) }
        assertThrows(IllegalArgumentException::class.java) { MarketSession(1.0, Random(1)) }
    }

    @Test
    fun aliasesAreUniqueAndStable() {
        val a = session.registerParticipant(seller)
        val b = session.registerParticipant(buyer)
        assertNotEquals(a, b)
        assertEquals(a, session.registerParticipant(seller)) // idempotent
        assertEquals("Незнайомець №$a", session.aliasOf(seller))
    }

    @Test
    fun nonParticipantHasNoAlias() {
        assertThrows(MarketException::class.java) { session.aliasOf(stranger) }
        assertFalse(session.isParticipant(stranger))
        assertTrue(session.isParticipant(seller))
    }

    @Test
    fun registeringMoreThanNinetyNineParticipantsTerminatesWithDistinctAliases() {
        // More than 99 participants exhausts the 1..99 range — the randomisation loop must
        // widen the bounds, otherwise it hangs forever on the main thread.
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val assigned = mutableSetOf<Int>()
            assigned.add(session.registerParticipant(seller))
            assigned.add(session.registerParticipant(buyer))
            repeat(120) {
                val alias = session.registerParticipant(UUID.randomUUID())
                assertTrue(assigned.add(alias), "Псевдонім №$alias видано повторно")
            }
            assertEquals(122, assigned.size)
        }
    }

    @Test
    fun listedLotIsVisibleAndAnonymousViewCarriesData() {
        val lotId = session.listLot(
            seller, "custom:stellar_aqua_crystal", 2,
            Consideration.money(PoundMoney.of(1, 5)),
        )
        val lot = session.activeLots().first { it.lotId == lotId }
        assertEquals("custom:stellar_aqua_crystal", lot.itemKey)
        assertEquals(2, lot.amount)
        assertEquals(PoundMoney.of(1, 5), lot.price.money)
        assertFalse(lot.sold)
    }

    @Test
    fun rejectsInvalidListings() {
        assertThrows(MarketException::class.java) {
            session.listLot(seller, "custom:x", 0, Consideration.money(PoundMoney.ofCoppets(5)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(0)))
        }
        assertThrows(MarketException::class.java) {
            session.listLot(stranger, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(5)))
        }
    }

    @Test
    fun buyLotSettlesWithCommission() {
        val lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(30)))
        // buyer: 2 pounds, 0 coppets
        val s = session.buyLot(buyer, lotId, 2, 0)
        assertEquals(buyer, s.payerId)
        assertEquals(seller, s.payeeId)
        assertEquals(30, s.price.money.coppets)
        assertEquals(3, s.commissionPaid.coppets) // ceil(30×0.10)
        assertEquals(27, s.sellerProceeds.coppets) // 30 − 3
        assertEquals(30, s.payerCharge.paidCoppets)
        assertEquals(lotId, s.escrowRef)
        assertTrue(session.activeLots().isEmpty()) // the lot is gone from the list
    }

    @Test
    fun doubleBuyIsImpossible() {
        val lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(10)))
        session.buyLot(buyer, lotId, 1, 0)
        assertThrows(MarketException::class.java) { session.buyLot(buyer, lotId, 1, 0) }
    }

    @Test
    fun cannotBuyOwnLotOrWithoutFunds() {
        val lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(30)))
        assertThrows(MarketException::class.java) { session.buyLot(seller, lotId, 5, 5) }
        assertThrows(MarketException::class.java) { session.buyLot(buyer, lotId, 1, 9) } // 29 c < 30 c
        assertEquals(1, session.activeLots().size) // failed attempts do not remove the lot
    }

    @Test
    fun nonParticipantCannotBuy() {
        val lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(10)))
        assertThrows(MarketException::class.java) { session.buyLot(stranger, lotId, 5, 5) }
    }

    private companion object {
        const val RATE = 0.10
    }
}
