package me.vangoo.mysteries.pathways

import com.mojang.serialization.JsonOps
import me.vangoo.domain.contracts.Contract
import me.vangoo.domain.contracts.ContractTerm
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.pathways.sun.Oaths
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class OathsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() {
            GameRegistries.bootstrap()
        }
    }

    private fun debt(item: String) = Contract.propose(
        UUID.randomUUID(), UUID.randomUUID(), ContractTerm.DEBT,
        mapOf("item" to item, "amount" to "16", "deadlineEpochMillis" to "1234567"),
    )

    @Test
    @DisplayName("Порушена Клятва переживає рестарт разом зі своїми умовами")
    fun breachedOathRoundTrips() {
        val oath = debt("minecraft:iron_ingot").apply { sign(); breach() }

        val json = Oaths.CODEC.encodeStart(JsonOps.INSTANCE, listOf(oath)).getOrThrow()
        val back = Oaths.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().single()

        assertEquals(oath.id, back.id)
        assertEquals(oath.partyB, back.partyB)
        assertEquals(Contract.ContractState.BREACHED, back.state)
        val terms = Oaths.termsOf(back)!!
        assertSame(Items.IRON_INGOT, terms.item)
        assertEquals(16, terms.amount)
        assertEquals(1234567L, terms.deadline)
    }

    @Test
    @DisplayName("Клятва на предмет, якого немає в реєстрі, не читається як умови")
    fun unknownItemHasNoTerms() {
        assertNull(Oaths.termsOf(debt("nosuchmod:nothing")))
    }
}
