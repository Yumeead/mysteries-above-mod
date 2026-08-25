package me.vangoo.domain.valueobjects

import kotlin.math.max
import kotlin.math.min

data class Spirituality(val current: Int, val maximum: Int) {

    init {
        require(maximum >= MIN_VALUE) { "Maximum spirituality cannot be negative: $maximum" }
        require(current >= MIN_VALUE) { "Current spirituality cannot be negative: $current" }
        require(current <= maximum) {
            "Current spirituality ($current) cannot exceed maximum ($maximum)"
        }
    }

    fun hasSufficient(required: Int): Boolean = current >= required

    fun isCritical(): Boolean {
        if (maximum == 0) return false
        return current.toDouble() / maximum <= CRITICAL_THRESHOLD
    }

    fun increment(amount: Int): Spirituality {
        require(amount >= 0) { "Cannot increment by negative amount: $amount" }
        return Spirituality(min(current + amount, maximum), maximum)
    }

    fun decrement(amount: Int): Spirituality {
        require(amount >= 0) { "Cannot decrement by negative amount: $amount" }
        return Spirituality(max(current - amount, MIN_VALUE), maximum)
    }

    fun withNewMaximum(newMaximum: Int): Spirituality =
        Spirituality(min(current, newMaximum), newMaximum)

    fun regenerate(amount: Int): Spirituality = increment(amount)

    fun isFull(): Boolean = current == maximum

    fun isEmpty(): Boolean = current == MIN_VALUE

    fun getPercentage(): Double {
        if (maximum == 0) return 0.0
        return current.toDouble() / maximum
    }

    override fun toString(): String = "$current/$maximum"

    companion object {
        private const val MIN_VALUE = 0
        private const val CRITICAL_THRESHOLD = 0.05

        fun of(current: Int, maximum: Int): Spirituality = Spirituality(current, maximum)

        fun empty(maximum: Int): Spirituality = Spirituality(MIN_VALUE, maximum)

        fun full(maximum: Int): Spirituality = Spirituality(maximum, maximum)
    }
}
