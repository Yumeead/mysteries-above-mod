package me.vangoo.domain.entities

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.abilities.CastAuthorization
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.abilities.TestAbilitySpec
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Життєвий цикл касту. У плагіні його тримав коментар «КРИТИЧНО: НЕ встановлювати
 * кулдаун якщо результат deferred» — тут це квиток, і саме цей файл стереже, що
 * невитрачений квиток нічого не коштує, а витрачений не витрачається вдруге.
 */
class BeyonderCastTest {

    private val spell = TestAbilitySpec.of("spell", cost = 30, cooldown = 20)
    private val passive =
        TestAbilitySpec.of("passive", type = AbilityType.PERMANENT_PASSIVE)

    private fun beyonder(vararg specs: me.vangoo.domain.abilities.AbilitySpec): Beyonder {
        val pathway = Pathway(
            name = "Tyrant",
            group = PathwayGroup.GodAlmighty,
            sequenceNames = (0..9).map { "S$it" },
            roster = mapOf(9 to specs.toList()),
        )
        return Beyonder(UUID.randomUUID(), Sequence.starter(), pathway).apply {
            setSpirituality(Spirituality.full(maxSpirituality))
        }
    }

    private fun ticketFor(b: Beyonder, spec: me.vangoo.domain.abilities.AbilitySpec) =
        assertInstanceOf(CastAuthorization.Granted::class.java, b.authorize(spec)).ticket

    @Test
    fun `authorization alone costs nothing`() {
        val b = beyonder(spell)
        val before = b.spirituality.current

        ticketFor(b, spell)

        assertEquals(before, b.spirituality.current)
    }

    @Test
    fun `a ticket that is never settled is a free cast — this is what deferred means`() {
        val b = beyonder(spell)
        val before = b.spirituality.current

        val ticket = ticketFor(b, spell)
        // Здібність відкрила меню й гравець його закрив: квиток просто зникає.

        assertFalse(ticket.isSettled)
        assertEquals(before, b.spirituality.current)
        assertEquals(0.0, b.mastery.value)
    }

    @Test
    fun `settling spends spirituality, grants mastery and reports the cooldown`() {
        val b = beyonder(spell)
        val before = b.spirituality.current

        val receipt = b.settle(ticketFor(b, spell))

        assertEquals(30, receipt.spentSpirituality)
        assertEquals(before - 30, b.spirituality.current)
        assertEquals(20, receipt.cooldownSeconds)
        assertTrue(receipt.masteryGained > 0)
        assertTrue(b.mastery.value > 0)
    }

    @Test
    fun `a ticket cannot be settled twice`() {
        val b = beyonder(spell)
        val ticket = ticketFor(b, spell)
        b.settle(ticket)

        assertThrows(IllegalStateException::class.java) { b.settle(ticket) }
    }

    @Test
    fun `a ticket cannot be settled by another beyonder`() {
        val mine = beyonder(spell)
        val theirs = beyonder(spell)

        assertThrows(IllegalArgumentException::class.java) { theirs.settle(ticketFor(mine, spell)) }
    }

    @Test
    fun `an ability you do not own is refused`() {
        val b = beyonder()
        val refusal = assertInstanceOf(CastAuthorization.Refused::class.java, b.authorize(spell))
        assertEquals(CastRefusal.NotOwned, refusal.reason)
    }

    @Test
    fun `a passive is never castable`() {
        val b = beyonder(passive)
        val refusal = assertInstanceOf(CastAuthorization.Refused::class.java, b.authorize(passive))
        assertEquals(CastRefusal.NotCastable, refusal.reason)
    }

    @Test
    fun `not enough spirituality is refused with the numbers`() {
        val b = beyonder(spell)
        b.setSpirituality(Spirituality.of(5, b.maxSpirituality))

        val refusal = assertInstanceOf(CastAuthorization.Refused::class.java, b.authorize(spell))
        val reason = assertInstanceOf(CastRefusal.InsufficientSpirituality::class.java, refusal.reason)
        assertEquals(30, reason.required)
        assertEquals(5, reason.available)
    }

    @Test
    fun `running the tank dry rattles the mind`() {
        val expensive = TestAbilitySpec.of("expensive", cost = 100)
        val b = beyonder(expensive)
        b.setSpirituality(Spirituality.full(b.maxSpirituality))

        b.settle(ticketFor(b, expensive))

        assertTrue(b.spirituality.isCritical())
        assertTrue(b.sanityLoss.scale > 0, "критично низька духовність має додати втрату глузду")
    }

    @Test
    fun `an off-pathway ability is castable once granted`() {
        val b = beyonder()
        val stolen = TestAbilitySpec.of("stolen", cost = 10)

        assertTrue(b.grantOffPathway(stolen))
        assertFalse(b.grantOffPathway(stolen), "та сама ідентичність не додається двічі")

        val receipt = b.settle(ticketFor(b, stolen))
        assertEquals(10, receipt.spentSpirituality)
    }
}
