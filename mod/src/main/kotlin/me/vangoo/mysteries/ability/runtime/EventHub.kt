package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.event.GameEvent
import java.util.UUID
import kotlin.reflect.KClass

/**
 * The subscription registry: who is waiting for what, and until when.
 *
 * A descendant of the plugin's `TemporaryEventManager`, but without its central mechanism.
 * There a dynamic `Listener` was registered for every new event class through
 * `Bukkit.getPluginManager().registerEvent(...)` — reflection was **mandatory**, because the
 * platform defines the events and there are a thousand of them. Here the mod defines the events,
 * they are all visible in `GameEvent`, and an ordinary [KClass] serves as the map key.
 *
 * ## The three properties it is responsible for
 *
 * 1. **A subscription does not outlive its ability.** The key is an "owner + ability" pair, so
 *    [forget] removes exactly what belongs to one of them, and never anything foreign.
 * 2. **A term expires by itself.** The clock is [now], the server tick: a paused game does not
 *    age subscriptions. They are removed **lazily**, during dispatch rather than by a separate
 *    scheduled job: a subscription whose term has run out will not fire in any case, so there is
 *    no need to pay a tick for it.
 * 3. **One fallen ability does not stop the rest.** The same invariant as in `PassiveTicker`
 *    and `TickScheduler`, and for the same reason: a damage event arrives to everyone at once.
 *
 * Minecraft is not needed here — [GameEvent] has no platform types, so the registry is checked
 * by an ordinary unit test, like the tick queue and the sessions.
 */
class EventHub(
    private val now: () -> Long,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    private inner class Entry(
        val owner: UUID,
        val ability: AbilityIdentity,
        val type: KClass<out GameEvent>,
        val expiresAtTick: Long,
        val handler: (GameEvent) -> Unit,
    ) : Subscription {

        private var cancelled = false

        override val isActive: Boolean
            get() = !cancelled && now() < expiresAtTick

        override fun cancel() {
            cancelled = true
        }
    }

    private val byType = HashMap<KClass<out GameEvent>, MutableList<Entry>>()

    // ── Subscription ────────────────────────────────────────────────────────────

    fun <E : GameEvent> subscribe(
        owner: UUID,
        ability: AbilityIdentity,
        type: KClass<E>,
        durationTicks: Int,
        handler: (E) -> Unit,
    ): Subscription {
        require(durationTicks > 0) { "Строк підписки має бути додатним, а не $durationTicks" }

        val expiresAt =
            if (durationTicks == EVENT_FOREVER) Long.MAX_VALUE else now() + durationTicks

        @Suppress("UNCHECKED_CAST")
        val entry = Entry(owner, ability, type, expiresAt, handler as (GameEvent) -> Unit)
        byType.getOrPut(type) { mutableListOf() } += entry
        return entry
    }

    /** A view of the registry through the eyes of one ability of one player — what the context sees. */
    fun scopeOf(owner: UUID, ability: AbilityIdentity): Events = Scope(owner, ability)

    private inner class Scope(
        private val owner: UUID,
        private val ability: AbilityIdentity,
    ) : Events {
        override fun <E : GameEvent> subscribe(
            type: KClass<E>,
            durationTicks: Int,
            handler: (E) -> Unit,
        ): Subscription = this@EventHub.subscribe(owner, ability, type, durationTicks, handler)
    }

    // ── Dispatch ────────────────────────────────────────────────────────────────

    /**
     * Hands the event to everyone waiting for exactly its type.
     *
     * The key is the exact event class, with no walk up the hierarchy: `GameEvent` is a flat
     * list of final types, and subscribing to "something common" simply has no meaning here.
     *
     * A handler may both unsubscribe and subscribe during dispatch — hence the list is walked
     * over a snapshot, and new subscriptions do not see this same event.
     */
    fun publish(event: GameEvent) {
        val waiting = byType[event::class] ?: return
        if (waiting.isEmpty()) return

        for (entry in waiting.toList()) {
            if (!entry.isActive) continue
            try {
                entry.handler(event)
            } catch (error: Throwable) {
                onError("Handler for ${event::class.simpleName} threw: ${entry.ability}", error)
                entry.cancel()
            }
        }

        waiting.removeIf { !it.isActive }
        if (waiting.isEmpty()) byType.remove(event::class)
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────────

    /** Removes one ability's subscriptions: re-activation, losing the ability. */
    fun forget(owner: UUID, ability: AbilityIdentity) =
        drop { it.owner == owner && it.ability == ability }

    /** Removes all of a player's subscriptions: leaving the world, losing Beyonder status. */
    fun forget(owner: UUID) = drop { it.owner == owner }

    /** Server shutdown. An integrated server comes up again inside the same process. */
    fun clear() = byType.clear()

    /** How many subscriptions are alive right now — for diagnostics and tests. */
    val live: Int get() = byType.values.sumOf { list -> list.count { it.isActive } }

    private fun drop(matches: (Entry) -> Boolean) {
        val empty = mutableListOf<KClass<out GameEvent>>()
        for ((type, list) in byType) {
            list.removeIf { entry -> matches(entry).also { if (it) entry.cancel() } }
            if (list.isEmpty()) empty += type
        }
        empty.forEach(byType::remove)
    }
}
