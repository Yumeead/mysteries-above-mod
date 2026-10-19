package me.vangoo.domain.creatures

import me.vangoo.domain.creatures.DailyRoutine.Activity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure rules behind creature minds: grudges, the pressure of a Sequence, the day, boss acts. */
internal class CreatureMindRulesTest {

    @Test
    fun threatPicksTheAngriestAndHoldsTheCurrentTargetWithinTheMargin() {
        val table = ThreatTable<String>()
        table.add("tank", 100.0)
        table.add("archer", 105.0)
        // 105 is within 110 % of 100: the creature keeps fighting the one it already fights
        assertEquals("tank", table.pick("tank", listOf("tank", "archer")))
        table.add("archer", 10.0)
        assertEquals("archer", table.pick("tank", listOf("tank", "archer")))
    }

    @Test
    fun threatIgnoresTheAbsentAndTheHarmless() {
        val table = ThreatTable<String>()
        table.add("gone", 50.0)
        assertNull(table.pick(null, listOf("bystander")))
        assertEquals("gone", table.pick(null, listOf("gone", "bystander")))
    }

    @Test
    fun threatFadesAndIsForgotten() {
        val table = ThreatTable<String>()
        table.add("a", 1.0)
        table.decay(0.4)
        assertTrue(table.isEmpty)
        assertThrows(IllegalArgumentException::class.java) { table.add("a", -1.0) }
    }

    @Test
    fun pressureNeedsTwoSequencesAndTwoBeyonders() {
        assertTrue(SequencePressure.dreads(own = 8, other = 6))
        assertFalse(SequencePressure.dreads(own = 8, other = 7))
        assertFalse(SequencePressure.dreads(own = 8, other = null))
        assertFalse(SequencePressure.dreads(own = null, other = 5))
        assertTrue(SequencePressure.preysOn(own = 5, other = 7))
        assertFalse(SequencePressure.preysOn(own = 5, other = 5))
    }

    @Test
    fun citizensWorkByDayGatherInTheAfternoonAndSleepAtNight() {
        assertEquals(Activity.HOME, DailyRoutine.citizen(500))
        assertEquals(Activity.WORK, DailyRoutine.citizen(6_000))
        assertEquals(Activity.GATHER, DailyRoutine.citizen(10_000))
        assertEquals(Activity.HOME, DailyRoutine.citizen(12_000))
        assertEquals(Activity.SLEEP, DailyRoutine.citizen(18_000))
        // the clock keeps running past one day
        assertEquals(Activity.WORK, DailyRoutine.citizen(24_000L * 3 + 6_000))
    }

    @Test
    fun theNightWatchStaysAwake() {
        assertEquals(Activity.PATROL, DailyRoutine.guard(6_000, nightWatch = false))
        assertEquals(Activity.SLEEP, DailyRoutine.guard(18_000, nightWatch = false))
        assertEquals(Activity.PATROL, DailyRoutine.guard(18_000, nightWatch = true))
    }

    @Test
    fun bossPhasesOnlyMoveForward() {
        val phases = BossPhases(listOf(1f, 0.6f, 0.3f))
        assertEquals(0, phases.at(0.9f, current = 0))
        assertEquals(1, phases.at(0.55f, current = 0))
        assertEquals(2, phases.at(0.1f, current = 1))
        // healed back up — still the second act
        assertEquals(1, phases.at(1f, current = 1))
        assertThrows(IllegalArgumentException::class.java) { BossPhases(listOf(0.5f)) }
        assertThrows(IllegalArgumentException::class.java) { BossPhases(listOf(1f, 0.3f, 0.6f)) }
    }
}
