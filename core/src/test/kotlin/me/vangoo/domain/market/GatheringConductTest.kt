package me.vangoo.domain.market

import me.vangoo.domain.market.GatheringConduct.Sanction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GatheringConductTest {

    private val conduct = GatheringConduct()

    @Test
    fun firstViolationWarnsThenSubsequentKick() {
        val p = UUID.randomUUID()
        assertEquals(Sanction.WARN, conduct.recordViolation(p))
        assertEquals(Sanction.KICK, conduct.recordViolation(p))
        assertEquals(Sanction.KICK, conduct.recordViolation(p))
    }

    @Test
    fun countersAreIndependentPerPlayer() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(Sanction.WARN, conduct.recordViolation(a))
        assertEquals(Sanction.WARN, conduct.recordViolation(b))
        assertEquals(Sanction.KICK, conduct.recordViolation(a))
    }

    @Test
    fun resetClearsCounters() {
        val p = UUID.randomUUID()
        conduct.recordViolation(p)
        conduct.reset()
        assertEquals(Sanction.WARN, conduct.recordViolation(p))
    }
}
