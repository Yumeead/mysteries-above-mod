package me.vangoo.mysteries.client.hud

import me.vangoo.domain.hud.SpiritualityDisplay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpiritualityDisplayTest {
    @Test
    fun gainsCountThroughEveryPointAtTheReceivedRate() {
        for (gain in listOf(1, 6, 40, 150)) {
            val display = SpiritualityDisplay()
            display.tick(100, 1000)
            display.tick(100 + gain, 1000)
            val seen = mutableSetOf(100)
            var previous = 100
            repeat(20) {
                repeat(20) { frame ->
                    val current = display.value(frame / 20f).toInt()
                    assertTrue(current in previous..previous + 1, "gain $gain: $previous -> $current")
                    seen += current
                    previous = current
                }
                display.tick(100 + gain, 1000)
            }
            seen += display.value(0f).toInt()
            assertEquals((100..100 + gain).toSet(), seen)
            assertEquals((100 + gain).toFloat(), display.value(1f))
        }
    }

    @Test
    fun spendingFinishesAtTheConfirmedValue() {
        val display = SpiritualityDisplay()
        display.tick(100, 1000)
        display.tick(120, 1000)
        repeat(5) { display.tick(120, 1000) }
        display.tick(80, 1000)
        repeat(12) { display.tick(80, 1000) }
        assertEquals(80f, display.value(0f))
        assertEquals(80f, display.value(1f))
    }

    @Test
    fun newPacketsContinueFromTheCurrentDisplayedValue() {
        val display = SpiritualityDisplay()
        display.tick(100, 1000)
        display.tick(120, 1000)
        repeat(5) { display.tick(120, 1000) }
        val before = display.value(1f)
        display.tick(130, 1000)
        assertEquals(before, display.value(0f))
        repeat(20) { display.tick(130, 1000) }
        assertEquals(130f, display.value(0f))
    }

    @Test
    fun initialStateNewMaximumReconnectAndReducedMotionSnap() {
        val display = SpiritualityDisplay()
        display.tick(50, 100)
        assertEquals(50f, display.value(0f))
        display.tick(60, 100)
        display.tick(150, 300)
        assertEquals(150f, display.value(0f))
        display.reset()
        display.tick(90, 300)
        assertEquals(90f, display.value(0f))
        display.tick(100, 300, instant = true)
        assertEquals(100f, display.value(0f))
    }

    @Test
    fun recoveryStopsExactlyAtTheCap() {
        val display = SpiritualityDisplay()
        display.tick(99, 100)
        display.tick(100, 100)
        repeat(50) {
            assertTrue(display.value(.99f) <= 100f)
            display.tick(100, 100)
        }
        assertEquals(100f, display.value(0f))
    }
}
