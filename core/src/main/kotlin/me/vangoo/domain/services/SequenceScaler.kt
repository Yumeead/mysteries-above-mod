package me.vangoo.domain.services

import kotlin.math.max
import kotlin.math.min

object SequenceScaler {

    enum class ScalingStrategy(val percentPerLevel: Double) {

        WEAK(5.0),      // +5% per level (Seq 0: +45%)
        MODERATE(15.0), // +15% per level (Seq 0: +135%)
        STRONG(30.0),   // +30% per level (Seq 0: +270%)
        DIVINE(60.0),   // +60% per level (Seq 0: +540% - true might)

        // Penalties (High Risk): the penalty gets much harsher for low sequences
        PENALTY_LINEAR(20.0),   // +20% to the penalty per level of power
        PENALTY_SEVERE(50.0),   // +50% to the penalty per level of power
        PENALTY_EXTREME(100.0); // The penalty doubles per level of power (extreme risk)
    }

    fun calculateMultiplier(sequence: Int, strategy: ScalingStrategy): Double {
        val powerLevel = 9 - sequence // Seq 9 = 0, Seq 0 = 9
        return 1.0 + powerLevel * strategy.percentPerLevel / 100.0
    }

    fun scaleSpiritualityLoss(maxSpirituality: Int, sequence: Int): Int {
        val power = 9 - sequence

        // Seq 9: loses ~5% of the maximum
        // Seq 0: loses ~50% of the maximum (catastrophic)
        val basePercent = 0.05
        val growthPerLevel = 0.05 // +5% to the total pool per sequence

        val totalPercent = basePercent + power * growthPerLevel
        val loss = (maxSpirituality * totalPercent).toInt()

        return max(10, loss)
    }

    fun scaleDamagePenalty(sequence: Int): Int {
        val power = 9 - sequence

        // Base damage: 2 (one heart)
        // Add the progression: Seq 9 = 2, Seq 5 = 10, Seq 0 = 20 (ten hearts!)
        val baseDamage = 2
        var escalation = power * 2

        // For demigods (Seq 4-0) add an extra "divine kickback"
        if (sequence <= 4) {
            escalation += (5 - sequence) * 3
        }

        return baseDamage + escalation
    }

    /**
     * Scaling of PROTECTION against Beyonder creatures (WEAK): the stronger the Beyonder (the
     * lower the sequence), the less damage gets through. Seq 9 → 0%, Seq 5 → 20%, Seq 0 → 45%
     * (the ceiling).
     *
     * @return the damage reduction share in the range [0.0, 0.45]
     */
    fun creatureDamageReduction(sequenceLevel: Int): Double {
        val power = 9 - sequenceLevel // Seq 9 = 0, Seq 0 = 9
        val reduction = power * (ScalingStrategy.WEAK.percentPerLevel / 100.0)
        return max(0.0, min(0.45, reduction))
    }

    fun getSequencePower(sequence: Int): Int = 9 - sequence
}
