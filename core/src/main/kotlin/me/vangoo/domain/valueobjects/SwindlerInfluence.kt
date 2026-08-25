package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 8 шляху Помилки (Аферист).
 *
 * Одне джерело правди для всіх семи здібностей Seq 8: чарівність, красномовність,
 * підміна думок, ментальний розлад, вища спостережливість і крадіжка матеріалів.
 * Чиста математика — без платформи; ефекти живуть у `pathways.error.abilities`.
 */
object SwindlerInfluence {

    // --- Вартість духовності та базові кулдауни (сек) ---
    const val ELOQUENCE_COST = 35
    const val ELOQUENCE_COOLDOWN_SECONDS = 20
    const val MISDIRECTION_COST = 30
    const val MISDIRECTION_COOLDOWN_SECONDS = 30
    const val DISRUPTION_COST = 45
    const val DISRUPTION_COOLDOWN_SECONDS = 60
    const val MATERIAL_THEFT_COST = 60

    // --- Дальності (блоки) ---
    const val CHARM_AGGRO_RANGE = 16.0
    const val ELOQUENCE_RANGE = 12.0
    const val MISDIRECTION_CONE_RANGE = 10.0
    const val MISDIRECTION_CONE_ANGLE_DEGREES = 90.0
    const val DISRUPTION_RANGE = 15.0
    const val OBSERVATION_GAZE_RANGE = 15.0

    /** Радіус чуття цінностей; вікі розширює його до 50 лише на Прометеї (Seq 6). */
    const val OBSERVATION_VALUABLES_RADIUS = 10.0
    const val MATERIAL_THEFT_RANGE = 5.0

    /** Шанс гравця-жертви отримати перемішаний хотбар від Підміни думок. */
    const val MISDIRECTION_SCRAMBLE_CHANCE = 0.70

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = SequenceScaler.getSequencePower(sequence.level)

    private fun scaled(base: Double, sequence: Sequence, strategy: ScalingStrategy): Double =
        base * SequenceScaler.calculateMultiplier(sequence.level, strategy)

    /**
     * Шанс, що вороже створіння не зможе взяти Афериста на приціл.
     * Seq 8 = 25%, зростає повільно (WEAK) зі стелею 40%.
     */
    fun charmAggroCancelChance(sequence: Sequence): Double =
        scaled(0.25, sequence, ScalingStrategy.WEAK).coerceIn(0.0, 0.40)

    /** Знижка в торгівлі з селянами: Seq 8 = 10%, стеля 30%. */
    fun charmTradeDiscount(sequence: Sequence): Double =
        (0.10 + power(sequence) * 0.025).coerceIn(0.0, 0.30)

    /** Тривалість придушення агресії (тіки). Seq 8 = 8 с, росте сильно (STRONG). */
    fun eloquenceDurationTicks(sequence: Sequence): Int =
        ticks(scaled(8.0, sequence, ScalingStrategy.STRONG))

    /** Тривалість перенацілювання в конусі (тіки). Seq 8 = 6 с. */
    fun misdirectionDurationTicks(sequence: Sequence): Int =
        ticks(scaled(6.0, sequence, ScalingStrategy.MODERATE))

    /** Тривалість фальшивої реальності (тіки). Seq 8 = 8 с. */
    fun disruptionDurationTicks(sequence: Sequence): Int =
        ticks(scaled(8.0, sequence, ScalingStrategy.MODERATE))

    /** Втрата глузду жертви-Beyonder'а від Ментального розладу. Seq 8 = 3. */
    fun disruptionSanityLoss(sequence: Sequence): Int =
        scaled(3.0, sequence, ScalingStrategy.WEAK).roundToInt()

    /** Кулдаун Крадіжки матеріалів (сек): Seq 8 = 40 с, не коротше 10 с. */
    fun materialTheftCooldownSeconds(sequence: Sequence): Int {
        val seconds = 40.0 / SequenceScaler.calculateMultiplier(sequence.level, ScalingStrategy.MODERATE)
        return max(10L, seconds.roundToLong()).toInt()
    }

    private fun ticks(seconds: Double): Int = (seconds * 20.0).roundToInt()
}
