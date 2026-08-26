package me.vangoo.mysteries.ability.context

import me.vangoo.domain.valueobjects.AbilityIdentity
import java.util.UUID

/**
 * Кулдауни здібностей.
 *
 * Живуть у моді, а не в `core`: це таймер, прив'язаний до серверного тіку, а не
 * правило. Саме тому [me.vangoo.domain.entities.Beyonder.authorize] їх не перевіряє —
 * питати треба **до** нього.
 */
interface Cooldowns {

    fun remainingSeconds(playerId: UUID, identity: AbilityIdentity): Long

    fun isOnCooldown(playerId: UUID, identity: AbilityIdentity): Boolean =
        remainingSeconds(playerId, identity) > 0

    fun set(playerId: UUID, identity: AbilityIdentity, seconds: Int)

    fun clear(playerId: UUID, identity: AbilityIdentity)

    /** Замикає геть усі здібності гравця — приголомшення, зрив касту, ефект чужої сили. */
    fun lockAll(playerId: UUID, seconds: Int)

    /** Гравець вийшов зі світу: тримати його таймери більше нема сенсу. */
    fun forget(playerId: UUID)
}
