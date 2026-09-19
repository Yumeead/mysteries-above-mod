package me.vangoo.mysteries.client.beyonder

import me.vangoo.mysteries.net.payload.MarionetteStatePayload
import java.util.UUID

/**
 * The client's copy of the row of threads.
 *
 * A cache and nothing else, exactly like [ClientAbilityState]: the server decides who is on whose
 * threads, what they are doing and how long a snapped thread has left, and this holds the last
 * thing it said so the HUD can be drawn sixty times a second off a packet that arrives once.
 *
 * There is no clock here — unlike the cooldowns next door, which the client counts down itself.
 * A strand countdown ticks on the **server's** wall clock, and a second guess at it on this side
 * would drift against the number that actually kills the puppet.
 */
object ClientMarionettes {

    var state: MarionetteStatePayload = MarionetteStatePayload.EMPTY
        private set

    val entries: List<MarionetteStatePayload.Entry> get() = state.entries

    val hasThreads: Boolean get() = state.entries.isNotEmpty()

    val selected: UUID? get() = state.selected

    /** Which puppet the Marionettist is inside, or `null` — they are in their own body. */
    val possessing: UUID? get() = state.possessing

    val limit: Int get() = state.limit

    fun accept(payload: MarionetteStatePayload) {
        state = payload
    }

    fun clear() {
        state = MarionetteStatePayload.EMPTY
    }
}
