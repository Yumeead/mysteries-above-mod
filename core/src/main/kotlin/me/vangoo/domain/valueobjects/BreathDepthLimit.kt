package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Depth ceiling for "endless" underwater breathing (Seq 7 Magician of the Fool pathway).
 *
 * The Beyonder breathes underwater indefinitely UNTIL he dives deeper than the
 * threshold below the water surface (5 blocks base, grows with power). Deeper than
 * that, oxygen drains as usual. Pure math — no platform.
 */
object BreathDepthLimit {

    const val BASE_DEPTH = 5
    private const val MAX_DEPTH = 12

    /** Maximum depth (in blocks below the surface) at which the breathing still holds. */
    fun maxDepth(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(MAX_DEPTH, (BASE_DEPTH * multiplier).roundToInt())
    }
}
