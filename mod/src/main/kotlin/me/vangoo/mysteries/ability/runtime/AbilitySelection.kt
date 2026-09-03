package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import java.util.UUID

/**
 * Which ability the player currently holds "in hand".
 *
 * A temporary form of selection: the real one is the ability wheel from phase 11. But the cast
 * key must know **what** to cast right now, and this answer cannot be kept on the client: the
 * client knows neither the list of abilities nor whether they are still there.
 *
 * Memory only and only while the player is in the game — the selection survives neither a
 * disconnect nor an ascension (after which the ability list is different).
 */
class AbilitySelection {

    private val byPlayer = HashMap<UUID, AbilityIdentity>()

    fun of(player: UUID): AbilityIdentity? = byPlayer[player]

    fun select(player: UUID, ability: AbilityIdentity) {
        byPlayer[player] = ability
    }

    fun forget(player: UUID) {
        byPlayer.remove(player)
    }
}
