package me.vangoo.mysteries.pathways.whitetower

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.CombatProficiency
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.common.RitualMagic
import me.vangoo.mysteries.pathways.whitetower.abilities.Agility
import me.vangoo.mysteries.pathways.whitetower.abilities.Analysis
import me.vangoo.mysteries.pathways.whitetower.abilities.CrimeReconstruction
import me.vangoo.mysteries.pathways.whitetower.abilities.Deduction
import me.vangoo.mysteries.pathways.whitetower.abilities.EnhancedMind
import me.vangoo.mysteries.pathways.whitetower.abilities.MirrorCurse
import me.vangoo.mysteries.pathways.whitetower.abilities.Spellcasting
import net.minecraft.network.chat.Component

/**
 * The pathway of the White Tower — knowledge, deduction, light.
 *
 * The first pathway of phase 08 and deliberately the smallest: 2,397 lines in the plugin against
 * Death's 6,735. The routine is worked out on something cheap.
 *
 * **Filled for Seq. 9–5.** The plugin never went higher at all — not one of its twenty-two
 * pathways has a roster for Seq. 4–0. Writing those is a new game, not a port.
 */
object WhiteTower {

    const val NAME: String = "WhiteTower"

    /** The list index **equals** the Sequence: 0 — White Tower, 9 — Reader. */
    private val SEQUENCE_NAMES = listOf(
        "White Tower", "Omniscient Eye", "Wisdom Angel", "Cognizer", "Prophet",
        "Mysticism Magister", "Polymath", "Detective", "Student", "Reader",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(RitualMagic(), EnhancedMind()),
            8 to listOf(Deduction()),
            7 to listOf(
                CombatProficiency(),
                Agility(),
                PhysicalEnhancement(
                    identity = AbilityIdentity.of("whitetower_physical_enhancement"),
                    displayName = Component.literal("Фізичні посилення"),
                    flavour = "Роки над книгами не зробили вас слабким — навпаки.",
                    hearts = 3,
                ),
                CrimeReconstruction(),
            ),
            6 to listOf(Analysis()),
            5 to listOf(Spellcasting(), MirrorCurse()),
        ),
    )
}
