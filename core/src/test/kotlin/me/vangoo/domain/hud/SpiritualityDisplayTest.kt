package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpiritualityDisplayTest {
    @Test fun refillStartsAndSettlesGentlyWithoutMovingTheTarget() {
        val display = SpiritualityDisplay()
        display.tick(0, 100)
        display.tick(100, 100)
        val first = display.value(1f)
        assertTrue(first > 0f && first < 5f)
        repeat(10) { display.tick(100, 100) }
        assertEquals(50f, display.value(0f), .001f)
        repeat(10) { display.tick(100, 100) }
        assertEquals(100f, display.value(0f))
    }

    @Test fun spendingFallsSmoothlyAndLeavesAFadingTrail() {
        val display = SpiritualityDisplay()
        display.tick(100, 100)
        display.tick(40, 100)
        assertEquals(100f, display.value(0f))
        assertEquals(1f, display.trailFraction(0f))
        repeat(6) { display.tick(40, 100) }
        assertTrue(display.value(0f) > 40f && display.value(0f) < 100f)
        repeat(6) { display.tick(40, 100) }
        assertEquals(40f, display.value(0f))
        assertTrue(display.trailFraction(0f) > display.fraction(0f))
        repeat(30) { display.tick(40, 100) }
        assertEquals(.4f, display.trailFraction(0f))
    }

    @Test fun recoveryNewMaximumAndReducedMotionNeverOvershoot() {
        val display = SpiritualityDisplay()
        display.tick(10, 100)
        display.tick(60, 100)
        repeat(20) {
            assertTrue(display.value(.5f) in 10f..60f)
            display.tick(60, 100)
        }
        assertEquals(60f, display.value(0f))
        display.tick(100, 200)
        assertEquals(100f, display.value(0f))
        display.tick(20, 200, instant = true)
        assertEquals(20f, display.value(0f))
        assertEquals(.1f, display.trailFraction(0f))
        display.reset()
        display.tick(150, 200)
        assertEquals(150f, display.value(0f))
    }

    @Test fun numberVisibilityAndVisualThresholdsAreIndependentOfGameRules() {
        val display = SpiritualityDisplay()
        display.tick(19, 100)
        assertTrue(display.low)
        assertTrue(display.showNumber(SpiritualityNumbers.ON_CHANGE, false))
        repeat(51) { display.tick(19, 100) }
        assertFalse(display.showNumber(SpiritualityNumbers.ON_CHANGE, false))
        assertTrue(display.showNumber(SpiritualityNumbers.ON_CHANGE, true))
        assertFalse(display.showNumber(SpiritualityNumbers.NEVER, true))
        display.tick(20, 100)
        assertFalse(display.low)
        display.tick(100, 100)
        assertTrue(display.full)
        display.tick(0, 0)
        assertFalse(display.full)
        assertEquals(0f, display.fraction(0f))
    }

    @Test fun normalRegenerationCountsOnePointAtATimeAndNeverPredictsUnconfirmedSpirit() {
        val display = SpiritualityDisplay()
        display.tick(50,100,sequence=5,regenerationPerSecond=6)
        display.tick(56,100,sequence=5,regenerationPerSecond=6)
        var last = 50
        val seen = mutableSetOf(last)
        repeat(21) {
            repeat(3) { frame ->
                val number = display.number(frame / 3f)
                assertTrue(number in last..minOf(last+1,56))
                seen += number
                last = number
            }
            display.tick(56,100,sequence=5,regenerationPerSecond=6)
        }
        assertEquals((50..56).toSet(),seen)
        repeat(80) { display.tick(56,100,sequence=5,regenerationPerSecond=6) }
        assertEquals(56,display.number(1f))
    }

    @Test fun sequenceChangesTheAnimationSpeedInBothDirectionsAndRetargetingNeverJumps() {
        fun display(sequence: Int, rate: Int) = SpiritualityDisplay().also {
            it.tick(50,100,sequence=sequence,regenerationPerSecond=rate)
            it.tick(56,100,sequence=sequence,regenerationPerSecond=rate)
        }
        val slow=display(9,1)
        val fast=display(0,100)
        repeat(3) { slow.tick(56,100,sequence=9,regenerationPerSecond=1);fast.tick(56,100,sequence=0,regenerationPerSecond=100) }
        assertTrue(fast.value(0f)>slow.value(0f))
        slow.tick(0,100,sequence=9);fast.tick(0,100,sequence=0)
        repeat(6) { slow.tick(0,100,sequence=9);fast.tick(0,100,sequence=0) }
        assertTrue(fast.value(0f)<slow.value(0f))
        val edge=slow.value(1f)
        slow.tick(80,100,sequence=9)
        assertEquals(edge,slow.value(0f),.0001f)
        assertTrue(slow.value(.5f)>=edge)
    }
}
