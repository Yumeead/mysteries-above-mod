package me.vangoo.mysteries.ability.runtime

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Session
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.schedule.ServerTickDispatcher
import me.vangoo.mysteries.schedule.TaskHandle
import me.vangoo.mysteries.schedule.TickScheduler
import me.vangoo.mysteries.schedule.MILLIS_PER_TICK
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The registry of live sessions on top of [TickScheduler].
 *
 * One session per "player + ability" pair. A new cast of the same ability cancels the previous
 * session **before** starting a new one: in the plugin this was a line in the rules ("a recast
 * replaces the session: remove and cancel the old one first"), and each of the 22 sessions
 * carried it out by hand — or did not.
 *
 * The scope is a [SupervisorJob]: a session that fell with an exception does not drag the rest
 * down. The exception is logged as one player's error, not as the whole layer collapsing.
 */
class SessionHost(
    private val scheduler: TickScheduler,
    private val beyonders: Beyonders,
    private val players: (UUID) -> ServerPlayer?,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
    /** Told whose session just ended, however it ended. */
    private val onEnded: (UUID) -> Unit = {},
) : Scheduling {

    private val dispatcher = ServerTickDispatcher(scheduler)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + supervisor + CoroutineName("mysteries-sessions"))

    private data class Key(val owner: UUID, val ability: AbilityIdentity)

    private val sessions = LinkedHashMap<Key, Session>()

    override val tick: Long get() = scheduler.tick

    override fun after(delayTicks: Int, block: () -> Unit): TaskHandle =
        scheduler.after(delayTicks, block)

    override fun every(periodTicks: Int, delayTicks: Int, block: () -> Unit): TaskHandle =
        scheduler.every(periodTicks, delayTicks, block)

    override fun session(
        owner: UUID,
        ability: AbilitySpec,
        block: suspend SessionScope.() -> Unit,
    ): Session {
        val key = Key(owner, ability.identity)
        sessions.remove(key)?.stop()

        val startedAt = scheduler.tick
        // LAZY, because the body may run to completion synchronously, and then we would have to
        // remove from the registry something that has not been put there yet.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            LiveSessionScope(this, owner, ability, startedAt).block()
        }
        val session = Session(owner, ability.identity, job)
        sessions[key] = session
        job.invokeOnCompletion { error ->
            if (sessions[key] === session) sessions.remove(key)
            onEnded(owner)
            if (error != null && error !is CancellationException) {
                onError("Session ${ability.identity} of player $owner threw", error)
            }
        }
        job.start()
        return session
    }

    override fun sessionOf(owner: UUID, ability: AbilityIdentity): Session? =
        sessions[Key(owner, ability)]

    override fun stop(owner: UUID, ability: AbilityIdentity): Boolean {
        val session = sessions.remove(Key(owner, ability)) ?: return false
        val wasActive = session.isActive
        session.stop()
        return wasActive
    }

    override fun stopAll(owner: UUID) {
        sessions.keys.filter { it.owner == owner }
            .mapNotNull { sessions.remove(it) }
            .forEach { it.stop() }
    }

    override val live: Collection<Session> get() = sessions.values.toList()

    /**
     * Server shutdown: no session may outlive the world it started in.
     *
     * The children are cancelled, not the scope itself: a single-player game brings the
     * integrated server up and down inside the same process, and a cancelled [SupervisorJob]
     * will never launch a coroutine again — the next world would be left with no sessions at all.
     */
    fun shutdown() {
        sessions.values.toList().forEach { it.stop() }
        sessions.clear()
        supervisor.cancelChildren()
    }

    private inner class LiveSessionScope(
        delegate: CoroutineScope,
        override val ownerId: UUID,
        override val ability: AbilitySpec,
        private val startedAt: Long,
    ) : SessionScope, CoroutineScope by delegate {

        override val owner: ServerPlayer? get() = players(ownerId)

        override val beyonder: Beyonder? get() = beyonders.of(ownerId)

        override val ticksAlive: Long get() = scheduler.tick - startedAt

        override val periodicCost: Int
            get() = beyonder?.let { ability.periodicCost(it.sequence) } ?: 0

        override suspend fun waitTicks(ticks: Int) {
            if (ticks > 0) delay(ticks * MILLIS_PER_TICK)
        }

        override fun payUpkeep(amount: Int): Boolean =
            beyonder?.payUpkeep(amount) ?: false
    }
}
