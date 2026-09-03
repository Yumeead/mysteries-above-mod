package me.vangoo.domain.entities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.abilities.TestAbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Sequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BeyonderProgressionTest {

    private val seq9 = TestAbilitySpec.of("sailor")
    private val seq8 = TestAbilitySpec.of("sailor", cost = 50) // the same identity, stronger
    private val seq8Extra = TestAbilitySpec.of("cutthroat")

    private fun tyrant(roster: Map<Int, List<AbilitySpec>>) = Pathway(
        name = "Tyrant",
        group = PathwayGroup.GodAlmighty,
        sequenceNames = (0..9).map { "S$it" },
        roster = roster,
    )

    private fun beyonder(
        pathway: Pathway = tyrant(mapOf(9 to listOf(seq9), 8 to listOf(seq8, seq8Extra))),
        sequence: Sequence = Sequence.starter(),
    ) = Beyonder(UUID.randomUUID(), sequence, pathway)

    @Test
    fun `a fresh beyonder holds only the abilities of its own sequence`() {
        val b = beyonder()
        assertEquals(listOf("sailor"), b.abilities.map { it.identity.id })
        assertEquals(0, b.abilities[0].spiritualityCost)
    }

    @Test
    fun `advancing needs full mastery`() {
        val b = beyonder()
        assertFalse(b.canAdvance())
        assertThrows(IllegalStateException::class.java) { b.advance() }
    }

    @Test
    fun `advancing upgrades in place and adds what is new`() {
        val b = beyonder()
        b.setMastery(Mastery.max())

        b.advance()

        assertEquals(8, b.sequenceLevel)
        assertEquals(listOf("sailor", "cutthroat"), b.abilities.map { it.identity.id })
        assertEquals(50, b.abilities[0].spiritualityCost, "сильніша версія має витіснити слабшу")
        assertEquals(0.0, b.mastery.value, "засвоєння скидається після піднесення")
    }

    @Test
    fun `advancing raises the spirituality ceiling`() {
        val b = beyonder()
        val before = b.maxSpirituality
        b.setMastery(Mastery.max())

        b.advance()

        assertTrue(b.maxSpirituality > before)
    }

    @Test
    fun `demotion drops back to the weaker version of the same ability`() {
        val b = beyonder(sequence = Sequence.of(8))
        assertEquals(50, b.abilityBy(AbilityIdentity.of("sailor"))!!.spiritualityCost)

        b.demote()

        assertEquals(9, b.sequenceLevel)
        assertEquals(listOf("sailor"), b.abilities.map { it.identity.id })
        assertEquals(0, b.abilities[0].spiritualityCost)
    }

    @Test
    fun `demotion at sequence nine is a no-op`() {
        val b = beyonder()
        b.demote()
        assertEquals(9, b.sequenceLevel)
    }

    @Test
    fun `a potion of the next sequence in the same group is drinkable`() {
        val b = beyonder()
        b.setMastery(Mastery.max())

        assertTrue(b.canConsumePotion(b.pathway, Sequence.of(8)))
        assertFalse(b.canConsumePotion(b.pathway, Sequence.of(7)), "через сходинку не перестрибнути")
    }

    @Test
    fun `a potion from another group is never drinkable`() {
        val b = beyonder()
        b.setMastery(Mastery.max())
        val foreign = Pathway("Fool", PathwayGroup.LordOfMysteries, (0..9).map { "S$it" })

        assertFalse(b.canConsumePotion(foreign, Sequence.of(8)))
    }

    @Test
    fun `changing pathway is allowed only from sequence nine`() {
        val other = tyrant(mapOf(9 to listOf(TestAbilitySpec.of("other"))))

        val tooStrong = beyonder(sequence = Sequence.of(8))
        tooStrong.setMastery(Mastery.max())
        assertThrows(IllegalStateException::class.java) { tooStrong.advanceWithPathwayChange(other) }

        val sailor = beyonder()
        sailor.setMastery(Mastery.max())
        sailor.advanceWithPathwayChange(other)
        assertEquals(8, sailor.sequenceLevel)
        assertEquals(listOf("other"), sailor.abilities.map { it.identity.id })
    }

    @Test
    fun `changing to a pathway of another group throws`() {
        val b = beyonder()
        b.setMastery(Mastery.max())
        val foreign = Pathway("Fool", PathwayGroup.LordOfMysteries, (0..9).map { "S$it" })

        assertThrows(IllegalArgumentException::class.java) { b.advanceWithPathwayChange(foreign) }
    }

    @Test
    fun `the strongest resistance passive wins, and several do not stack into immunity`() {
        val b = beyonder(tyrant(emptyMap()))
        b.grantOffPathway(
            TestAbilitySpec.of("calm", type = AbilityType.PERMANENT_PASSIVE, sanityLossMultiplier = 0.5),
        )
        b.grantOffPathway(
            TestAbilitySpec.of("stoic", type = AbilityType.PERMANENT_PASSIVE, sanityLossMultiplier = 0.75),
        )

        b.increaseSanityLoss(10)

        assertEquals(5, b.sanityLoss.scale, "0.5 * 10, а не 0.5 * 0.75 * 10")
    }

    @Test
    fun `resistance never rounds an incoming loss down to nothing`() {
        val b = beyonder(tyrant(emptyMap()))
        b.grantOffPathway(
            TestAbilitySpec.of("void", type = AbilityType.PERMANENT_PASSIVE, sanityLossMultiplier = 0.01),
        )

        b.increaseSanityLoss(1)

        assertEquals(1, b.sanityLoss.scale)
    }

    @Test
    fun `removing an ability takes it out of both lists`() {
        val b = beyonder()
        b.grantOffPathway(TestAbilitySpec.of("stolen"))

        assertTrue(b.remove(AbilityIdentity.of("stolen")))
        assertTrue(b.remove(AbilityIdentity.of("sailor")))
        assertFalse(b.remove(AbilityIdentity.of("sailor")))
        assertTrue(b.abilities.isEmpty())
    }

    @Test
    fun `possession swaps identity and restoring puts it back`() {
        val b = beyonder()
        b.increaseSanityLoss(7)
        val snapshot = b.snapshot()
        val puppet = tyrant(mapOf(9 to listOf(TestAbilitySpec.of("puppet"))))

        b.possess(puppet, Sequence.of(5))
        assertEquals(5, b.sequenceLevel)
        assertEquals(listOf("puppet"), b.abilities.map { it.identity.id })

        b.restoreFrom(snapshot)
        assertEquals(9, b.sequenceLevel)
        assertEquals("Tyrant", b.pathway.name)
        assertEquals(listOf("sailor"), b.abilities.map { it.identity.id })
        assertEquals(7, b.sanityLoss.scale)
    }

    @Test
    fun `restore clamps stored spirituality to the recomputed ceiling`() {
        val pathway = tyrant(emptyMap())
        val restored = Beyonder.restore(
            playerId = UUID.randomUUID(),
            pathway = pathway,
            sequence = Sequence.starter(),
            mastery = Mastery.of(50.0),
            currentSpirituality = 999_999,
            sanityLoss = SanityLoss.of(12),
        )

        assertEquals(restored.maxSpirituality, restored.spirituality.current)
        assertEquals(12, restored.sanityLoss.scale)
        assertEquals(50.0, restored.mastery.value)
    }

    @Test
    fun `sleep tops the tank to half and calms the mind`() {
        val b = beyonder()
        b.increaseSanityLoss(20)

        b.restoreAfterSleep()

        assertEquals(15, b.sanityLoss.scale)
        assertEquals(b.maxSpirituality / 2, b.spirituality.current)
    }
}
