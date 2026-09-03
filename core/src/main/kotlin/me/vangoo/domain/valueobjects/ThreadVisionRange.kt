package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Radius of "Thread Vision" (Seq 5 Marionettist of the Fool pathway): threads above the heads
 * of creatures + detecting invisible entities nearby. 24 blocks base, grows with power.
 * Capped for performance. Pure math.
 */
object ThreadVisionRange {

    private const val BASE_RANGE = 24
    private const val MAX_RANGE = 40

    fun rangeFor(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(MAX_RANGE, (BASE_RANGE * multiplier).roundToInt())
    }
}
