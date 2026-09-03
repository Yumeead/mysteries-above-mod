package me.vangoo.mysteries.pathways.tyrant

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.mysteries.pathways.tyrant.abilities.IllusoryScales
import me.vangoo.mysteries.pathways.tyrant.abilities.RestorativeWater

/**
 * The pathway of the Tyrant — the sea, the storm and the wind.
 *
 * Not a subclass of `Pathway` as in the plugin: there the base constructor called the overridden
 * `initializeAbilities()`, i.e. the subclass filled a field that did not exist yet. Here a
 * pathway is simply a value, and this is the factory that assembles it.
 *
 * **Filled only for phase 05.** Of the Tyrant's 30 abilities, two have been ported — exactly the
 * ones that close the "drink a potion → cast" cycle. The rest is phase 08.
 */
object Tyrant {

    const val NAME: String = "Tyrant"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Tyrant, 9 — Sailor. */
    private val SEQUENCE_NAMES = listOf(
        "Tyrant", "God of Thunder", "Calamity", "Sea King", "Calamity Patron",
        "Ocean Songster", "Wind-Blessed", "Seafarer", "Furious One", "Sailor",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(IllusoryScales()),
            7 to listOf(RestorativeWater()),
        ),
    )
}
