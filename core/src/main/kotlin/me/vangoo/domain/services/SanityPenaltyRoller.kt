package me.vangoo.domain.services

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.SanityPenalty
import me.vangoo.domain.valueobjects.Sequence
import kotlin.math.max
import kotlin.random.Random

/**
 * The steps of the sanity-loss penalty. Eighty lines of balance that in the plugin lived as
 * private methods inside `Beyonder` and were therefore covered by no test at all: `Random`
 * there was a field of the entity, not an argument.
 */
object SanityPenaltyRoller {

    /**
     * The penalty roll. First the failure chance on the [SanityLoss] scale, then the severity.
     * [random] is passed explicitly so that the steps are tested rather than guessed at.
     */
    fun roll(
        sanityLoss: SanityLoss,
        sequence: Sequence,
        maxSpirituality: Int,
        random: Random = Random.Default,
    ): SanityPenalty {
        if (sanityLoss.isNegligible()) return SanityPenalty.none()
        if (random.nextDouble() >= sanityLoss.calculateFailureChance()) return SanityPenalty.none()
        return severity(sanityLoss, sequence, maxSpirituality, random)
    }

    private fun severity(
        sanityLoss: SanityLoss,
        sequence: Sequence,
        maxSpirituality: Int,
        random: Random,
    ): SanityPenalty {
        val fullLoss = { SequenceScaler.scaleSpiritualityLoss(maxSpirituality, sequence.level) }
        val fullDamage = { SequenceScaler.scaleDamagePenalty(sequence.level) }

        return when {
            // 96-100: death.
            sanityLoss.isExtreme() -> SanityPenalty.extreme()

            // 81-95: the full penalty, a coin flip between spirituality and damage.
            sanityLoss.isCritical() ->
                if (random.nextBoolean()) SanityPenalty.spiritualityLoss(fullLoss())
                else SanityPenalty.damage(fullDamage())

            // 61-80: two thirds of the spirituality loss or the full damage.
            sanityLoss.isSevere() ->
                if (random.nextBoolean()) SanityPenalty.spiritualityLoss(max(5, fullLoss() * 2 / 3))
                else SanityPenalty.damage(fullDamage())

            // 41-60: mostly half damage, occasionally a third of the spirituality loss.
            sanityLoss.isSerious() ->
                if (random.nextDouble() < 0.7) SanityPenalty.damage(max(1, fullDamage() / 2))
                else SanityPenalty.spiritualityLoss(max(3, fullLoss() / 3))

            // 21-40: a scratch, now and then.
            sanityLoss.isModerate() ->
                if (random.nextDouble() < 0.3) SanityPenalty.damage(1) else SanityPenalty.none()

            // 11-20: shaking hands, and nothing more.
            else -> SanityPenalty.none()
        }
    }
}
