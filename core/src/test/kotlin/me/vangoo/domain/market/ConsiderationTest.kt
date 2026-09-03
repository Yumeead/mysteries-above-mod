package me.vangoo.domain.market

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ConsiderationTest {

    private val demand = Consideration.ItemDemand("characteristic:Door:7", 1)

    @Test
    fun moneyOnlyIsNotBarterAndPaysCommission() {
        val c = Consideration.money(PoundMoney.ofCoppets(20))
        assertFalse(c.isBarter)
        assertNull(c.item)
        assertTrue(c.hasMoney)
        assertEquals(2, c.commission(0.10).coppets) // ceil(20 * 0.10)
    }

    @Test
    fun pureItemIsBarterAndChargesNoCommission() {
        val c = Consideration.of(demand, PoundMoney.ofCoppets(0))
        assertTrue(c.isBarter)
        assertEquals(demand, c.item)
        assertFalse(c.hasMoney)
        assertEquals(0, c.commission(0.10).coppets) // a barter never pays commission
    }

    @Test
    fun itemWithBootIsBarterAndStillChargesNoCommission() {
        val c = Consideration.of(demand, PoundMoney.ofCoppets(60))
        assertTrue(c.isBarter)
        assertTrue(c.hasMoney)
        assertEquals(60, c.money.coppets)
        assertEquals(0, c.commission(0.10).coppets)
    }

    @Test
    fun emptyConsiderationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Consideration.money(PoundMoney.ofCoppets(0))
        }
        assertThrows(IllegalArgumentException::class.java) { Consideration.ItemDemand("x", 0) }
        assertThrows(IllegalArgumentException::class.java) { Consideration.ItemDemand("", 1) }
    }
}
