package me.vangoo.domain.entities

import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Upkeep of a power that lasts in time.
 *
 * The plugin had no such operation: each of its 22 sessions fetched the Beyonder, checked
 * `getSpirituality().current() < cost` and called `decrement` itself. Here it is one rule, and
 * the tests guard the main thing about it — **either it is spent in full or nothing is spent
 * at all**. A session able to run on credit would run forever.
 */
class BeyonderUpkeepTest {

    private fun beyonder(current: Int): Beyonder {
        val pathway = Pathway(
            name = "Tyrant",
            group = PathwayGroup.GodAlmighty,
            sequenceNames = (0..9).map { "S$it" },
            roster = emptyMap(),
        )
        return Beyonder(UUID.randomUUID(), Sequence.starter(), pathway).apply {
            setSpirituality(Spirituality.of(current, maxSpirituality))
        }
    }

    @Test
    fun `upkeep debits exactly the asked amount`() {
        val b = beyonder(50)

        assertTrue(b.payUpkeep(20))

        assertEquals(30, b.spirituality.current)
    }

    @Test
    fun `upkeep the beyonder cannot afford takes nothing at all`() {
        val b = beyonder(10)

        assertFalse(b.payUpkeep(11))

        assertEquals(10, b.spirituality.current, "часткове списання лишило б сесію в борг")
    }

    @Test
    fun `upkeep may spend the last point`() {
        val b = beyonder(10)

        assertTrue(b.payUpkeep(10))

        assertEquals(0, b.spirituality.current)
    }

    @Test
    fun `free upkeep always succeeds`() {
        val b = beyonder(0)

        assertTrue(b.payUpkeep(0))

        assertEquals(0, b.spirituality.current)
    }

    @Test
    fun `upkeep does not grow mastery — only a real cast does`() {
        val b = beyonder(50)
        val before = b.mastery

        b.payUpkeep(20)

        assertEquals(before, b.mastery)
    }

    @Test
    fun `negative upkeep is a bug, not a refund`() {
        val b = beyonder(50)

        assertThrows(IllegalArgumentException::class.java) { b.payUpkeep(-5) }
    }
}
