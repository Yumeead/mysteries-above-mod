package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SpawnDistanceGateTest {

    @Test
    fun insideRadiusIsNotFarEnough() {
        assertFalse(SpawnDistanceGate.isFarEnough(1000.0, 0.0, 2000.0))
        assertFalse(SpawnDistanceGate.isFarEnough(1000.0, 1000.0, 2000.0)) // ~1414 < 2000
    }

    @Test
    fun onOrBeyondRadiusIsFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(2000.0, 0.0, 2000.0))
        assertTrue(SpawnDistanceGate.isFarEnough(0.0, 2500.0, 2000.0))
        assertTrue(SpawnDistanceGate.isFarEnough(1500.0, 1500.0, 2000.0)) // ~2121 >= 2000
    }

    @Test
    fun zeroMinDistanceAlwaysFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(0.0, 0.0, 0.0))
        assertTrue(SpawnDistanceGate.isFarEnough(0.0, 0.0, -1.0))
    }
}
