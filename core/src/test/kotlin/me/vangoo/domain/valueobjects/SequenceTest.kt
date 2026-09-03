package me.vangoo.domain.valueobjects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SequenceTest {

    @Test
    fun demoteWeakensByOneLevel() {
        assertEquals(7, Sequence.of(6).demote().level)
    }

    @Test
    fun cannotDemotePastWeakestSequence() {
        val weakest = Sequence.of(9)
        assertFalse(weakest.canDemote())
        // Kotlin's check() throws IllegalStateException — the same type as in the Java version.
        assertThrows(IllegalStateException::class.java) { weakest.demote() }
    }

    @Test
    fun canDemoteFromAnyNonWeakestLevel() {
        assertTrue(Sequence.of(0).canDemote())
        assertTrue(Sequence.of(8).canDemote())
    }
}
