package me.vangoo.domain.valueobjects

data class SanityPenalty(val type: PenaltyType, val amount: Int) {

    enum class PenaltyType {
        NONE,              // No penalty
        DAMAGE,            // Physical damage
        SPIRITUALITY_LOSS, // Spirituality deduction
        EXTREME,           // Death + warden spawn
    }

    fun hasEffect(): Boolean = type != PenaltyType.NONE

    companion object {
        fun none(): SanityPenalty = SanityPenalty(PenaltyType.NONE, 0)

        fun damage(amount: Int): SanityPenalty = SanityPenalty(PenaltyType.DAMAGE, amount)

        fun spiritualityLoss(amount: Int): SanityPenalty =
            SanityPenalty(PenaltyType.SPIRITUALITY_LOSS, amount)

        fun extreme(): SanityPenalty = SanityPenalty(PenaltyType.EXTREME, 0)
    }
}
