package me.vangoo.domain.hud

import kotlin.math.roundToInt

/** Animates only server-confirmed changes; the counter follows the same value as the liquid. */
class SpiritualityDisplay {
    private var start = 0f
    private var target = 0
    private var maximum = 0
    private var elapsed = 20
    private var duration = 20
    private var trail = 0f
    private var trailHold = 0
    private var changeTicks = 0
    var gainTicks = 0
        private set

    val low get() = maximum > 0 && target.toDouble() / maximum < .2
    val full get() = maximum > 0 && target == maximum

    fun tick(current: Int, maximum: Int, instant: Boolean = false, sequence: Int = 9,
             regenerationPerSecond: Int = 1) {
        require(maximum >= 0 && current in 0..maximum)
        require(sequence in 0..9 && regenerationPerSecond > 0)
        elapsed = (elapsed + 1).coerceAtMost(duration)
        if (changeTicks > 0) changeTicks--
        if (gainTicks > 0) gainTicks--
        if (this.maximum != maximum || current != target) changeTicks = 50
        if (this.maximum != maximum || instant) {
            start = current.toFloat()
            target = current
            elapsed = duration
            trail = start
            trailHold = 0
            gainTicks = 0
        } else if (current != target) {
            start = value(0f)
            if (current < target) {
                trail = maxOf(trail, start, target.toFloat())
                trailHold = 10
                duration = (12 - (9 - sequence) * .6f).roundToInt()
                gainTicks = 0
            } else {
                duration = ((current - start) * 20 / regenerationPerSecond).roundToInt().coerceIn(1, 20)
                gainTicks = duration
            }
            target = current
            elapsed = 0
        }
        this.maximum = maximum
        if (trailHold > 0) trailHold-- else trail = maxOf(value(0f), trail - maximum / 20f)
    }

    fun value(partialTick: Float): Float {
        val t = ((elapsed + partialTick.coerceIn(0f, 1f)) / duration).coerceIn(0f, 1f)
        return start + (target - start) * t * t * (3f - 2f * t)
    }

    fun fraction(partialTick: Float): Float = if (maximum > 0) (value(partialTick) / maximum).coerceIn(0f, 1f) else 0f
    fun number(partialTick: Float): Int = value(partialTick).roundToInt().coerceIn(0, maximum)
    fun trailFraction(partialTick: Float): Float = if (maximum > 0) maxOf(trail / maximum, fraction(partialTick)).coerceIn(0f, 1f) else 0f

    fun showNumber(mode: SpiritualityNumbers, inventoryOrChat: Boolean): Boolean = when (mode) {
        SpiritualityNumbers.ON_CHANGE -> changeTicks > 0 || inventoryOrChat
        SpiritualityNumbers.ALWAYS -> true
        SpiritualityNumbers.NEVER -> false
    }

    fun reset() {
        maximum = 0
        changeTicks = 0
        gainTicks = 0
    }
}
