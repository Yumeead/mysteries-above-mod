package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 8 of the Error pathway (Swindler).
 *
 * One source of truth for all seven Seq 8 abilities: charm, eloquence, thought substitution,
 * mental disorder, superior observation and material theft.
 * Pure math — no platform; the effects live in `pathways.error.abilities`.
 */
object SwindlerInfluence {

    // --- Spirituality cost and base cooldowns (sec) ---
    const val ELOQUENCE_COST = 35
    const val ELOQUENCE_COOLDOWN_SECONDS = 20
    const val MISDIRECTION_COST = 30
    const val MISDIRECTION_COOLDOWN_SECONDS = 30
    const val DISRUPTION_COST = 45
    const val DISRUPTION_COOLDOWN_SECONDS = 60
    const val MATERIAL_THEFT_COST = 60

    // --- Ranges (blocks) ---
    const val CHARM_AGGRO_RANGE = 16.0
    const val ELOQUENCE_RANGE = 12.0
    const val MISDIRECTION_CONE_RANGE = 10.0
    const val MISDIRECTION_CONE_ANGLE_DEGREES = 90.0
    const val DISRUPTION_RANGE = 15.0
    const val OBSERVATION_GAZE_RANGE = 15.0

    /** Radius for sensing valuables; the wiki widens it to 50 only for Prometheus (Seq 6). */
    const val OBSERVATION_VALUABLES_RADIUS = 10.0
    const val MATERIAL_THEFT_RANGE = 5.0

    /** Chance for a player victim to get a shuffled hotbar from Thought Substitution. */
    const val MISDIRECTION_SCRAMBLE_CHANCE = 0.70

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = SequenceScaler.getSequencePower(sequence.level)

    private fun scaled(base: Double, sequence: Sequence, strategy: ScalingStrategy): Double =
        base * SequenceScaler.calculateMultiplier(sequence.level, strategy)

    /**
     * Chance that a hostile creature fails to take the Swindler as a target.
     * Seq 8 = 25%, grows slowly (WEAK) with a 40% ceiling.
     */
    fun charmAggroCancelChance(sequence: Sequence): Double =
        scaled(0.25, sequence, ScalingStrategy.WEAK).coerceIn(0.0, 0.40)

    /** Trading discount with villagers: Seq 8 = 10%, ceiling 30%. */
    fun charmTradeDiscount(sequence: Sequence): Double =
        (0.10 + power(sequence) * 0.025).coerceIn(0.0, 0.30)

    /** Duration of aggression suppression (ticks). Seq 8 = 8 s, grows strongly (STRONG). */
    fun eloquenceDurationTicks(sequence: Sequence): Int =
        ticks(scaled(8.0, sequence, ScalingStrategy.STRONG))

    /** Duration of retargeting within the cone (ticks). Seq 8 = 6 s. */
    fun misdirectionDurationTicks(sequence: Sequence): Int =
        ticks(scaled(6.0, sequence, ScalingStrategy.MODERATE))

    /** Duration of the false reality (ticks). Seq 8 = 8 s. */
    fun disruptionDurationTicks(sequence: Sequence): Int =
        ticks(scaled(8.0, sequence, ScalingStrategy.MODERATE))

    /** Sanity loss of a Beyonder victim from Mental Disorder. Seq 8 = 3. */
    fun disruptionSanityLoss(sequence: Sequence): Int =
        scaled(3.0, sequence, ScalingStrategy.WEAK).roundToInt()

    /** Material Theft cooldown (sec): Seq 8 = 40 s, never shorter than 10 s. */
    fun materialTheftCooldownSeconds(sequence: Sequence): Int {
        val seconds = 40.0 / SequenceScaler.calculateMultiplier(sequence.level, ScalingStrategy.MODERATE)
        return max(10L, seconds.roundToLong()).toInt()
    }

    private fun ticks(seconds: Double): Int = (seconds * 20.0).roundToInt()
}
