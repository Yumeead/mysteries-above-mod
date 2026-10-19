package me.vangoo.mysteries.client.beyonder

import me.vangoo.domain.hud.AuxiliaryValues
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuxiliaryStateTest {
    @AfterEach fun clear() = ClientAbilityState.clear()

    @Test fun indicatorsFollowServerSnapshotsIncludingHundredthsAndResetOnDisconnect() {
        for ((sanity, digestion) in listOf(0 to 0.0, 37 to 42.37, 0 to .01, 100 to 100.0)) {
            ClientAbilityState.accept(AbilityStatePayload(0,Spirituality(10,10),SanityLoss.of(sanity),
                null,emptyList(),Mastery.of(digestion)))
            assertEquals(sanity*100,ClientAbilityState.auxiliary.sanityLoss)
            assertEquals(Math.round(digestion*100).toInt(),ClientAbilityState.auxiliary.potionDigestion)
        }
        ClientAbilityState.clear()
        assertEquals(AuxiliaryValues(0,0),ClientAbilityState.auxiliary)
    }
}
