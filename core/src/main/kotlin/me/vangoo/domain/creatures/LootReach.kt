package me.vangoo.domain.creatures

/**
 * A pure rule: which Sequences of loot a chest can hold for the one who opens it.
 *
 * The window runs one Sequence above the opener (the next potion's ingredients — what they are
 * looking for) down to two below (what they still brew for others). Somebody who is not a Beyonder
 * counts as Sequence [NOT_A_BEYONDER] and finds Sequence 9 alone. It replaces the plugin's reroll,
 * which threw weak loot away twice and then kept it anyway.
 */
object LootReach {

    /** Where a non-Beyonder stands: one step below Sequence 9. */
    const val NOT_A_BEYONDER = 10

    fun allows(openerSequence: Int, itemSequence: Int): Boolean =
        itemSequence in openerSequence - 1..openerSequence + 2
}
