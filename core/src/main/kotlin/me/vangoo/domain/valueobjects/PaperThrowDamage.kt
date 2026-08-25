package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.ceil

/**
 * Балансні числа пасивного кидка звичайного паперу (Seq 8 Клоуна шляху Блазня).
 *
 * Кидок нічого не коштує, має короткий кулдаун і завдає шкоди, що зростає
 * зі силою Beyonder'а. Чиста математика — без платформи; ефект кидка живе в
 * `pathways.fool.abilities.PaperCutter` + `PaperThrowListener`.
 */
object PaperThrowDamage {

    /** Базова шкода кинджала на Seq 9 (до скейлу). */
    const val BASE_DAMAGE = 6

    /** Кулдаун між кидками — 0.5с (10 тіків). */
    const val THROW_COOLDOWN_TICKS = 10

    /** Шкода одного паперового снаряда за поточною послідовністю. */
    fun damageFor(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.MODERATE,
        )
        return ceil(BASE_DAMAGE * multiplier).toInt()
    }
}
