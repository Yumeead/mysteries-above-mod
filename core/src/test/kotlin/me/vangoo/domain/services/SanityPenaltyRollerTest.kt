package me.vangoo.domain.services

import me.vangoo.domain.ScriptedRandom
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.SanityPenalty.PenaltyType
import me.vangoo.domain.valueobjects.Sequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SanityPenaltyRollerTest {

    private val seq9 = Sequence.of(9)
    private val maxSpirituality = 1000

    private fun roll(
        scale: Int,
        doubles: List<Double> = emptyList(),
        booleans: List<Boolean> = emptyList(),
        sequence: Sequence = seq9,
    ) = SanityPenaltyRoller.roll(
        SanityLoss.of(scale),
        sequence,
        maxSpirituality,
        ScriptedRandom(doubles, booleans),
    )

    @Test
    fun `negligible loss never rolls at all`() {
        // The script is empty: if the roller gets this far, the test fails on an exhausted roll.
        assertFalse(roll(scale = 10).hasEffect())
    }

    @Test
    fun `a passed check yields no penalty`() {
        // 11-20 -> failure chance 5%; a roll of 0.99 is above it.
        assertFalse(roll(scale = 15, doubles = listOf(0.99)).hasEffect())
    }

    @Test
    fun `minor severity has no penalty even after a failed check`() {
        assertFalse(roll(scale = 15, doubles = listOf(0.0)).hasEffect())
    }

    @Test
    fun `moderate severity scratches on the inner roll`() {
        val hit = roll(scale = 30, doubles = listOf(0.0, 0.1))
        assertEquals(PenaltyType.DAMAGE, hit.type)
        assertEquals(1, hit.amount)

        val miss = roll(scale = 30, doubles = listOf(0.0, 0.9))
        assertFalse(miss.hasEffect())
    }

    @Test
    fun `serious severity is mostly halved damage`() {
        val damage = roll(scale = 50, doubles = listOf(0.0, 0.5))
        assertEquals(PenaltyType.DAMAGE, damage.type)
        assertEquals(SequenceScaler.scaleDamagePenalty(9) / 2, damage.amount)

        val loss = roll(scale = 50, doubles = listOf(0.0, 0.8))
        assertEquals(PenaltyType.SPIRITUALITY_LOSS, loss.type)
    }

    @Test
    fun `severe severity spends two thirds of the full spirituality loss`() {
        val loss = roll(scale = 70, doubles = listOf(0.0), booleans = listOf(true))
        assertEquals(PenaltyType.SPIRITUALITY_LOSS, loss.type)
        assertEquals(SequenceScaler.scaleSpiritualityLoss(maxSpirituality, 9) * 2 / 3, loss.amount)

        val damage = roll(scale = 70, doubles = listOf(0.0), booleans = listOf(false))
        assertEquals(PenaltyType.DAMAGE, damage.type)
    }

    @Test
    fun `critical severity spends the full spirituality loss`() {
        val loss = roll(scale = 90, doubles = listOf(0.0), booleans = listOf(true))
        assertEquals(SequenceScaler.scaleSpiritualityLoss(maxSpirituality, 9), loss.amount)
    }

    @Test
    fun `extreme severity always fires and always kills`() {
        // The failure chance at 96+ equals 1.0, so even the highest possible roll is below it.
        val penalty = roll(scale = 100, doubles = listOf(0.999999))
        assertEquals(PenaltyType.EXTREME, penalty.type)
        assertTrue(penalty.hasEffect())
    }

    @Test
    fun `higher sequences hurt harder`() {
        val weak = roll(scale = 90, doubles = listOf(0.0), booleans = listOf(false), sequence = Sequence.of(9))
        val strong = roll(scale = 90, doubles = listOf(0.0), booleans = listOf(false), sequence = Sequence.of(1))
        assertTrue(strong.amount > weak.amount)
    }
}
