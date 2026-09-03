package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Teleport range of "Jump Through Flame" (Seq 7 Magician of the Fool pathway).
 * 30 blocks base, grows with power. Pure math — no platform.
 */
object FlameJumpRange {

    const val BASE_RANGE = 30
    private const val MAX_RANGE = 60

    fun rangeFor(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(MAX_RANGE, (BASE_RANGE * multiplier).roundToInt())
    }
}
