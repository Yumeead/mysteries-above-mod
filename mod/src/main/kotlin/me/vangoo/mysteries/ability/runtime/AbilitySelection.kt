package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import java.util.UUID

/**
 * Which ability the player currently holds "in hand".
 *
 * The wheel and the book only send a pick; the answer lives here because the cast key must know
 * **what** to cast right now, and the client knows neither the list of abilities nor whether
 * they are still there.
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
