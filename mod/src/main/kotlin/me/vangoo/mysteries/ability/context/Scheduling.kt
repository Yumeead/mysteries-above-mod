package me.vangoo.mysteries.ability.context

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Session
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.schedule.TaskHandle
import java.util.UUID

/**
 * Time: deferred work and live sessions.
 *
 * A direct descendant of the plugin's `ISchedulingContext`, but without its third method.
 * `runAsync` deliberately did not move here: in Bukkit it was used to flee the main thread,
 * because otherwise the server stalled, and half the calls did it "just in case". Here any work
 * is on the server thread anyway, and genuinely background work is `withContext(Dispatchers.IO)`
 * inside a session, written out explicitly and visible to the eye.
 *
 * The clock is the server tick. A paused game spends no time.
 */
interface Scheduling {

    /** How many ticks the server has run since the mod came up. */
    val tick: Long

    /** Once, after [delayTicks]. Zero or less — this very tick. */
    fun after(delayTicks: Int, block: () -> Unit): TaskHandle

    /** Once every [periodTicks], the first time after [delayTicks]. */
    fun every(periodTicks: Int, delayTicks: Int = periodTicks, block: () -> Unit): TaskHandle

    /**
     * Opens a session of [ability] for [owner], **cancelling the previous one with the same
     * key**. A recast replaces the session — that is the registry's behaviour, not the
     * ability's duty.
     */
    fun session(
        owner: UUID,
        ability: AbilitySpec,
        block: suspend SessionScope.() -> Unit,
    ): Session

    fun sessionOf(owner: UUID, ability: AbilityIdentity): Session?

    fun isRunning(owner: UUID, ability: AbilityIdentity): Boolean =
        sessionOf(owner, ability)?.isActive == true

    /** Cancels one session. `true` — it was alive. */
    fun stop(owner: UUID, ability: AbilityIdentity): Boolean

    /** Cancels all of a player's sessions: leaving the game, losing the pathway, death. */
    fun stopAll(owner: UUID)

    /** How many sessions are alive right now — for diagnostics and tests. */
    val live: Collection<Session>
}
