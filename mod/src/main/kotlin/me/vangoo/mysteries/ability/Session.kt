package me.vangoo.mysteries.ability

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * A live session: a power that lasts in time and may go out.
 *
 * ## What it replaces
 *
 * The plugin had 22 such classes, and each carried the same scaffolding by hand: a
 * `BukkitTask` field, a `runs` counter, an idempotent `cancel()`, removing itself from the
 * session map and `Bukkit.getPlayer(ownerId)` at the start of every tick. The comment "cancel
 * is idempotent" was a real invariant — held together by discipline.
 *
 * Here it is a coroutine. The tick is `waitTicks`, cancellation is cancelling the job, cleanup
 * is `finally` in the body. Writing a session that fails to remove its effect when switched off
 * is now as hard as forgetting a `finally` around an open file.
 *
 * ## The key is the owner plus the ability
 *
 * The [me.vangoo.mysteries.ability.context.Scheduling] registry keeps **one** session per
 * "player + ability" pair: recasting the same ability cancels the previous one itself. In the
 * plugin the "a recast replaces the session" rule was a line in the docs; now it is the
 * registry's behaviour.
 */
class Session internal constructor(
    val owner: UUID,
    val ability: AbilityIdentity,
    private val job: Job,
) {

    val isActive: Boolean get() = job.isActive

    /** Cancels the session. Idempotent; the session body still runs its `finally`. */
    fun stop() {
        job.cancel()
    }

    suspend fun join() {
        job.join()
    }

    override fun toString(): String = "Session($ability, owner=$owner, active=$isActive)"
}

/** Why [sustain] handed control back. Cancellation never lands here — it does not return. */
enum class SessionEnd {

    /** The owner left the world or died. */
    OWNER_GONE,

    /** There was no spirituality left for the upkeep. */
    EXHAUSTED,

    /** The declared time ran out. */
    EXPIRED,
}

/**
 * What a session sees from inside.
 *
 * The cast context is deliberately absent: [AbilityContext] carries a live `ServerPlayer` and
 * the identity of one call, while a session outlives both a respawn and a dimension change.
 * Instead of a stored player there is [owner], which asks the server's list anew every time.
 */
interface SessionScope : CoroutineScope {

    val ownerId: UUID

    val ability: AbilitySpec

    /**
     * The owner **right now**. Do not cache between ticks: after a death or a dimension change
     * the server creates a new `ServerPlayer`, and the old object quietly stops being that
     * player. This is the quietest bug in this layer, and precisely why the player is never
     * handed out for good.
     */
    val owner: ServerPlayer?

    val beyonder: Beyonder?

    /** How many ticks the session has been alive. */
    val ticksAlive: Long

    /** The upkeep per tick from [AbilitySpec.periodicCost] for the current Sequence. */
    val periodicCost: Int

    suspend fun waitTicks(ticks: Int)

    /** Pays the upkeep. `false` — there was no spirituality left, the session must go out. */
    fun payUpkeep(amount: Int = periodicCost): Boolean
}

/**
 * The main loop of a session — what all 22 of the plugin's `BukkitRunnable`s were together.
 *
 * Ticks [body] once every [periodTicks], keeps the upkeep going and hands control back when the
 * power must go out on its own: the owner left, the spirituality ran out or [durationTicks]
 * expired. Cancellation from the outside (a recast, leaving the game, the server stopping) does
 * not return here — it flies on as a `CancellationException`, so **cleanup is written in
 * `finally`**, not after the call.
 *
 * ```kotlin
 * context.session {
 *     try {
 *         val end = sustain(periodTicks = 2, upkeep = periodicCost) { player ->
 *             trail(player)
 *         }
 *         if (end == SessionEnd.EXHAUSTED) message("Духовність вичерпана — вітер стихає")
 *     } finally {
 *         owner?.removeEffect(MobEffects.SPEED)
 *     }
 * }
 * ```
 */
suspend fun SessionScope.sustain(
    periodTicks: Int = 1,
    upkeep: Int = 0,
    upkeepPeriodTicks: Int = TICKS_PER_SECOND,
    durationTicks: Int = 0,
    body: (ServerPlayer) -> Unit,
): SessionEnd {
    require(periodTicks > 0) { "Такт сесії мусить бути додатним: $periodTicks" }
    require(upkeepPeriodTicks > 0) { "Період утримання мусить бути додатним: $upkeepPeriodTicks" }

    val deadline = if (durationTicks > 0) ticksAlive + durationTicks else Long.MAX_VALUE
    var sinceUpkeep = 0

    while (true) {
        val player = owner ?: return SessionEnd.OWNER_GONE
        body(player)

        if (ticksAlive >= deadline) return SessionEnd.EXPIRED
        waitTicks(periodTicks)
        if (ticksAlive >= deadline) return SessionEnd.EXPIRED

        sinceUpkeep += periodTicks
        if (upkeep > 0) {
            // Paid **for every** period that has passed, not once per tick. A session's tick
            // can be longer than the payment period (`SunHalo` strikes once every two seconds
            // but pays every second) — and the previous revision quietly charged half of what
            // was declared in that case. The counter is decreased by what was paid rather than
            // zeroed: otherwise the remainder of the tick would be lost.
            val due = upkeepDue(sinceUpkeep, upkeepPeriodTicks)
            sinceUpkeep -= due * upkeepPeriodTicks
            repeat(due) {
                if (!payUpkeep(upkeep)) return SessionEnd.EXHAUSTED
            }
        }
    }
}

/**
 * How many times the upkeep came due over [sinceUpkeep] accumulated ticks.
 *
 * A separate function precisely because there was a bug here: `if (sinceUpkeep >= period)` paid
 * exactly once, however many periods fitted into the session's tick. Now it is a division — and
 * it can be checked without the game.
 */
internal fun upkeepDue(sinceUpkeep: Int, upkeepPeriodTicks: Int): Int =
    if (upkeepPeriodTicks <= 0) 0 else sinceUpkeep / upkeepPeriodTicks
