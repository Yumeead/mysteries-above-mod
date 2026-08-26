package me.vangoo.domain.entities

import me.vangoo.domain.abilities.TestAbilitySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathwayTest {

    private val ten = (0..9).map { it.toString() }

    private fun pathway(
        names: List<String> = ten,
        roster: Map<Int, List<me.vangoo.domain.abilities.AbilitySpec>> = emptyMap(),
    ) = Pathway("Stub", PathwayGroup.LordOfMysteries, names, roster)

    @Test
    fun `ten sequence names are indexed by sequence level`() {
        val p = pathway()
        assertEquals("9", p.sequenceName(9))
        assertEquals("0", p.sequenceName(0))
    }

    @Test
    fun `fewer than ten sequence names throws`() {
        assertThrows(IllegalArgumentException::class.java) { pathway(names = (0..8).map { "$it" }) }
    }

    @Test
    fun `more than ten sequence names throws`() {
        assertThrows(IllegalArgumentException::class.java) { pathway(names = (0..10).map { "$it" }) }
    }

    @Test
    fun `blank name throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Pathway("  ", PathwayGroup.LordOfMysteries, ten)
        }
    }

    @Test
    fun `abilities on a sequence outside 0-9 throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            pathway(roster = mapOf(10 to listOf(TestAbilitySpec.of("x"))))
        }
    }

    @Test
    fun `a pathway without abilities is a stub`() {
        assertFalse(pathway().hasAnyAbility())
        assertTrue(pathway().abilitiesFor(9).isEmpty())
    }

    @Test
    fun `a single registered ability makes the pathway real`() {
        val p = pathway(roster = mapOf(9 to listOf(TestAbilitySpec.of("x"))))
        assertTrue(p.hasAnyAbility())
        assertEquals(1, p.abilitiesFor(9).size)
    }

    @Test
    fun `sequence name out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) { pathway().sequenceName(10) }
    }
}
