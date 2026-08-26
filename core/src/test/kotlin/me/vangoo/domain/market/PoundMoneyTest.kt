package me.vangoo.domain.market

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PoundMoneyTest {

    // --- PoundMoney ---

    @Test
    fun convertsPoundsAndCoppets() {
        val m = PoundMoney.of(2, 15)
        assertEquals(55, m.coppets)
        assertEquals(2, m.wholePounds)
        assertEquals(15, m.remainderCoppets)
    }

    @Test
    fun rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException::class.java) { PoundMoney.ofCoppets(-1) }
        assertThrows(IllegalArgumentException::class.java) { PoundMoney.of(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { PoundMoney.of(0, -5) }
        assertThrows(IllegalArgumentException::class.java) { PoundMoney.of(1, 0) - PoundMoney.of(2, 0) }
    }

    @Test
    fun formatsUkrainian() {
        assertEquals("2 ф 15 к", PoundMoney.of(2, 15).format())
        assertEquals("2 ф", PoundMoney.of(2, 0).format())
        assertEquals("15 к", PoundMoney.of(0, 15).format())
        assertEquals("0 к", PoundMoney.ofCoppets(0).format())
    }

    @Test
    fun commissionIsCeiled() {
        // 10% від 55 к = 5.5 → 6 к
        assertEquals(6, PoundMoney.ofCoppets(55).commission(0.10).coppets)
        assertEquals(0, PoundMoney.ofCoppets(0).commission(0.10).coppets)
    }

    @Test
    fun plusAddsCoppets() {
        assertEquals(PoundMoney.of(3, 0), PoundMoney.of(1, 15) + PoundMoney.of(1, 5))
    }

    @Test
    fun timesScalesCoppets() {
        assertEquals(60, (PoundMoney.ofCoppets(20) * 3).coppets)
        assertEquals(3, (PoundMoney.ofCoppets(20) * 3).wholePounds)
    }

    @Test
    fun timesZeroIsZeroMoney() {
        assertTrue((PoundMoney.ofCoppets(20) * 0).isZero)
    }

    @Test
    fun timesRejectsNegativeFactor() {
        assertThrows(IllegalArgumentException::class.java) { PoundMoney.ofCoppets(20) * -1 }
    }

    // --- CoinChange (жадібний розмін: спершу коппети, потім фунти) ---

    @Test
    fun paysExactlyWithCoppetsFirst() {
        // ціна 30 к; у гаманці 2 ф + 12 к → зняти 12 к + 1 ф, здача 2 к
        val change = CoinChange.make(2, 12, PoundMoney.ofCoppets(30))!!
        assertEquals(1, change.takePounds)
        assertEquals(12, change.takeCoppets)
        assertEquals(2, change.changeCoppets)
        assertEquals(30, change.paidCoppets)
    }

    @Test
    fun paysWithoutChangeWhenCoppetsSuffice() {
        val change = CoinChange.make(5, 30, PoundMoney.ofCoppets(30))!!
        assertEquals(0, change.takePounds)
        assertEquals(30, change.takeCoppets)
        assertEquals(0, change.changeCoppets)
    }

    @Test
    fun poundsOnlyWalletGetsChange() {
        // ціна 30 к; лише 2 фунти → зняти 2 ф (40 к), здача 10 к
        val change = CoinChange.make(2, 0, PoundMoney.ofCoppets(30))!!
        assertEquals(2, change.takePounds)
        assertEquals(0, change.takeCoppets)
        assertEquals(10, change.changeCoppets)
        assertEquals(30, change.paidCoppets)
    }

    @Test
    fun insufficientFundsIsNull() {
        assertNull(CoinChange.make(1, 9, PoundMoney.ofCoppets(30)))
        assertNull(CoinChange.make(0, 0, PoundMoney.ofCoppets(1)))
    }

    @Test
    fun negativeWalletIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CoinChange.make(-1, 0, PoundMoney.ofCoppets(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoinChange.make(0, -1, PoundMoney.ofCoppets(1))
        }
    }

    @Test
    fun zeroPriceTakesNothing() {
        val change = CoinChange.make(3, 3, PoundMoney.ofCoppets(0))!!
        assertEquals(0, change.takePounds)
        assertEquals(0, change.takeCoppets)
        assertEquals(0, change.changeCoppets)
    }
}
