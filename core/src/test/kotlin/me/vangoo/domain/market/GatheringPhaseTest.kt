package me.vangoo.domain.market

import me.vangoo.domain.market.GatheringPhase.ANNOUNCED
import me.vangoo.domain.market.GatheringPhase.CLOSING
import me.vangoo.domain.market.GatheringPhase.IDLE
import me.vangoo.domain.market.GatheringPhase.OPEN
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GatheringPhaseTest {

    @Test
    fun allowsHappyPathCycle() {
        assertTrue(IDLE.canTransitionTo(ANNOUNCED))
        assertTrue(ANNOUNCED.canTransitionTo(OPEN))
        assertTrue(OPEN.canTransitionTo(CLOSING))
        assertTrue(CLOSING.canTransitionTo(IDLE))
    }

    @Test
    fun allowsCancellingAnnouncementWhenNobodyJoined() {
        assertTrue(ANNOUNCED.canTransitionTo(IDLE))
    }

    @Test
    fun rejectsSkippingPhases() {
        assertFalse(IDLE.canTransitionTo(OPEN))
        assertFalse(IDLE.canTransitionTo(CLOSING))
        assertFalse(ANNOUNCED.canTransitionTo(CLOSING))
        assertFalse(OPEN.canTransitionTo(IDLE))
        assertFalse(OPEN.canTransitionTo(ANNOUNCED))
        assertFalse(CLOSING.canTransitionTo(OPEN))
    }

    @Test
    fun noPhaseTransitionsToItself() {
        for (phase in GatheringPhase.entries) {
            assertFalse(phase.canTransitionTo(phase), "$phase → $phase")
        }
    }
}
