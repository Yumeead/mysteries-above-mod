package me.vangoo.domain.rituals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RitualEffectMathTest {

    @Test
    fun baseConstantsHaveExpectedValues() {
        assertEquals(6000, RitualEffectMath.LUCK_BASE_TICKS)
        assertEquals(50, RitualEffectMath.SANCTIFY_BASE_DURABILITY)
        assertEquals(1800, RitualEffectMath.EVENTS_BASE_WINDOW_SECONDS)
        assertEquals(600, RitualEffectMath.WALL_BASE_TICKS)
    }

    @Test
    fun bestowmentChanceIsNonDecreasingAsSequenceDrops() {
        val seq9 = RitualEffectMath.bestowmentChance(9)
        val seq5 = RitualEffectMath.bestowmentChance(5)
        val seq0 = RitualEffectMath.bestowmentChance(0)
        assertTrue(seq5 >= seq9)
        assertTrue(seq0 >= seq5)
    }

    @Test
    fun bestowmentChanceIsCappedAtNinetyPercent() {
        assertEquals(0.9, RitualEffectMath.bestowmentChance(0), 0.0001)
        assertEquals(0.9, RitualEffectMath.bestowmentChance(1), 0.0001)
    }

    @Test
    fun bestowmentChanceAtSequenceNineIsHalf() {
        assertEquals(0.5, RitualEffectMath.bestowmentChance(9), 0.0001)
    }
}
