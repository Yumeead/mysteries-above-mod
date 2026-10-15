package me.vangoo.mysteries.client.beyonder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AbilityCycleTest {

    private val ids = listOf("a", "b", "c")

    @Test
    fun stepsAndWrapsBothWays() {
        assertEquals("b", ClientAbilityState.cycle(ids, "a", 1))
        assertEquals("a", ClientAbilityState.cycle(ids, "c", 1))
        assertEquals("c", ClientAbilityState.cycle(ids, "a", -1))
        assertEquals("c", ClientAbilityState.cycle(ids, "a", 5))
    }

    @Test
    fun withoutSelectionStartsFromTheNearEnd() {
        assertEquals("a", ClientAbilityState.cycle(ids, null, 1))
        assertEquals("c", ClientAbilityState.cycle(ids, "gone", -1))
    }

    @Test
    fun emptyListHasNothing() {
        assertNull(ClientAbilityState.cycle(emptyList(), "a", 1))
    }
}
