package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.mysteries.pathways.common.RitualMagic
import me.vangoo.mysteries.pathways.fool.abilities.DangerIntuition
import me.vangoo.mysteries.pathways.fool.abilities.DivinationArts
import me.vangoo.mysteries.pathways.fool.abilities.SeerSpiritVision

/**
 * The pathway of the Fool — divination and information first (Seer), then trickery and paper
 * (Clown, Magician), then identity itself (Faceless, Marionettist).
 *
 * **Phase 08, Seq. 9-5.** Filled incrementally: Seq. 9 first, the rest to follow. `Shapeshifting`
 * (Seq. 6) and `MarionettistControl` (Seq. 5, the pathway's largest single ability — no Fabric
 * equivalent to the plugin's Citizens-NPC puppet) are deferred to their own design pass.
 */
object Fool {

    const val NAME: String = "Fool"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Fool, 9 — Seer. */
    private val SEQUENCE_NAMES = listOf(
        "Fool", "Lord of Mysteries", "Attendant of Mysteries", "Scholar of Yore", "Bizarro Sorcerer",
        "Marionettist", "Faceless", "Magician", "Clown", "Seer",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.LordOfMysteries,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(DivinationArts(), SeerSpiritVision(), DangerIntuition(), RitualMagic()),
        ),
    )
}
