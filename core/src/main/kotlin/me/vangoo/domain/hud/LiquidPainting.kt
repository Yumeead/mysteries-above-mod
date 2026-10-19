package me.vangoo.domain.hud

import kotlin.math.roundToInt

/** Original ARGB pixels at an artist-authored fill level. Zero means dry glass. */
data class LiquidPainting(val level: Float, val surface: Int, val pixels: List<IntArray>) {
    val paintedBottom = pixels.indexOfLast { row -> row.any { it ushr 24 > 0 } }
}

class LiquidPaintingBlend(val lower: LiquidPainting, val upper: LiquidPainting, val mix: Float, val surface: Int) {
    fun color(x: Int, y: Int, bottom: Int): Int {
        if (y < surface || y > bottom) return 0
        fun sample(painting: LiquidPainting): Int {
            val row = if (surface == bottom) painting.paintedBottom else painting.surface +
                ((y - surface).toFloat() * (bottom - painting.surface) / (bottom - surface)).roundToInt()
            return painting.pixels.getOrNull(row)?.getOrNull(x) ?: 0
        }
        val a = sample(lower)
        if (mix == 0f || lower === upper) return a
        val b = sample(upper)
        if (mix == 1f) return b
        if (a == b) return a
        val alphaA = a ushr 24
        val alphaB = b ushr 24
        val weightA = alphaA * (1f - mix)
        val weightB = alphaB * mix
        val alpha = weightA + weightB
        if (alpha < .5f) return 0
        var result = alpha.roundToInt() shl 24
        for (shift in 16 downTo 0 step 8) {
            val channel = (((a shr shift and 255) * weightA + (b shr shift and 255) * weightB) / alpha).roundToInt()
            result = result or (channel shl shift)
        }
        return result
    }

    companion object {
        fun at(paintings: List<LiquidPainting>, level: Float, bottom: Int): LiquidPaintingBlend? {
            require(level.isFinite())
            if (level <= 0f || paintings.isEmpty()) return null
            // Integer spirituality can land a fraction of one point away from an illustrated level.
            paintings.firstOrNull { kotlin.math.abs(it.level - level) <= .00005f }?.let {
                return LiquidPaintingBlend(it, it, 0f, it.surface)
            }
            val upper = paintings.firstOrNull { it.level >= level } ?: paintings.last()
            val lower = paintings.lastOrNull { it.level < level } ?: paintings.first()
            if (level < paintings.first().level) {
                val surface = (bottom - (bottom - upper.surface) * level / upper.level).roundToInt()
                return LiquidPaintingBlend(upper, upper, 0f, surface)
            }
            val mix = if (lower === upper) 0f else ((level - lower.level) / (upper.level - lower.level)).coerceIn(0f, 1f)
            val surface = (lower.surface + (upper.surface - lower.surface) * mix).roundToInt()
            return LiquidPaintingBlend(lower, upper, mix, surface)
        }
    }
}
