package me.vangoo.domain.creatures

import me.vangoo.domain.valueobjects.LootTableData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CreatureSelectorTest {

    private fun def(
        id: String,
        spawn: SpawnRule,
        pathway: String? = "visionary",
        sequence: Int = 9,
    ) = CreatureDefinition(
        id, "GUARDIAN", CreatureTier.COMMON,
        LootTableData(emptyList(), 1, 2),
        spawn, pathway, sequence,
    )

    private fun natural(chance: Double) = SpawnRule(listOf("OCEAN"), listOf("GUARDIAN"), chance, 0.0)

    private fun structure(chance: Double) = SpawnRule(emptyList(), emptyList(), 0.0, chance)

    /** Усе, кого може віддати вікно послідовності playerSequence, за 1000 рівномірних roll'ів. */
    private fun structurePool(s: CreatureSelector, playerSequence: Int): Set<String> =
        (0 until 1000).mapNotNull { s.pickForStructure(playerSequence, null, it / 1000.0)?.id }.toSet()

    @Test
    fun biomeMatchWithinChanceReturnsCreature() {
        val s = CreatureSelector(listOf(def("a", natural(0.5))))
        assertNotNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.4))
    }

    @Test
    fun biomeRollBeyondChanceReturnsNull() {
        val s = CreatureSelector(listOf(def("a", natural(0.5))))
        assertNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.6))
    }

    @Test
    fun wrongBiomeReturnsNull() {
        val s = CreatureSelector(listOf(def("a", natural(0.5))))
        assertNull(s.pickForBiome("PLAINS", "GUARDIAN", 0.1))
    }

    @Test
    fun wrongBaseEntityReturnsNull() {
        val s = CreatureSelector(listOf(def("a", natural(0.5))))
        assertNull(s.pickForBiome("OCEAN", "ZOMBIE", 0.1))
    }

    @Test
    fun structureWindowIsOwnAndNextSequence() {
        val s = CreatureSelector(
            listOf(
                def("s9", structure(0.5), "visionary", 9),
                def("s8", structure(0.5), "visionary", 8),
                def("s7", structure(0.5), "visionary", 7),
            ),
        )
        assertEquals(setOf("s9", "s8"), structurePool(s, 9))
        assertEquals(setOf("s8", "s7"), structurePool(s, 8))
    }

    @Test
    fun structureApexNeedsSequenceSixOrStronger() {
        val s = CreatureSelector(
            listOf(
                def("apex", structure(0.5), "visionary", 5),
                def("weak", structure(0.5), "visionary", 9),
            ),
        )
        for (seq in 9 downTo 7) {
            assertFalse("apex" in structurePool(s, seq), "Посл. $seq не має бачити apex")
        }
        assertTrue("apex" in structurePool(s, 6))
        assertTrue("apex" in structurePool(s, 5))
    }

    @Test
    fun structureZeroWeightNeverSpawns() {
        val s = CreatureSelector(listOf(def("mute", structure(0.0), "visionary", 9)))
        assertTrue(structurePool(s, 9).isEmpty())
    }

    @Test
    fun structureIgnoresBiomeAndReplaceRules() {
        // натуральні правила порожні — структурний спавн на них не дивиться
        val s = CreatureSelector(listOf(def("a", structure(0.5), "visionary", 9)))
        assertEquals(setOf("a"), structurePool(s, 9))
    }

    @Test
    fun structureBiasPrefersOwnPathwayButKeepsOthersReachable() {
        val s = CreatureSelector(
            listOf(
                def("mine", structure(0.5), "visionary", 8),
                def("other", structure(0.5), "fool", 8),
            ),
        )
        val bias = ConvergenceBias("visionary", 9)
        val picked = (0 until 1000).map { s.pickForStructure(9, bias, it / 1000.0)!!.id }

        assertEquals(setOf("mine", "other"), picked.toSet(), "чужий шлях лишається досяжним")
        assertTrue(picked.count { it == "mine" } > 500, "свій шлях мусить важити більше, а не рівно")
    }

    @Test
    fun weightedSegmentsPickByRoll() {
        val s = CreatureSelector(listOf(def("a", natural(0.2)), def("b", natural(0.3))))
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.1)?.id)
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.35)?.id)
        assertNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.9))
    }

    @Test
    fun structureEveryCandidateInWindowIsReachable() {
        // 12 кандидатів однієї послідовності: жоден не мусить лишитись за межею сегментів
        val all = (0 until 12).map { def("c$it", structure(0.12), "visionary", 9) }
        assertEquals(12, structurePool(CreatureSelector(all), 9).size)
    }

    @Test
    fun emptyRegistryReturnsNull() {
        val s = CreatureSelector(emptyList())
        assertNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.0))
        assertNull(s.pickForStructure(9, null, 0.0))
    }

    @Test
    fun defaultBiasArgumentIsNull() {
        val s = CreatureSelector(listOf(def("a", natural(0.5))))
        assertEquals(
            s.pickForBiome("OCEAN", "GUARDIAN", 0.4),
            s.pickForBiome("OCEAN", "GUARDIAN", 0.4, null),
        )
    }

    @Test
    fun convergencePreservesTotalSpawnProbability() {
        // два visionary-кандидати, посл. 9 і 8, базовий шанс 0.2 кожен -> сума 0.4
        val s = CreatureSelector(
            listOf(def("a", natural(0.2), "visionary", 9), def("b", natural(0.2), "visionary", 8)),
        )
        val bias = ConvergenceBias("Visionary", 9) // next-needed = 8 (x4), current = 9 (x2)

        // загальна ймовірність спавну незмінна: roll трохи менший за 0.4 спавнить, 0.4 — ні
        assertNotNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.399, bias))
        assertNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.4, bias))
    }

    @Test
    fun convergenceFavorsNextNeededSequence() {
        val s = CreatureSelector(
            listOf(
                def("a", natural(0.2), "visionary", 9), // current -> x2
                def("b", natural(0.2), "visionary", 8), // next-needed -> x4
            ),
        )
        val bias = ConvergenceBias("Visionary", 9)
        // ваги: a=0.4, b=0.8, сума=1.2, scale=0.4/1.2 -> a=0.1333, b=0.2667
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, bias)?.id)
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.20, bias)?.id)
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.39, bias)?.id)
    }

    @Test
    fun convergenceForUnrelatedPathwayIsNoOp() {
        val s = CreatureSelector(
            listOf(def("a", natural(0.2), "visionary", 9), def("b", natural(0.2), "visionary", 8)),
        )
        val unrelated = ConvergenceBias("Error", 5)
        // жоден кандидат не збігається -> ті самі сегменти, що й без ухилу
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, unrelated)?.id)
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.30, unrelated)?.id)
        assertNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.40, unrelated))
    }

    @Test
    fun biasAgainstNullPathwayCreatureIsNoOp() {
        val s = CreatureSelector(listOf(def("x", natural(0.3), null, 0)))
        assertNotNull(s.pickForBiome("OCEAN", "GUARDIAN", 0.1, ConvergenceBias("Visionary", 9)))
    }

    @Test
    fun ambientPicksPathwayAndBiomeMatch() {
        val s = CreatureSelector(listOf(def("a", natural(0.2), "visionary", 9)))
        assertEquals("a", s.pickForAmbient("OCEAN", ConvergenceBias("Visionary", 9), 0.5)?.id)
    }

    @Test
    fun ambientIncludesOtherPathways() {
        val s = CreatureSelector(listOf(def("a", natural(0.2), "visionary", 9)))
        // істота чужого шляху придатна, бо збігається біом
        assertEquals("a", s.pickForAmbient("OCEAN", ConvergenceBias("Error", 5), 0.5)?.id)
    }

    @Test
    fun ambientMixesPathwaysOwnFavored() {
        val s = CreatureSelector(
            listOf(
                def("own", natural(0.2), "visionary", 8), // next-needed для ухилу посл. 9 -> x4
                def("other", natural(0.2), "error", 8), // чужий шлях -> x1
            ),
        )
        val bias = ConvergenceBias("Visionary", 9)
        // ваги: own = 0.8, other = 0.2, сума = 1.0; own у [0,0.8), other у [0.8,1.0)
        assertEquals("own", s.pickForAmbient("OCEAN", bias, 0.5)?.id)
        assertEquals("other", s.pickForAmbient("OCEAN", bias, 0.9)?.id)
    }

    @Test
    fun ambientNullWhenBiomeMismatch() {
        val s = CreatureSelector(listOf(def("a", natural(0.2), "visionary", 9)))
        assertNull(s.pickForAmbient("PLAINS", ConvergenceBias("Visionary", 9), 0.5))
    }

    @Test
    fun ambientNullBiasIsNull() {
        val s = CreatureSelector(listOf(def("a", natural(0.2), "visionary", 9)))
        assertNull(s.pickForAmbient("OCEAN", null, 0.5))
    }

    @Test
    fun ambientFavorsNextNeededSequence() {
        val s = CreatureSelector(
            listOf(
                def("a", natural(0.2), "visionary", 9), // current -> x2
                def("b", natural(0.2), "visionary", 8), // next-needed -> x4
            ),
        )
        val bias = ConvergenceBias("Visionary", 9)
        // ваги a=0.4, b=0.8, сума=1.2; target = roll*1.2; a у [0,0.4), b у [0.4,1.2)
        assertEquals("a", s.pickForAmbient("OCEAN", bias, 0.1)?.id)
        assertEquals("b", s.pickForAmbient("OCEAN", bias, 0.5)?.id)
        assertEquals("b", s.pickForAmbient("OCEAN", bias, 0.99)?.id)
    }
}
