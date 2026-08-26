package me.vangoo.domain.services

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.SanityPenalty
import me.vangoo.domain.valueobjects.Sequence
import kotlin.math.max
import kotlin.random.Random

/**
 * Сходинки штрафу за втрату глузду. Вісімдесят рядків балансу, які в плагіні жили
 * приватними методами всередині `Beyonder` і тому не перевірялись жодним тестом:
 * `Random` там був полем сутності, а не аргументом.
 */
object SanityPenaltyRoller {

    /**
     * Кидок на штраф. Спершу шанс провалу за шкалою [SanityLoss], потім — важкість.
     * [random] передається явно, щоб сходинки перевірялись, а не вгадувались.
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
            // 96-100: смерть.
            sanityLoss.isExtreme() -> SanityPenalty.extreme()

            // 81-95: повний штраф, монетка між духовністю і шкодою.
            sanityLoss.isCritical() ->
                if (random.nextBoolean()) SanityPenalty.spiritualityLoss(fullLoss())
                else SanityPenalty.damage(fullDamage())

            // 61-80: дві третини втрати духовності або повна шкода.
            sanityLoss.isSevere() ->
                if (random.nextBoolean()) SanityPenalty.spiritualityLoss(max(5, fullLoss() * 2 / 3))
                else SanityPenalty.damage(fullDamage())

            // 41-60: переважно половинна шкода, іноді третина втрати духовності.
            sanityLoss.isSerious() ->
                if (random.nextDouble() < 0.7) SanityPenalty.damage(max(1, fullDamage() / 2))
                else SanityPenalty.spiritualityLoss(max(3, fullLoss() / 3))

            // 21-40: зрідка подряпина.
            sanityLoss.isModerate() ->
                if (random.nextDouble() < 0.3) SanityPenalty.damage(1) else SanityPenalty.none()

            // 11-20: тремтять руки, і не більше.
            else -> SanityPenalty.none()
        }
    }
}
