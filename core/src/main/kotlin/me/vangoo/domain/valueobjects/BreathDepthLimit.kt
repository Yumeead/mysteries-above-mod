package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Гранична глибина «вічного» підводного дихання (Seq 7 Фокусника шляху Блазня).
 *
 * Потойбічний дихає під водою скільки завгодно, ПОКИ не занурився глибше
 * порогу від поверхні води (база 5 блоків, зростає зі силою). Глибше — кисень
 * витрачається як звичайно. Чиста математика — без платформи.
 */
object BreathDepthLimit {

    const val BASE_DEPTH = 5
    private const val MAX_DEPTH = 12

    /** Максимальна глибина (у блоках від поверхні), на якій дихання ще діє. */
    fun maxDepth(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(MAX_DEPTH, (BASE_DEPTH * multiplier).roundToInt())
    }
}
