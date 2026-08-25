package me.vangoo.domain.valueobjects

import kotlin.math.max
import kotlin.math.min

data class SanityLoss(val scale: Int) {

    init {
        require(scale in MIN_VALUE..MAX_VALUE) {
            "Sanity loss scale must be between $MIN_VALUE and $MAX_VALUE, got: $scale"
        }
    }

    fun increase(amount: Int): SanityLoss {
        require(amount >= 0) { "Cannot increase by negative amount: $amount" }
        return SanityLoss(min(scale + amount, MAX_VALUE))
    }

    fun decrease(amount: Int): SanityLoss {
        require(amount >= 0) { "Cannot decrease by negative amount: $amount" }
        return SanityLoss(max(scale - amount, MIN_VALUE))
    }

    fun reset(): SanityLoss = none()

    /**
     * Calculate failure chance based on sanity loss scale.
     * Updated with more balanced progression.
     */
    fun calculateFailureChance(): Double = when {
        scale <= 10 -> 0.0   // 0-10: No failure (Negligible)
        scale <= 20 -> 0.05  // 11-20: 5% (Minor)
        scale <= 40 -> 0.15  // 21-40: 15% (Moderate)
        scale <= 60 -> 0.35  // 41-60: 35% (Serious)
        scale <= 80 -> 0.60  // 61-80: 60% (Severe)
        scale <= 95 -> 0.85  // 81-95: 85% (Critical)
        else -> 1.0          // 96-100: 100% (Extreme - always fails)
    }

    fun isNegligible(): Boolean = scale <= 10

    fun isMinor(): Boolean = scale in 11..20

    fun isModerate(): Boolean = scale in 21..40

    fun isSerious(): Boolean = scale in 41..60

    fun isSevere(): Boolean = scale in 61..80

    fun isCritical(): Boolean = scale in 81..95

    fun isExtreme(): Boolean = scale > 95

    override fun toString(): String = "$scale/100"

    companion object {
        private const val MIN_VALUE = 0
        private const val MAX_VALUE = 100

        fun of(scale: Int): SanityLoss = SanityLoss(scale)

        fun none(): SanityLoss = SanityLoss(MIN_VALUE)

        fun maximum(): SanityLoss = SanityLoss(MAX_VALUE)
    }
}
