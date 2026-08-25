package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Балансні числа Послідовності 7 шляху Помилки (Криптолог).
 *
 * Одне джерело правди для Дешифрування та для тіру доказів у Вищій спостережливості.
 * Чиста математика — без платформи; ефекти живуть у `pathways.error.abilities`.
 *
 * Ключове правило вікі: Дешифрування — не ворожіння. Глибина відповіді залежить від
 * того, скільки слідів реально існує ([depthFor]), а не від удачі каста.
 */
object CryptologistInsight {

    /** Послідовність, на якій здібність з'являється; усі базові числа задані для неї. */
    private const val BASE_SEQUENCE = 7

    // --- Вартість і дальності ---
    const val DECRYPTION_COST = 110
    const val DECRYPTION_COOLDOWN_SECONDS = 45
    private const val DECRYPTION_COOLDOWN_FLOOR_SECONDS = 20L

    /** Дальність пошуку цілі/блока в прицілі (режими A і B). */
    const val CROSSHAIR_RANGE = 20.0

    /** Радіус сканування містицизму (режим D). */
    const val ZONE_RADIUS = 24.0

    /** Радіус і вікно (сек) запиту історії CoreProtect. */
    const val TRACE_LOOKUP_RADIUS = 8
    const val TRACE_LOOKUP_WINDOW_SECONDS = 600

    /** Пасивне маркування доказів (Seq 7 тір Вищої спостережливості). */
    const val EVIDENCE_MARK_RADIUS = 8.0
    const val EVIDENCE_MARK_INTERVAL_SECONDS = 15

    /** Тривалість замовляння з режиму C (сек). */
    const val INCANTATION_DURATION_SECONDS = 180

    /** Скільки слідів потрібно для глибини 1/2/3 на Seq 7. */
    private val DEPTH_BASE_THRESHOLDS = intArrayOf(2, 5, 9)

    /** Втрата глузду за глибину 1/2/3 на Seq 7. */
    private val DEPTH_BASE_SANITY = intArrayOf(0, 2, 5)

    /** Кулдаун (сек): Seq 7 = 45, коротшає MODERATE, не нижче підлоги. */
    fun decryptionCooldownSeconds(sequence: Sequence): Int {
        val seconds = DECRYPTION_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE)
        return max(DECRYPTION_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /**
     * Скільки слідів треба, щоб дістати глибину [depth] (1..3).
     * Пороги падають, коли Криптолог сильнішає.
     */
    fun depthThreshold(depth: Int, sequence: Sequence): Int {
        requireDepth(depth)
        val base = DEPTH_BASE_THRESHOLDS[depth - 1]
        val easier = max(
            0,
            SequenceScaler.getSequencePower(sequence.level) -
                SequenceScaler.getSequencePower(BASE_SEQUENCE),
        )
        return max(depth, base - (easier * base * 0.06).roundToInt())
    }

    /** Глибина відповіді за наявними доказами: 0 = нічого не вичитати, 3 = вся правда. */
    fun depthFor(evidenceCount: Int, sequence: Sequence): Int {
        for (depth in 3 downTo 1) {
            if (evidenceCount >= depthThreshold(depth, sequence)) {
                return depth
            }
        }
        return 0
    }

    /** Втрата глузду за глибоке читання: d1 = 0, d2 = 2, d3 = 5 на Seq 7, росте WEAK. */
    fun sanityLoss(depth: Int, sequence: Sequence): Int {
        if (depth <= 0) {
            return 0
        }
        requireDepth(depth)
        val base = DEPTH_BASE_SANITY[depth - 1].toDouble()
        return (base / relative(sequence, ScalingStrategy.WEAK)).roundToInt()
    }

    /** Множник шкоди від замовляння (режим C, глибина 3): Seq 7 = 1.20, росте WEAK. */
    fun incantationDamageMultiplier(sequence: Sequence): Double =
        1.0 + 0.20 / relative(sequence, ScalingStrategy.WEAK)

    /** Відношення сили на Seq 7 до сили на заданій Послідовності (менше 1.0 для сильніших). */
    private fun relative(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy) /
            SequenceScaler.calculateMultiplier(sequence.level, strategy)

    private fun requireDepth(depth: Int) {
        require(depth in 1..3) { "Depth must be 1..3, got: $depth" }
    }
}
