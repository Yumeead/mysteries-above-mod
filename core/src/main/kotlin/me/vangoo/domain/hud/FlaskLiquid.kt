package me.vangoo.domain.hud

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ParticleFlow { SPIRAL, DRIFT, RISE, FALL, ORBIT, STEP }

data class LiquidHighlight(val x: Int, val y: Int, val opacity: Float)

/** Pixel geometry and motion have no dependency on the game or its renderer. */
object FlaskLiquid {
    fun filledRows(height: Int, fraction: Float): Int {
        require(height > 0 && fraction.isFinite())
        if (fraction <= 0f) return 0
        return (height * fraction.coerceAtMost(1f)).roundToInt().coerceIn(1, height)
    }

    fun shade(x: Int, row: Int, surface: Int, bottom: Int, time: Float, colors: Int): Int {
        require(colors >= 2)
        val height = (bottom - surface + 1).coerceAtLeast(1)
        val lift = (bottom - row).toFloat() / height
        val core = (1f - kotlin.math.abs(x - 31) / 18f).coerceAtLeast(0f) * .5f
        val drift = sin(time / 65f + x * .21f + row * .13f) * .18f
        return (lift * (colors - 2) + core + drift).roundToInt().coerceIn(0, colors - 1)
    }

    fun particle(flow: ParticleFlow, phase: Float, index: Int): Pair<Float, Float> {
        val t = ((phase + index * .173f) % 1f + 1f) % 1f
        val angle = t * (2 * Math.PI).toFloat()
        val seed = ((index * 17 + 7) % 29) / 28f
        return when (flow) {
            ParticleFlow.SPIRAL -> (.5f + sin(angle + index) * .3f) to (1f - t)
            ParticleFlow.DRIFT -> (seed * .7f + .15f + sin(angle) * .08f) to (1f - t)
            ParticleFlow.RISE -> seed to (1f - t)
            ParticleFlow.FALL -> seed to t
            ParticleFlow.ORBIT -> (.5f + cos(angle) * .3f) to (.5f + sin(angle) * .3f)
            ParticleFlow.STEP -> (seed + sin(angle).roundToInt() * .06f).coerceIn(0f, 1f) to (1f - t)
        }
    }

    fun highlights(rows: List<List<IntRange>>, painting: LiquidPaintingBlend?, bottom: Int,
                   time: Float, periodTicks: Int, count: Int, flow: ParticleFlow): List<LiquidHighlight> {
        if (painting == null || bottom - painting.surface < 2 || count <= 0) return emptyList()
        val surface = painting.surface
        return buildList {
            fun pixel(x: Int, y: Int, opacity: Float) {
                if (opacity > 0f && rows.getOrNull(y)?.any { x in it } == true &&
                    painting.color(x, y, bottom) ushr 24 > 0)
                    add(LiquidHighlight(x, y, opacity))
            }
            fun stamp(u: Float, v: Float, opacity: Float, bubble: Boolean) {
                val y = (surface + 1 + v * (bottom - surface - 2)).roundToInt()
                val span = rows.getOrNull(y)?.firstOrNull() ?: return
                val x = (span.first + 1 + u * (span.last - span.first - 2).coerceAtLeast(0)).roundToInt()
                if (bubble && bottom - surface >= 5) {
                    pixel(x, y - 1, opacity)
                    pixel(x - 1, y, opacity * .65f)
                    pixel(x + 1, y, opacity * .5f)
                    pixel(x, y + 1, opacity * .35f)
                } else pixel(x, y, opacity)
            }
            for (i in 0 until count) {
                val (u, v) = particle(ParticleFlow.RISE, time / (periodTicks * (.85f + i * .13f)), i)
                val fade = minOf(1f, v * 6f, (1f - v) * 6f)
                stamp(.1f + u * .8f + sin(time / 13f + i) * .04f, v, .66f * fade, i % 2 == 0)
            }
            for (i in 0..1) for (tail in 0..3) {
                val (u, v) = particle(flow, (time - tail * 5) / (periodTicks * 1.4f), i + 9)
                stamp(u, v, .26f - tail * .06f, false)
            }
        }
    }
}
