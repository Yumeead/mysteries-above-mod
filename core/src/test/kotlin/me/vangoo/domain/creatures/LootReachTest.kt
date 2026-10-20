package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LootReachTest {

    private fun reach(opener: Int) = (9 downTo 0).filter { LootReach.allows(opener, it) }

    @Test
    fun aNonBeyonderFindsSequenceNineAlone() {
        assertEquals(listOf(9), reach(LootReach.NOT_A_BEYONDER))
    }

    @Test
    fun aBeyonderFindsTheNextStepAndTwoBelow() {
        assertEquals(listOf(9, 8), reach(9))
        assertEquals(listOf(9, 8, 7, 6), reach(7))
        assertEquals(listOf(7, 6, 5, 4), reach(5))
    }
}
