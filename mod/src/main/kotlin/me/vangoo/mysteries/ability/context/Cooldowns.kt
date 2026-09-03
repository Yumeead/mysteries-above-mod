package me.vangoo.mysteries.ability.context

import me.vangoo.domain.valueobjects.AbilityIdentity
import java.util.UUID

/**
 * Ability cooldowns.
 *
 * They live in the mod, not in `core`: this is a timer tied to the server tick, not a rule.
 * That is exactly why [me.vangoo.domain.entities.Beyonder.authorize] does not check them —
 * they must be asked **before** it.
 */
interface Cooldowns {

    fun remainingSeconds(playerId: UUID, identity: AbilityIdentity): Long

    fun isOnCooldown(playerId: UUID, identity: AbilityIdentity): Boolean =
        remainingSeconds(playerId, identity) > 0

    fun set(playerId: UUID, identity: AbilityIdentity, seconds: Int)

    fun clear(playerId: UUID, identity: AbilityIdentity)

    /** Locks every one of a player's abilities — a stun, a broken cast, someone else's power. */
    fun lockAll(playerId: UUID, seconds: Int)

    /** The player left the world: there is no sense keeping their timers any more. */
    fun forget(playerId: UUID)
}
