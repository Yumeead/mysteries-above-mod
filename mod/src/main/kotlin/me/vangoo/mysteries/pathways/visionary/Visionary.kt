package me.vangoo.mysteries.pathways.visionary

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.visionary.abilities.Alteration
import me.vangoo.mysteries.pathways.visionary.abilities.Appeasement
import me.vangoo.mysteries.pathways.visionary.abilities.BattleHypnotism
import me.vangoo.mysteries.pathways.visionary.abilities.DangerSense
import me.vangoo.mysteries.pathways.visionary.abilities.DragonScale
import me.vangoo.mysteries.pathways.visionary.abilities.DreamTraversal
import me.vangoo.mysteries.pathways.visionary.abilities.GoodMemory
import me.vangoo.mysteries.pathways.visionary.abilities.Guidance
import me.vangoo.mysteries.pathways.visionary.abilities.IntentReader
import me.vangoo.mysteries.pathways.visionary.abilities.PsychicCue
import me.vangoo.mysteries.pathways.visionary.abilities.PsychologicalInvisibility
import me.vangoo.mysteries.pathways.visionary.abilities.ScanGaze
import me.vangoo.mysteries.pathways.visionary.abilities.ScanGazePassive
import me.vangoo.mysteries.pathways.visionary.abilities.SharpVision
import me.vangoo.mysteries.pathways.visionary.abilities.SurgeOfInsanity
import me.vangoo.mysteries.pathways.visionary.abilities.Telepathy
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of the Visionary — a mind that reads other minds, from a glance that lingers to
 * one that reaches all the way into someone else's dream.
 *
 * The fifth pathway of phase 08, and the first from `GodAlmighty` rather than
 * `LordOfMysteries` — the domain enum already carried both groups from phase 00.
 *
 * | Seq. | what it reads |
 * |---|---|
 * | 9 | a face — `Хороша пам'ять`, `Сканування поглядом`, `Гострий зір` |
 * | 8 | a threat — `Психічне відлуння`, `Зчитування намірів` |
 * | 7 | a mind under duress — `Всплеск божевілля`, `Умиротворення`, `Телепатія`, `Психічний сигнал` |
 * | 6 | a fight — `Психологічна невидимість`, `Бойовий гіпноз`, `Луска дракона` |
 * | 5 | a dream — `Видозміна`, `Керівництво`, `Блукання у снах` |
 *
 * **Filled for Seq. 9–5** — as much as the plugin filled.
 *
 * ## Two of sixteen carry the pending-choice seam, and say so
 *
 * `PsychicCue` and `Alteration` both used to draw a chest menu; both now offer their choice in
 * chat through [me.vangoo.mysteries.ability.runtime.PendingChoices] and `/mysteries choose <n>`,
 * a real service standing in for phase 11's screens rather than a placeholder. `DreamTraversal`'s
 * menu is not carried forward at all — see its own doc for why the choice itself was cut.
 *
 * ## Battle Hypnotism needed new ground, not a borrowed effect
 *
 * [me.vangoo.mysteries.ability.SelectiveVisibility] exists because this pathway is the first to
 * need "invisible to exactly one viewer" rather than to everyone (`Flash`'s vanilla-invisibility
 * shortcut) — Seq. 6 already has a public invisibility in `PsychologicalInvisibility`, and a second
 * one would have been the same power twice.
 */
object Visionary {

    const val NAME: String = "Visionary"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Visionary, 9 — Spectator. */
    private val SEQUENCE_NAMES = listOf(
        "Visionary", "Author", "Discerner", "Dream Weaver", "Manipulator",
        "Dreamwalker", "Hypnotist", "Psychiatrist", "Telepathist", "Spectator",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(GoodMemory(), ScanGaze(), SharpVision()),
            8 to listOf(DangerSense(), IntentReader()),
            7 to listOf(
                SurgeOfInsanity(),
                Appeasement(),
                Telepathy(),
                PhysicalEnhancement(
                    identity = AbilityIdentity.of("visionary_physical_enhancement"),
                    displayName = Component.literal("Фізичні посилення"),
                    flavour = "Ви отримуєте сильне тіло та надзвичайну швидкість.",
                    hearts = 3,
                    MobEffects.SPEED,
                ),
                ScanGazePassive(),
                PsychicCue(),
            ),
            6 to listOf(PsychologicalInvisibility(), BattleHypnotism(), DragonScale()),
            5 to listOf(Alteration(), Guidance(), DreamTraversal()),
        ),
    )
}
