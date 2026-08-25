package me.vangoo.domain.services

import kotlin.math.max
import kotlin.math.min

object SequenceScaler {

    enum class ScalingStrategy(val percentPerLevel: Double) {

        WEAK(5.0),      // +5% за рівень (Seq 0: +45%)
        MODERATE(15.0), // +15% за рівень (Seq 0: +135%)
        STRONG(30.0),   // +30% за рівень (Seq 0: +270%)
        DIVINE(60.0),   // +60% за рівень (Seq 0: +540% - справжня потужність)

        // Штрафи (High Risk): Штраф стає набагато жорсткішим для низьких послідовностей
        PENALTY_LINEAR(20.0),   // +20% до штрафу за кожен рівень сили
        PENALTY_SEVERE(50.0),   // +50% до штрафу за кожен рівень сили
        PENALTY_EXTREME(100.0); // Штраф подвоюється за кожен рівень сили (екстремальний ризик)
    }

    fun calculateMultiplier(sequence: Int, strategy: ScalingStrategy): Double {
        val powerLevel = 9 - sequence // Seq 9 = 0, Seq 0 = 9
        return 1.0 + powerLevel * strategy.percentPerLevel / 100.0
    }

    fun scaleSpiritualityLoss(maxSpirituality: Int, sequence: Int): Int {
        val power = 9 - sequence

        // Seq 9: втрачає ~5% від максимуму
        // Seq 0: втрачає ~50% від максимуму (катастрофічно)
        val basePercent = 0.05
        val growthPerLevel = 0.05 // +5% до загального обсягу за кожну послідовність

        val totalPercent = basePercent + power * growthPerLevel
        val loss = (maxSpirituality * totalPercent).toInt()

        return max(10, loss)
    }

    fun scaleDamagePenalty(sequence: Int): Int {
        val power = 9 - sequence

        // Базова шкода: 2 (1 серце)
        // Додаємо прогресію: Seq 9 = 2, Seq 5 = 10, Seq 0 = 20 (10 сердець!)
        val baseDamage = 2
        var escalation = power * 2

        // Для напівбогів (Seq 4-0) додаємо додатковий "божественний відкат"
        if (sequence <= 4) {
            escalation += (5 - sequence) * 3
        }

        return baseDamage + escalation
    }

    /**
     * Скейлінг ЗАХИСТУ від потойбічних істот (WEAK): чим сильніший Beyonder (нижчий sequence),
     * тим менше урону по ньому проходить. Seq 9 → 0%, Seq 5 → 20%, Seq 0 → 45% (стеля).
     *
     * @return частка зниження урону в діапазоні [0.0, 0.45]
     */
    fun creatureDamageReduction(sequenceLevel: Int): Double {
        val power = 9 - sequenceLevel // Seq 9 = 0, Seq 0 = 9
        val reduction = power * (ScalingStrategy.WEAK.percentPerLevel / 100.0)
        return max(0.0, min(0.45, reduction))
    }

    fun getSequencePower(sequence: Int): Int = 9 - sequence
}
