package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ConvergencePullTest {

    private val pull = ConvergencePull()

    private val a: UUID = UUID(0, 1)
    private val b: UUID = UUID(0, 2)

    @Test
    fun samePathwayBeatsNeighborAtEqualDistance() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val same = ResonantBeyonder(a, "Fool", "LordOfMysteries", 9, 10.0, 0.0)
        val neighbor = ResonantBeyonder(b, "Door", "LordOfMysteries", 9, 0.0, 10.0)
        val res = pull.computePull(src, listOf(same, neighbor), 128.0)
        assertNotNull(res)
        assertEquals(a, res!!.targetId)
    }

    @Test
    fun nextNeededSequenceGivesStrongerStrengthThanOther() {
        // A Beyonder at seq. 8 needs seq. 7 next.
        val needed = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val other = ConvergenceSource("Fool", "LordOfMysteries", 3, 0.0, 0.0)
        val beyonder = ResonantBeyonder(a, "Fool", "LordOfMysteries", 8, 5.0, 0.0)
        val sNeeded = pull.computePull(needed, listOf(beyonder), 128.0)!!.strength
        val sOther = pull.computePull(other, listOf(beyonder), 128.0)!!.strength
        assertTrue(sNeeded > sOther)
    }

    @Test
    fun outOfRadiusExcluded() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val far = ResonantBeyonder(a, "Fool", "LordOfMysteries", 9, 200.0, 0.0)
        assertNull(pull.computePull(src, listOf(far), 128.0))
    }

    @Test
    fun neighborResonatesForeignGroupDoesNot() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val neighbor = ResonantBeyonder(a, "Error", "LordOfMysteries", 9, 5.0, 0.0)
        val foreign = ResonantBeyonder(b, "Justiciar", "TheAnarchy", 9, 1.0, 0.0)
        val res = pull.computePull(src, listOf(neighbor, foreign), 128.0)
        assertNotNull(res)
        assertEquals(a, res!!.targetId)
    }

    @Test
    fun nearestWinsAmongEqualResonance() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 9, 0.0, 0.0)
        val near = ResonantBeyonder(a, "Fool", "LordOfMysteries", 9, 3.0, 0.0)
        val far = ResonantBeyonder(b, "Fool", "LordOfMysteries", 9, 20.0, 0.0)
        assertEquals(a, pull.computePull(src, listOf(far, near), 128.0)!!.targetId)
    }

    @Test
    fun maxStrengthIsOneForSamePathwayNextNeeded() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val beyonder = ResonantBeyonder(a, "Fool", "LordOfMysteries", 8, 5.0, 0.0)
        val strength = pull.computePull(src, listOf(beyonder), 128.0)!!.strength
        assertEquals(1.0, strength, 1e-9)
        assertTrue(strength > 0.0 && strength <= 1.0)
    }

    @Test
    fun emptyCandidatesReturnsNull() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        assertNull(pull.computePull(src, emptyList(), 128.0))
        assertNull(pull.computePull(src, null, 128.0))
        assertNull(pull.computePull(null, listOf(ResonantBeyonder(a, "Fool", null, 8, 0.0, 0.0)), 128.0))
    }

    @Test
    fun lowerUuidWinsWhenWeightAndDistanceTie() {
        val src = ConvergenceSource("Fool", "LordOfMysteries", 7, 0.0, 0.0)
        val first = ResonantBeyonder(b, "Fool", "LordOfMysteries", 9, 5.0, 0.0)
        val second = ResonantBeyonder(a, "Fool", "LordOfMysteries", 9, -5.0, 0.0)
        assertEquals(a, pull.computePull(src, listOf(first, second), 128.0)!!.targetId)
    }
}
