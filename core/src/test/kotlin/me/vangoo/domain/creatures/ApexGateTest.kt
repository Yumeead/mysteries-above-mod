package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ApexGateTest {

    @Test
    fun commonCreaturesReachEveryone() {
        assertTrue(ApexGate.allows(CreatureTier.COMMON, null))
        assertTrue(ApexGate.allows(CreatureTier.COMMON, 9))
    }

    @Test
    fun apexNeverReachesNonBeyonders() {
        assertFalse(ApexGate.allows(CreatureTier.APEX, null))
    }

    @Test
    fun apexOnlyReachesPlayersNearItsOwnSequence() {
        for (seq in 9 downTo ApexGate.APEX_MIN_SEQUENCE + 1) {
            assertFalse(ApexGate.allows(CreatureTier.APEX, seq), "seq $seq must not meet apex")
        }
        for (seq in ApexGate.APEX_MIN_SEQUENCE downTo 0) {
            assertTrue(ApexGate.allows(CreatureTier.APEX, seq), "seq $seq must meet apex")
        }
    }
}
