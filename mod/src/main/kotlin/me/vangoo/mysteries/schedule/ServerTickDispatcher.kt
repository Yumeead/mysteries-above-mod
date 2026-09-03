package me.vangoo.mysteries.schedule

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.CoroutineContext

/**
 * A dispatcher for which a "thread" is the server tick.
 *
 * A coroutine launched here wakes up inside [TickScheduler.advance], i.e. on the server's main
 * thread. That is why abilities in a session are allowed to touch the world directly: no
 * `runTask(plugin, …)` around every touch, as there was in the plugin.
 *
 * ## Why [Delay] is implemented too
 *
 * Without it `delay()` would go through the coroutines' own background timer — wall time. A
 * paused single-player game stands still, while such a `delay` would keep ticking, and the
 * session would wake up with a debt in ticks. With [Delay] **every** wait is measured in ticks:
 * `delay(1.seconds)` is exactly 20 server ticks, however much real time they take.
 *
 * The price is an opt-in to [InternalCoroutinesApi]: the [Delay] interface is marked internal.
 * It has been stable for years and every mod dispatcher lives on it; the alternative is leaving
 * a trap the very first `delay()` would step into instead of [waitTicks].
 */
@OptIn(InternalCoroutinesApi::class)
class ServerTickDispatcher(private val scheduler: TickScheduler) : CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        scheduler.submit(block::run)
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>,
    ) {
        val handle = scheduler.after(millisToTicks(timeMillis)) {
            // resumeWith rather than resume(value, onCancellation): the continuation is
            // dispatched back here, i.e. it wakes up in the very tick its term came due.
            continuation.resumeWith(Result.success(Unit))
        }
        continuation.invokeOnCancellation { handle.cancel() }
    }

    override fun invokeOnTimeout(
        timeMillis: Long,
        block: Runnable,
        context: CoroutineContext,
    ): DisposableHandle {
        val handle = scheduler.after(millisToTicks(timeMillis)) { block.run() }
        return DisposableHandle { handle.cancel() }
    }

    override fun toString(): String = "ServerTick"
}
