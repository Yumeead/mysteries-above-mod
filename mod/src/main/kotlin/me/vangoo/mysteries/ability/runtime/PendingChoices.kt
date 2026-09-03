package me.vangoo.mysteries.ability.runtime

import java.util.UUID

/**
 * A choice an ability is waiting on, until phase 11's real menus exist.
 *
 * `PsychicCue`, `Alteration` and `DreamTraversal` all pause mid-cast for a choice among a
 * handful of options — the plugin drew a chest menu for each. The mod has none yet, so the
 * choice is offered as a numbered list in chat and resolved by `/mysteries choose <n>`, the same
 * temporary shape [AbilitySelection] already stands in for the ability wheel with. Swapping this
 * for a real screen later touches the offering ability, not its callers.
 *
 * One offer per player at a time: a fresh [offer] silently replaces whatever was pending — the
 * player is never left answering a question two abilities asked.
 */
class PendingChoices {

    private class Offer(val labels: List<String>, val onChosen: (Int) -> Unit)

    private val byPlayer = HashMap<UUID, Offer>()

    /** Replaces any choice already pending for [player] with a fresh one. */
    fun offer(player: UUID, labels: List<String>, onChosen: (Int) -> Unit) {
        byPlayer[player] = Offer(labels, onChosen)
    }

    /** The labels of the choice [player] is being asked, if any. */
    fun labels(player: UUID): List<String>? = byPlayer[player]?.labels

    /**
     * Resolves the pending choice for [player] with the option at [index].
     *
     * @return false if there was nothing pending or [index] was out of range — either way the
     *   offer is gone, so the player has to be told rather than left with a live but unusable one.
     */
    fun choose(player: UUID, index: Int): Boolean {
        val offer = byPlayer.remove(player) ?: return false
        if (index !in offer.labels.indices) return false
        offer.onChosen(index)
        return true
    }

    fun forget(player: UUID) {
        byPlayer.remove(player)
    }
}
