package me.vangoo.domain.valueobjects

import kotlin.math.max

/**
 * Balance numbers for the improved Danger Intuition (Seq 9 Seer of the Fool pathway).
 *
 * Every value grows with the Beyonder's power (a lower Sequence means a stronger
 * premonition). Pure math — no platform, covered by unit tests. The effects
 * (particles, glow, dash) live in `pathways.fool.abilities.DangerIntuition`.
 */
object DangerPremonition {

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /**
     * Chance to automatically dodge a blow that would have been lethal.
     * Seq 9 ≈ 12%, rising to a 40% ceiling (never an absolute shield).
     */
    fun lethalDodgeChance(sequence: Sequence): Double =
        (0.12 + power(sequence) * 0.03).coerceIn(0.0, 0.40)

    /**
     * Internal cooldown between auto-dodges (ms). A stronger Beyonder recovers the
     * premonition faster: Seq 9 = 20s … never shorter than 6s.
     */
    fun lethalDodgeCooldownMillis(sequence: Sequence): Long =
        max(6_000L, 20_000L - power(sequence) * 1_500L)

    /**
     * Chance (per check) to show a hint about the next action of an enemy the Beyonder is
     * watching. A rare manifestation: Seq 9 ≈ 4% … 25% ceiling.
     */
    fun actionPredictionChance(sequence: Sequence): Double =
        (0.04 + power(sequence) * 0.015).coerceIn(0.0, 0.25)
}
