package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Радіус «Бачення ниток» (Seq 5 Маріонетника шляху Блазня): нитки над головами
 * істот + виявлення невидимих сущностей поблизу. База 24 блоки, росте зі силою.
 * Обмежений заради продуктивності. Чиста математика.
 */
object ThreadVisionRange {

    private const val BASE_RANGE = 24
    private const val MAX_RANGE = 40

    fun rangeFor(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(MAX_RANGE, (BASE_RANGE * multiplier).roundToInt())
    }
}
