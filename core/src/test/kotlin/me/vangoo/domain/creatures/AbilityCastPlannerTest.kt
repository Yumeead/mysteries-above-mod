package me.vangoo.domain.creatures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class AbilityCastPlannerTest {

    // kit: signature (вага 2), filler (вага 1); обидві з кулдауном 300; ГКД 120
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
        val second = p.pickNext(120)!! // перша ще на кулдауні до 300
        assertNotEquals(first, second)
    }

    @Test
    fun returnsNullWhenEverythingOnCooldown() {
        val p = planner(1)
        p.pickNext(0) // готова знову з 300
        p.pickNext(120) // готова знову з 420
        assertNull(p.pickNext(240)) // ГКД минув, обидві на кулдауні
    }

    @Test
    fun abilityBecomesAvailableAgainAfterItsCooldown() {
        val p = planner(1)
        val first = p.pickNext(0)!!
        p.pickNext(120)
        // t=300: перша знову готова, друга — лише з 420 → вибір детермінований
        assertEquals(first, p.pickNext(300))
    }

    @Test
    fun selectionIsWeightedTowardEarlierEntries() {
        // обидві завжди готові (кулдаун 0), ГКД 1 → чиста перевірка ваг 2:1
        val p = AbilityCastPlanner(
            listOf(
                AbilityCastPlanner.KitEntry("signature", 0),
                AbilityCastPlanner.KitEntry("filler", 0),
            ),
            1,
            Random(42),
        )
        val signaturePicks = (0L until 3000L).count { p.pickNext(it * 2) == "signature" }
        // очікування 2/3 від 3000 = 2000; фіксований сід робить перевірку детермінованою
        assertTrue(
            signaturePicks in 1701..2299,
            "signature picked $signaturePicks/3000 — вага 2:1 не працює",
        )
    }

    @Test
    fun skillCooldownsAreIndependentPerPlannerInstance() {
        planner(1).pickNext(0)
        assertNotNull(planner(1).pickNext(0))
    }
}
