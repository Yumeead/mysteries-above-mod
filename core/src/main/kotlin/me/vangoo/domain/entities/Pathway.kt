package me.vangoo.domain.entities

import me.vangoo.domain.abilities.AbilitySpec

/**
 * A pathway: a name, a group and one ability set per each of the ten Sequences.
 *
 * Not abstract, unlike in the plugin. There the base constructor called
 * `initializeAbilities()` — an overridden method from a constructor, i.e. the subclass filled
 * a field that did not exist yet. Here the set arrives ready from outside, and the pathways in
 * the mod are the factories that assemble it.
 */
class Pathway(
    val name: String,
    val group: PathwayGroup,
    sequenceNames: List<String>,
    roster: Map<Int, List<AbilitySpec>> = emptyMap(),
) {

    private val sequenceNames: List<String> = sequenceNames.toList()
    private val roster: Map<Int, List<AbilitySpec>> =
        roster.mapValues { (_, abilities) -> abilities.toList() }

    init {
        require(name.isNotBlank()) { "Pathway name cannot be blank" }
        require(this.sequenceNames.size == SEQUENCE_COUNT) {
            "Pathway $name must declare $SEQUENCE_COUNT sequence names (0-9), " +
                "got ${this.sequenceNames.size}"
        }
        val stray = this.roster.keys.filterNot { it in 0 until SEQUENCE_COUNT }
        require(stray.isEmpty()) { "Pathway $name has abilities on unknown sequences: $stray" }
    }

    /** The canonical Sequence name from the novel, e.g. `sequenceName(9) == "Матрос"`. */
    fun sequenceName(sequence: Int): String {
        require(sequence in 0 until SEQUENCE_COUNT) { "Sequence out of range: $sequence" }
        return sequenceNames[sequence]
    }

    fun abilitiesFor(sequence: Int): List<AbilitySpec> = roster[sequence].orEmpty()

    /** false — this is a stub pathway: the potion and the characteristics exist, the abilities do not yet. */
    fun hasAnyAbility(): Boolean = roster.values.any { it.isNotEmpty() }

    override fun toString(): String = name

    companion object {
        /** Every pathway has exactly 10 Sequences: 0 is the strongest, 9 the weakest. */
        const val SEQUENCE_COUNT: Int = 10
    }
}
