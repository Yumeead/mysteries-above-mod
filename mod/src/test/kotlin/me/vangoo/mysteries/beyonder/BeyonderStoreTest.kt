package me.vangoo.mysteries.beyonder

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.pathways.door.abilities.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class BeyonderStoreTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() {
            GameRegistries.bootstrap()
        }
    }

    @Test
    @DisplayName("Збережений Потойбічний проходить кодек туди й назад разом зі сторінкою запису")
    fun savedBeyonderRoundTrips() {
        val sun = ModPathways.byName("Sun")!!
        val beyonder = Beyonder(UUID.randomUUID(), Sequence.of(7), sun)
        beyonder.setMastery(Mastery.of(42.5))
        beyonder.increaseSanityLoss(12)
        val donor = sun.abilitiesFor(9).first { it is ActiveAbility }
        val page = Record.page(AbilityIdentity.of("door_recorded_${donor.identity.id}"))!!
        assertTrue(beyonder.grantOffPathway(page))

        val saved = BeyonderStore.snapshot(beyonder)
        val json: JsonElement = BeyonderStore.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow()
        val back = BeyonderStore.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()

        assertEquals(saved, back)
        assertEquals("Sun", back.pathway)
        assertEquals(7, back.sequence)
        assertEquals(42.5, back.mastery)
        assertEquals(listOf(page.identity.id), back.pages)
        assertEquals(page.identity, Record.page(AbilityIdentity.of(back.pages.single()))!!.identity)
    }

    @Test
    @DisplayName("Не сторінка запису не відновлюється як сторінка")
    fun onlyPagesAreRebuilt() {
        assertNull(Record.page(AbilityIdentity.of("sun_singing")))
        assertNull(Record.page(AbilityIdentity.of("door_recorded_no_such_power")))
    }
}
