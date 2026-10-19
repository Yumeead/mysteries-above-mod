package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class AbilityCastPlannerTest {

    // kit: signature (weight 2), filler (weight 1); both with cooldown 300; GCD 120
    private fun planner(seed: Int) = AbilityCastPlanner(
        listOf(
            AbilityCastPlanner.KitEntry("signature", 300),
            AbilityCastPlanner.KitEntry("filler", 300),
        ),
        120,
        Random(seed),
    )

    @Test
    fun gcdBlocksAnyCastUntilElapsed() {
        val p = planner(1)
        assertNotNull(p.pickNext(0))
        assertNull(p.pickNext(119))
    }

    @Test
    fun neverPicksAbilityOnItsOwnCooldown() {
        val p = planner(1)
        val first = p.pickNext(0)!!
        val second = p.pickNext(120)!! // the first is still on cooldown until 300
        assertNotEquals(first, second)
    }

    @Test
    fun returnsNullWhenEverythingOnCooldown() {
        val p = planner(1)
        p.pickNext(0) // ready again from 300
        p.pickNext(120) // ready again from 420
        assertNull(p.pickNext(240)) // the GCD has passed, both are on cooldown
    }

    @Test
    fun abilityBecomesAvailableAgainAfterItsCooldown() {
        val p = planner(1)
        val first = p.pickNext(0)!!
        p.pickNext(120)
        // t=300: the first is ready again, the second only from 420 → the choice is deterministic
        assertEquals(first, p.pickNext(300))
    }

    @Test
    fun selectionIsWeightedTowardEarlierEntries() {
        // both always ready (cooldown 0), GCD 1 → a pure check of the 2:1 weights
        val p = AbilityCastPlanner(
            listOf(
                AbilityCastPlanner.KitEntry("signature", 0),
                AbilityCastPlanner.KitEntry("filler", 0),
            ),
            1,
            Random(42),
        )
        val signaturePicks = (0L until 3000L).count { p.pickNext(it * 2) == "signature" }
        // expectation 2/3 of 3000 = 2000; the fixed seed makes the check deterministic
        assertTrue(
            signaturePicks in 1701..2299,
            "signature picked $signaturePicks/3000 — вага 2:1 не працює",
        )
    }

    @Test
    fun unusableSkillSpendsNeitherItsCooldownNorTheGcd() {
        val p = planner(1)
        assertEquals("filler", p.pickNext(0) { it == "filler" })
        // the GCD started with the filler; the signature was skipped, so it is ready the moment the GCD is
        assertEquals("signature", p.pickNext(120))
    }

    @Test
    fun nothingUsableMeansNoCastAndNoGcd() {
        val p = planner(1)
        assertNull(p.pickNext(0) { false })
        assertNotNull(p.pickNext(1))
    }

    @Test
    fun skillCooldownsAreIndependentPerPlannerInstance() {
        planner(1).pickNext(0)
        assertNotNull(planner(1).pickNext(0))
    }
}
