package me.vangoo.mysteries.schedule

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue

/** Something put in the queue that can be taken back out of it. */
interface TaskHandle {

    val isActive: Boolean

    fun cancel()
}

/**
 * A work queue tied to the server tick.
 *
 * ## Why a scheduler of our own
 *
 * Bukkit had `Bukkit.getScheduler()`, and the plugin hung on it through 92 calls. Fabric has no
 * scheduler at all — only an end-of-tick event. This is the piece the loader does not give and
 * that has to be written: the loop itself, the queue of deferred work and cancellation.
 *
 * ## The clock is the tick, not wall time
 *
 * The counter ticks only in [advance], i.e. exactly when the server ticked. A paused
 * single-player game stands still — and the sessions with it. `System.currentTimeMillis` would
 * keep going, and a player who opened the menu for a minute would come back to a world where
 * their toggle had spent everything and the wind had already died down.
 *
 * ## One broken tick does not bring down the rest
 *
 * An exception inside a job is caught here and goes to [onError]. In the plugin this same
 * invariant was held by a `try/catch` in every `PassiveAbilityScheduler` loop, and forgetting
 * it was a matter of time.
 *
 * The class does **not** need Minecraft, and that is exactly why it is checked by an ordinary
 * unit test.
 */
class TickScheduler(private val onError: (String, Throwable) -> Unit = { _, _ -> }) {

    private class Entry(
        val block: () -> Unit,
        var dueTick: Long,
        /** 0 — one-off work; more than zero — the repeat period. */
        val periodTicks: Int,
    ) : TaskHandle {

        @Volatile
        private var cancelled = false

        override val isActive: Boolean get() = !cancelled

        override fun cancel() {
            cancelled = true
        }
    }

    private val lock = Any()

    /** Deferred work, the nearest on top. */
    private val timed = PriorityQueue<Entry>(compareBy { it.dueTick })

    /**
     * Work "for right now". The queue is concurrent on purpose: a coroutine returning from
     * `Dispatchers.IO` dispatches itself here from a foreign thread.
     */
    private val immediate = ConcurrentLinkedQueue<Entry>()

    /** How many times the server has ticked since the scheduler came up. */
    var tick: Long = 0L
        private set

    /** Run as soon as possible: this very tick, if it is still draining the queue. */
    fun submit(block: () -> Unit): TaskHandle =
        Entry(block, tick, periodTicks = 0).also { immediate += it }

    /** Once, after [delayTicks] ticks. Zero or less — this very tick. */
    fun after(delayTicks: Int, block: () -> Unit): TaskHandle {
        if (delayTicks <= 0) return submit(block)
        val entry = Entry(block, tick + delayTicks, periodTicks = 0)
        synchronized(lock) { timed += entry }
        return entry
    }

    /** Once every [periodTicks] ticks, the first time after [delayTicks]. */
    fun every(periodTicks: Int, delayTicks: Int = periodTicks, block: () -> Unit): TaskHandle {
        require(periodTicks > 0) { "Період повтору мусить бути додатним: $periodTicks" }
        val entry = Entry(block, tick + maxOf(0, delayTicks), periodTicks)
        synchronized(lock) { timed += entry }
        return entry
    }

    /**
     * One server tick: run whatever came due, drain the "right now" queue.
     *
     * It is drained until empty rather than in a fixed portion: a coroutine that was waiting for
     * this tick often dispatches its continuation straight after resuming, and that continuation
     * must land in **this** tick, otherwise `waitTicks(1)` would silently cost two.
     * The [MAX_DRAIN] ceiling here guards against a loop (`while (true) { yield() }`), not
     * against load.
     */
    fun advance() {
        tick++

        synchronized(lock) {
            while (true) {
                val head = timed.peek() ?: break
                if (head.dueTick > tick) break
                timed.poll()
                if (!head.isActive) continue
                immediate += head
                if (head.periodTicks > 0) {
                    head.dueTick = tick + head.periodTicks
                    timed += head
                }
            }
        }

        var drained = 0
        while (drained < MAX_DRAIN) {
            val entry = immediate.poll() ?: return
            drained++
            if (!entry.isActive) continue
            try {
                entry.block()
            } catch (error: Throwable) {
                onError("Робота планувальника впала на тіку $tick", error)
            }
        }
        onError(
            "Планувальник розібрав $MAX_DRAIN робіт за тік $tick і зупинився — решта поїде наступним",
            IllegalStateException("Черга не порожніє: ${immediate.size} робіт лишилось"),
        )
    }

    /** Server shutdown: the queue must not outlive the world that filled it. */
    fun cancelAll() {
        synchronized(lock) {
            timed.forEach { it.cancel() }
            timed.clear()
        }
        while (true) {
            val entry = immediate.poll() ?: break
            entry.cancel()
        }
    }

    /** How many jobs are waiting. For tests and diagnostics. */
    val pending: Int get() = synchronized(lock) { timed.size } + immediate.size

    private companion object {
        const val MAX_DRAIN = 10_000
    }
}
