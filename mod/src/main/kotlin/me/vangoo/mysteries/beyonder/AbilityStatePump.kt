package me.vangoo.mysteries.beyonder

import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import java.util.UUID

/**
 * Decides **when** to push the ability state to the client.
 *
 * There is one rule: send on change, not on tick. Two halves carry it out — a dirty flag and a
 * throttle:
 *
 * - [markDirty] is called from everywhere the state really changed: a cast, a toggle, the start
 *   and the end of a session, a change of selection, an ascension, spirituality that actually
 *   grew. This does not yet produce a packet.
 * - [flush] sends to the "dirty" once a second and no more often — a player doing nothing and
 *   sitting at the spirituality ceiling produces no packet at all.
 * - [pushNow] bypasses the one-second tick: after a cast the bar must drop in the same frame,
 *   not 900 ms later.
 *
 * **It builds no snapshot and holds no players.** [BeyonderStatus] is a pure snapshot builder;
 * giving it a clock as well would give it a second responsibility, and that is exactly how the
 * plugin's "services that do everything" were born. Only a `UUID` comes here, and [send]
 * decides what to do with it — which is why this class is tested with no game at all.
 */
class AbilityStatePump(
    private val send: (UUID) -> Unit,
    private val now: () -> Long,
    private val periodTicks: Int = TICKS_PER_SECOND,
    private val readAuxiliary: (UUID) -> Pair<SanityLoss, Mastery>? = { null },
) {

    /** A `LinkedHashSet`, so that the send order matches the order things were marked dirty. */
    private val dirty = LinkedHashSet<UUID>()

    private val sentAt = HashMap<UUID, Long>()
    private val sentAuxiliary = HashMap<UUID, Pair<SanityLoss, Mastery>>()

    /** Passive sanity changes must reach a player whose spirituality is already full. */
    fun observeAuxiliary(playerId: UUID) {
        val current = readAuxiliary(playerId) ?: return
        if (current != sentAuxiliary[playerId]) markDirty(playerId)
    }

    private fun sendState(playerId: UUID) {
        send(playerId)
        readAuxiliary(playerId)?.let { sentAuxiliary[playerId] = it }
    }

    fun markDirty(playerId: UUID) {
        dirty += playerId
    }

    /** A player action: the state flies this very tick, out of turn and past the throttle. */
    fun pushNow(playerId: UUID) {
        dirty -= playerId
        sentAt[playerId] = now()
        sendState(playerId)
    }

    /**
     * The one-second tick. Called from the [me.vangoo.mysteries.ability.runtime.AbilityServices]
     * tick, in the same place regeneration already runs.
     */
    fun flush() {
        if (dirty.isEmpty()) return
        val tick = now()

        val iterator = dirty.iterator()
        while (iterator.hasNext()) {
            val playerId = iterator.next()
            val last = sentAt[playerId]
            // The throttle: a state just sent is not duplicated by the one-second tick.
            if (last != null && tick - last < periodTicks) continue
            iterator.remove()
            sentAt[playerId] = tick
            sendState(playerId)
        }
    }

    /** The player is no longer on the server — there is no sense marking or remembering them. */
    fun forget(playerId: UUID) {
        dirty -= playerId
        sentAt.remove(playerId)
        sentAuxiliary.remove(playerId)
    }
}
