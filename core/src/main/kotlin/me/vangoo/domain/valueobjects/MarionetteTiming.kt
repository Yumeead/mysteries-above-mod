package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.roundToInt

/**
 * Тайминги фаз Контролю Маріонетки (Seq 5 Маріонетника шляху Блазня).
 *
 * Фаза 1 (фіксація ниток) — 5…20с залежно від засвоєння кастера. Фаза 2
 * (конверсія) — для гравця база 5 хв, сильно скейлиться з силою; для моба ~15с.
 * Чиста математика — без платформи.
 */
object MarionetteTiming {

    private const val PHASE1_MAX_SECONDS = 20.0
    private const val PHASE1_MIN_SECONDS = 5.0
    private const val PLAYER_CONVERT_BASE_TICKS = 6000 // 5 хв на Seq 9
    private const val MOB_SWAP_BASE_TICKS = 300        // 15с на Seq 9

    /** Поріг разового урону по жертві, що збиває фіксацію. */
    const val BREAK_DAMAGE_THRESHOLD = 6.0

    /**
     * Тривалість фази фіксації в тіках за засвоєнням (0…100): 20с при 0%,
     * лінійно до 5с при 100%.
     */
    fun phase1LockTicks(masteryPercent: Double): Int {
        val clamped = masteryPercent.coerceIn(0.0, 100.0)
        val seconds = PHASE1_MAX_SECONDS -
            (PHASE1_MAX_SECONDS - PHASE1_MIN_SECONDS) * (clamped / 100.0)
        return (seconds * 20).roundToInt()
    }

    /** Тривалість фази конверсії гравця (тіки): база 5 хв, швидшає зі силою (STRONG). */
    fun playerConvertTicks(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.STRONG,
        )
        return (PLAYER_CONVERT_BASE_TICKS / multiplier).roundToInt()
    }

    /** Тривалість фази перетворення моба (тіки): ~15с, швидшає зі силою (WEAK). */
    fun mobSwapTicks(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return (MOB_SWAP_BASE_TICKS / multiplier).roundToInt()
    }
}
