package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.Cooldowns
import java.util.UUID
import kotlin.math.ceil

/**
 * Cooldowns in server ticks.
 *
 * The clock is [now], the server's tick counter, not wall time. The difference is visible: a
 * paused single-player game stands still, and the cooldown with it.
 * `System.currentTimeMillis` would keep ticking, and a power spent just before opening the menu
 * would come back for free.
 */
class CooldownTable(private val now: () -> Int) : Cooldowns {

    private data class Key(val playerId: UUID, val identity: AbilityIdentity)

    private val readyAt = HashMap<Key, Int>()
    private val lockedUntil = HashMap<UUID, Int>()

    override fun remainingSeconds(playerId: UUID, identity: AbilityIdentity): Long =
        toSeconds(readyTick(playerId, identity) - now())

    /**
     * The **absolute** tick at which the ability is ready again; `0` — ready now.
     *
     * The same number as [remainingSeconds], only not divided by 20 before sending. This is
     * exactly what the client needs: the remainder would have to be sent every second or it
     * would freeze, whereas an absolute tick flies once — after that the client counts down
     * itself.
     *
     * A global lock ([lockAll]) is expressed here naturally: every one of the player's abilities
     * returns the same tick.
     */
    fun readyAtTick(playerId: UUID, identity: AbilityIdentity): Int {
        val tick = now()
        val ready = readyTick(playerId, identity)
        return if (ready <= tick) 0 else ready
    }

    /** The later of the two timers — the ability's own and the global one. Never earlier than "now". */
    private fun readyTick(playerId: UUID, identity: AbilityIdentity): Int {
        val tick = now()
        return maxOf(readyAt[Key(playerId, identity)] ?: tick, lockedUntil[playerId] ?: tick)
    }

    override fun set(playerId: UUID, identity: AbilityIdentity, seconds: Int) {
        if (seconds <= 0) return
        readyAt[Key(playerId, identity)] = now() + seconds * TICKS_PER_SECOND
    }

    override fun clear(playerId: UUID, identity: AbilityIdentity) {
        readyAt.remove(Key(playerId, identity))
    }

    override fun lockAll(playerId: UUID, seconds: Int) {
        if (seconds <= 0) return
        val until = now() + seconds * TICKS_PER_SECOND
        lockedUntil.merge(playerId, until, ::maxOf)
    }

    override fun forget(playerId: UUID) {
        readyAt.keys.removeIf { it.playerId == playerId }
        lockedUntil.remove(playerId)
    }

    /** Removes timers that have already expired: otherwise the map grows by one ability forever. */
    fun prune() {
        val tick = now()
        readyAt.values.removeIf { it <= tick }
        lockedUntil.values.removeIf { it <= tick }
    }

    /** Rounding up: "0 s left" on a timer that has not expired yet would be confusing. */
    private fun toSeconds(ticks: Int): Long =
        if (ticks <= 0) 0L else ceil(ticks.toDouble() / TICKS_PER_SECOND).toLong()

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
