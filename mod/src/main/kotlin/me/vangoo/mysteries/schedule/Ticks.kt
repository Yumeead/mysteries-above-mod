package me.vangoo.mysteries.schedule

import kotlin.math.max

/** Ticks per second. A vanilla constant, but writing `20` all over the place is not worth it. */
const val TICKS_PER_SECOND: Int = 20

/** Milliseconds per tick — the rate `delay()` is converted into ticks at. */
const val MILLIS_PER_TICK: Long = 50L

/** Seconds into ticks. A negative value takes no time. */
fun seconds(count: Int): Int = max(0, count) * TICKS_PER_SECOND

/**
 * Milliseconds into ticks, rounding up.
 *
 * Rounding up precisely: `delay(1)` must wait a tick rather than pass straight through —
 * otherwise `while (true) { delay(1) }` would jam the server tick solid.
 */
fun millisToTicks(millis: Long): Int = when {
    millis <= 0L -> 0
    else -> ((millis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
