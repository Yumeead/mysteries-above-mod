package me.vangoo.domain.valueobjects

import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Засвоєння зілля, 0..100 %.
 *
 * НЕ data class навмисно: конструктор округлює вхідне значення до 2 знаків, а data-клас
 * дав би публічний `copy()`, який пройшов би повз округлення. Тут краще дописати три
 * методи вручну, ніж лишити дірку в інваріанті.
 */
class Mastery(raw: Double) {

    val value: Double = (raw * 100.0).roundToLong() / 100.0

    init {
        require(value in MIN_VALUE..MAX_VALUE) {
            "Mastery must be between $MIN_VALUE and $MAX_VALUE, got: $value"
        }
    }

    fun add(amount: Double): Mastery {
        require(amount >= 0) { "Cannot increment by negative amount: $amount" }
        return Mastery(min(value + amount, MAX_VALUE))
    }

    fun increment(): Mastery = add(1.0)

    fun reset(): Mastery = zero()

    fun canAdvance(): Boolean = value >= MAX_VALUE

    fun getPercentage(): Double = value

    fun asInt(): Int = value.toInt()

    override fun equals(other: Any?): Boolean = other is Mastery && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "%.2f%%".format(value)

    companion object {
        private const val MIN_VALUE = 0.0
        private const val MAX_VALUE = 100.0

        fun of(value: Double): Mastery = Mastery(value)

        fun zero(): Mastery = Mastery(MIN_VALUE)

        fun max(): Mastery = Mastery(MAX_VALUE)
    }
}
