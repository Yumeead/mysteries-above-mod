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
 * Шлях Білої Вежі — знання, дедукція, світло.
 *
 * Перший шлях фази 08 і навмисно найменший: 2 397 рядків у плагіні проти 6 735 у
 * Смерті. Рутина відпрацьовується на дешевому.
 *
 * **Заповнено Посл. 9–5.** Вище плагін не піднімався взагалі — жоден із двадцяти двох
 * шляхів не має ростера на Посл. 4–0. Дописати їх — це нова гра, а не порт.
 */
object WhiteTower {

    const val NAME: String = "WhiteTower"

    /** Індекс списку **дорівнює** Послідовності: 0 — Біла Вежа, 9 — Читач. */
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
