package me.vangoo.mysteries.pathways

import me.vangoo.domain.entities.Pathway
import me.vangoo.mysteries.pathways.door.Door
import me.vangoo.mysteries.pathways.error.Error
import me.vangoo.mysteries.pathways.justiciar.Justiciar
import me.vangoo.mysteries.pathways.sun.Sun
import me.vangoo.mysteries.pathways.tyrant.Tyrant
import me.vangoo.mysteries.pathways.visionary.Visionary
import me.vangoo.mysteries.pathways.whitetower.WhiteTower

/**
 * The pathways the mod can hand out.
 *
 * The descendant of the plugin's `PathwayManager`, but without its main flaw: there the Sequence
 * names lived in `PathwayManager` while the abilities lived in the pathway itself, so the two
 * halves of one pathway were edited in different files and drifted apart. Here a pathway is
 * assembled entirely in its own factory, and this object only keeps the list.
 *
 * **Two out of twenty-two.** Phase 05 ports exactly three abilities, and there would simply be
 * nothing to fill more pathways with: the rest arrive in phase 08 together with their abilities,
 * and the thirteen stubs together with their potions. The branding (liquid colours and names)
 * already knows all 22 — see `PathwayBranding`.
 */
object ModPathways {

    private val byName: Map<String, Pathway> =
        listOf(
            Tyrant.create(), Sun.create(), WhiteTower.create(), Door.create(), Error.create(), Visionary.create(),
            Justiciar.create(),
        ).associateBy { it.name }

    val all: Collection<Pathway> get() = byName.values

    val names: List<String> get() = byName.keys.toList()

    /** Case does not matter: pathway names travel in commands typed by hand. */
    fun byName(name: String): Pathway? =
        byName.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
