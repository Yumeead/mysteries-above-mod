package me.vangoo.mysteries.client.beyonder

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import kotlin.math.ceil

/**
 * Everything the client knows about the player's abilities.
 *
 * A cache of the last two packets plus a tick counter of its own. A cache is exactly what is
 * needed: drawing happens 60+ times a second while packets arrive at most once a second —
 * something has to hold on in between, and until now nothing on the client did.
 *
 * **It computes nothing from the game rules.** Neither spirituality, nor permissions, nor
 * whether a cast is possible: the server has already decided all that and sent it. The only
 * arithmetic here is counting a cooldown down between packets, and even that is derived from
 * the end tick that was sent.
 *
 * The state is global because the client itself is: there is exactly one player here.
 */
object ClientAbilityState {

    /**
     * A monotonic counter of client ticks.
     *
     * Wall time will not do: a paused single-player game stands still — the server and the
     * cooldowns with it — while `System.currentTimeMillis` would keep going and show readiness
     * before it arrives.
     */
    private var clientTick = 0

    /** The client tick and the server tick at the moment of the last state packet. */
    private var anchorClientTick = 0
    private var anchorServerTick = 0
    private var anchored = false

    /** `null` — the player is not a Beyonder. */
    var roster: AbilityRosterPayload.Roster? = null
        private set

    /** `null` — no state has been sent yet (or the player is not a Beyonder). */
    var spirituality: Spirituality? = null
        private set

    /** Sanity loss. For a non-Beyonder it is [SanityLoss.none]: there is nothing to shake. */
    var sanityLoss: SanityLoss = SanityLoss.none()
        private set

    /** The identity of the selected ability; the server holds the selection, this is only its echo. */
    var selected: String? = null
        private set

    private val live = HashMap<String, AbilityStatePayload.Live>()

    val isBeyonder: Boolean get() = roster != null

    /** The order is the same as in `Beyonder.abilities`: stable, made for muscle memory. */
    val entries: List<AbilityRosterPayload.Entry> get() = roster?.entries.orEmpty()

    fun entry(identity: String): AbilityRosterPayload.Entry? = entries.firstOrNull { it.identity == identity }

    // ── Incoming packets ────────────────────────────────────────────────────────

    /** Called from the client tick; the only place [clientTick] advances. */
    fun tick() {
        clientTick++
    }

    fun accept(payload: AbilityRosterPayload) {
        val fresh = payload.roster
        roster = fresh
        if (fresh == null) {
            // The player stopped being a Beyonder: there will be no more state, and what was
            // left would show cooldowns of a power that is gone.
            resetState()
            return
        }
        // An ability could have disappeared along with a change of the list — otherwise its
        // entry in [live] would hang around until the player left the world.
        val known = fresh.entries.mapTo(HashSet()) { it.identity }
        live.keys.retainAll(known)
        val chosen = selected
        if (chosen != null && chosen !in known) selected = null
    }

    fun accept(payload: AbilityStatePayload) {
        // Re-anchoring on every packet: the counter's drift does not accumulate.
        anchorServerTick = payload.serverTick
        anchorClientTick = clientTick
        anchored = true

        spirituality = payload.spirituality
        sanityLoss = payload.sanityLoss
        selected = payload.selected

        // The snapshot is complete by design: the packet holds only those with something to say,
        // so a missing entry means "ready, switched off, not running".
        live.clear()
        payload.live.associateByTo(live) { it.identity }
    }

    /** Leaving the world. The next world starts with a clean slate. */
    fun clear() {
        roster = null
        resetState()
    }

    private fun resetState() {
        spirituality = null
        sanityLoss = SanityLoss.none()
        selected = null
        live.clear()
        anchored = false
    }

    // ── Derived numbers ─────────────────────────────────────────────────────────

    /** The server tick as it should be now: the anchor plus what the client counted itself. */
    fun estimatedServerTick(): Int = anchorServerTick + (clientTick - anchorClientTick)

    fun isToggled(identity: String): Boolean = live[identity]?.toggled == true

    fun isSustaining(identity: String): Boolean = live[identity]?.sustaining == true

    /** How many ticks of cooldown are left; `0` — the ability is ready. */
    fun remainingTicks(identity: String): Int {
        if (!anchored) return 0
        val readyAt = live[identity]?.readyAtTick ?: return 0
        if (readyAt == 0) return 0
        return (readyAt - estimatedServerTick()).coerceAtLeast(0)
    }

    /**
     * Rounding up — the same rule as in the server's `CooldownTable`:
     * "0 s left" on a timer that has not expired yet is confusing.
     */
    fun remainingSeconds(identity: String): Int {
        val ticks = remainingTicks(identity)
        return if (ticks <= 0) 0 else ceil(ticks.toDouble() / TICKS_PER_SECOND).toInt()
    }

    /**
     * The share of the cooldown that has already passed: `0` — it just started, `1` — ready.
     *
     * The roster brings the full duration, so the proportion is honest rather than guessed from
     * the first remainder seen.
     */
    fun cooldownProgress(entry: AbilityRosterPayload.Entry): Float {
        val total = entry.cooldownSeconds * TICKS_PER_SECOND
        if (total <= 0) return 1f
        val remaining = remainingTicks(entry.identity)
        return (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * The identity [step] places away from [current] in [identities]. Wraps around both ends;
     * with nothing (or something absent) selected, a step forward lands on the first.
     */
    fun cycle(identities: List<String>, current: String?, step: Int): String? {
        if (identities.isEmpty()) return null
        val index = identities.indexOf(current)
        if (index < 0) return identities[if (step > 0) 0 else identities.size - 1]
        return identities[Math.floorMod(index + step, identities.size)]
    }

    /** A hint, not a ban: the key can always be pressed, the server is what refuses. */
    fun canAfford(entry: AbilityRosterPayload.Entry): Boolean =
        spirituality?.hasSufficient(entry.cost) ?: true
}
