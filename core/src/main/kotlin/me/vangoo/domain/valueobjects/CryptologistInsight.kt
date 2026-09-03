package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 7 of the Error pathway (Cryptologist).
 *
 * One source of truth for Decryption and for the evidence tier of Superior Observation.
 * Pure math — no platform; the effects live in `pathways.error.abilities`.
 *
 * The key rule from the wiki: Decryption is not divination. The depth of the answer
 * depends on how many traces actually exist ([depthFor]), not on the luck of the cast.
 */
object CryptologistInsight {

    /** The Sequence the ability appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 7

    // --- Cost and ranges ---
    const val DECRYPTION_COST = 110
    const val DECRYPTION_COOLDOWN_SECONDS = 45
    private const val DECRYPTION_COOLDOWN_FLOOR_SECONDS = 20L

    /** Range for finding the aimed-at target/block (modes A and B). */
    const val CROSSHAIR_RANGE = 20.0

    /** Mysticism scan radius (mode D). */
    const val ZONE_RADIUS = 24.0

    /** Radius and window (sec) of the CoreProtect history query. */
    const val TRACE_LOOKUP_RADIUS = 8
    const val TRACE_LOOKUP_WINDOW_SECONDS = 600

    /** Passive evidence marking (Seq 7 tier of Superior Observation). */
    const val EVIDENCE_MARK_RADIUS = 8.0
    const val EVIDENCE_MARK_INTERVAL_SECONDS = 15

    /** Duration of the mode C incantation (sec). */
    const val INCANTATION_DURATION_SECONDS = 180

    /** How many traces are needed for depth 1/2/3 at Seq 7. */
    private val DEPTH_BASE_THRESHOLDS = intArrayOf(2, 5, 9)

    /** Sanity loss per depth 1/2/3 at Seq 7. */
    private val DEPTH_BASE_SANITY = intArrayOf(0, 2, 5)

    /** Cooldown (sec): Seq 7 = 45, shortens MODERATE, never below the floor. */
    fun decryptionCooldownSeconds(sequence: Sequence): Int {
        val seconds = DECRYPTION_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE)
        return max(DECRYPTION_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /**
     * How many traces it takes to reach depth [depth] (1..3).
     * The thresholds fall as the Cryptologist grows stronger.
     */
    fun depthThreshold(depth: Int, sequence: Sequence): Int {
        requireDepth(depth)
        val base = DEPTH_BASE_THRESHOLDS[depth - 1]
        val easier = max(
            0,
            SequenceScaler.getSequencePower(sequence.level) -
                SequenceScaler.getSequencePower(BASE_SEQUENCE),
        )
        return max(depth, base - (easier * base * 0.06).roundToInt())
    }

    /** Answer depth from the available evidence: 0 = nothing to read, 3 = the whole truth. */
    fun depthFor(evidenceCount: Int, sequence: Sequence): Int {
        for (depth in 3 downTo 1) {
            if (evidenceCount >= depthThreshold(depth, sequence)) {
                return depth
            }
        }
        return 0
    }

    /** Sanity loss for deep reading: d1 = 0, d2 = 2, d3 = 5 at Seq 7, grows WEAK. */
    fun sanityLoss(depth: Int, sequence: Sequence): Int {
        if (depth <= 0) {
            return 0
        }
        requireDepth(depth)
        val base = DEPTH_BASE_SANITY[depth - 1].toDouble()
        return (base / relative(sequence, ScalingStrategy.WEAK)).roundToInt()
    }

    /** Damage multiplier of the incantation (mode C, depth 3): Seq 7 = 1.20, grows WEAK. */
    fun incantationDamageMultiplier(sequence: Sequence): Double =
        1.0 + 0.20 / relative(sequence, ScalingStrategy.WEAK)

    /** Ratio of power at Seq 7 to power at the given Sequence (below 1.0 for stronger ones). */
    private fun relative(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy) /
            SequenceScaler.calculateMultiplier(sequence.level, strategy)

    private fun requireDepth(depth: Int) {
        require(depth in 1..3) { "Depth must be 1..3, got: $depth" }
    }
}
