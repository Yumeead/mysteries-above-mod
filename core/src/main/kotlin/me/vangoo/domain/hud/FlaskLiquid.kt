package me.vangoo.domain.hud

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ParticleFlow { SPIRAL, DRIFT, RISE, FALL, ORBIT, STEP }

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
}
