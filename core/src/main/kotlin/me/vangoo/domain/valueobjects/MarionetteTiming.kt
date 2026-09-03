package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.roundToInt

/**
 * Phase timings of Marionette Control (Seq 5 Marionettist of the Fool pathway).
 *
 * Phase 1 (fixing the strings) — 5…20s depending on the caster's mastery. Phase 2
 * (conversion) — 5 min base for a player, scaling strongly with power; ~15s for a mob.
 * Pure math — no platform.
 */
object MarionetteTiming {

    private const val PHASE1_MAX_SECONDS = 20.0
    private const val PHASE1_MIN_SECONDS = 5.0
    private const val PLAYER_CONVERT_BASE_TICKS = 6000 // 5 min at Seq 9
    private const val MOB_SWAP_BASE_TICKS = 300        // 15s at Seq 9

    /** Single-hit damage threshold on the victim that breaks the fixation. */
    const val BREAK_DAMAGE_THRESHOLD = 6.0

    /**
     * Duration of the fixation phase in ticks by mastery (0…100): 20s at 0%,
     * linearly down to 5s at 100%.
     */
    fun phase1LockTicks(masteryPercent: Double): Int {
        val clamped = masteryPercent.coerceIn(0.0, 100.0)
        val seconds = PHASE1_MAX_SECONDS -
            (PHASE1_MAX_SECONDS - PHASE1_MIN_SECONDS) * (clamped / 100.0)
        return (seconds * 20).roundToInt()
    }

    /** Duration of a player's conversion phase (ticks): 5 min base, speeds up with power (STRONG). */
    fun playerConvertTicks(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.STRONG,
        )
        return (PLAYER_CONVERT_BASE_TICKS / multiplier).roundToInt()
    }

    /** Duration of a mob's transformation phase (ticks): ~15s, speeds up with power (WEAK). */
    fun mobSwapTicks(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return (MOB_SWAP_BASE_TICKS / multiplier).roundToInt()
    }
}
