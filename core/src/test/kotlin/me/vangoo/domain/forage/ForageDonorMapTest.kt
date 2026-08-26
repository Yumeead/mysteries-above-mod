package me.vangoo.domain.forage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ForageDonorMapTest {

    private fun map() = ForageDonorMap("WARPED_ROOTS", "AZALEA_LEAVES", mapOf("POPPY" to "CRIMSON_ROOTS"))

    @Test
    fun plantDefaultWhenNoOverride() {
        assertEquals("WARPED_ROOTS", map().donorFor("SHORT_GRASS", false))
    }

    @Test
    fun leavesDefaultWhenNoOverride() {
        assertEquals("AZALEA_LEAVES", map().donorFor("OAK_LEAVES", true))
    }

    @Test
    fun overrideBeatsDefault() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("POPPY", false))
    }

    @Test
    fun namesAreCaseInsensitive() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("poppy", false))
        assertEquals("CRIMSON_ROOTS", map().donorFor(" Poppy ", false))
    }

    @Test
    fun unknownOriginalFallsBackToClassDefault() {
        assertEquals("WARPED_ROOTS", map().donorFor("DANDELION", false))
        assertEquals("AZALEA_LEAVES", map().donorFor("BIRCH_LEAVES", true))
    }
}
