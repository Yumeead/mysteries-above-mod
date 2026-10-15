package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityMode
import java.util.UUID

/**
 * Which mode each player last picked for each ability that has modes.
 *
 * One store instead of a map in every ability: the wheel, the book, the hotkeys and the old
 * crouch-and-cast all change the same answer, and the client is told it in `ABILITY_STATE`.
 *
 * A stored id is checked against the ability's current list on every read, so a mode that a
 * Sequence no longer offers falls back to the first one instead of breaking the cast. Memory
 * only, like the selection: it does not outlive the session.
 */
class AbilityModes {

    private val chosen = HashMap<Pair<UUID, AbilityIdentity>, String>()

    /** The picked mode, or the first; `null` — the ability has no modes. */
    fun of(owner: UUID, ability: Ability, sequence: Sequence): AbilityMode? {
        val all = ability.modes(sequence)
        return all.firstOrNull { it.id == chosen[owner to ability.identity] } ?: all.firstOrNull()
    }

    /** `false` — the ability has no such mode; nothing changes. */
    fun select(owner: UUID, ability: Ability, sequence: Sequence, id: String): Boolean {
        if (ability.modes(sequence).none { it.id == id }) return false
        chosen[owner to ability.identity] = id
        return true
    }

    /** The next mode round the list — the crouch-and-cast fallback. */
    fun cycle(owner: UUID, ability: Ability, sequence: Sequence): AbilityMode? {
        val all = ability.modes(sequence)
        if (all.isEmpty()) return null
        val next = all[(all.indexOf(of(owner, ability, sequence)) + 1) % all.size]
        chosen[owner to ability.identity] = next.id
        return next
    }

    fun forget(owner: UUID) {
        chosen.keys.removeIf { it.first == owner }
    }
}
