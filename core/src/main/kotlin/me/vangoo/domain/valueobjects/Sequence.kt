package me.vangoo.domain.valueobjects

data class Sequence(val level: Int) {

    init {
        require(level in MIN_SEQUENCE..MAX_SEQUENCE) {
            "Sequence must be between $MIN_SEQUENCE and $MAX_SEQUENCE, got: $level"
        }
    }

    fun advance(): Sequence {
        check(canAdvance()) { "Cannot advance from sequence $level" }
        return Sequence(level - 1)
    }

    fun canAdvance(): Boolean = level > MIN_SEQUENCE

    /** Слабша послідовність (число росте); Посл. 9 — межа, слабшати нікуди. */
    fun demote(): Sequence {
        check(canDemote()) { "Cannot demote from sequence $level" }
        return Sequence(level + 1)
    }

    fun canDemote(): Boolean = level < MAX_SEQUENCE

    fun isStarter(): Boolean = level == MAX_SEQUENCE

    fun isHighest(): Boolean = level == MIN_SEQUENCE

    fun getAllSequencesUpToCurrent(): IntArray =
        IntArray(MAX_SEQUENCE - level + 1) { MAX_SEQUENCE - it }

    override fun toString(): String = "Sequence $level"

    companion object {
        private const val MIN_SEQUENCE = 0
        private const val MAX_SEQUENCE = 9

        fun of(level: Int): Sequence = Sequence(level)

        fun starter(): Sequence = Sequence(MAX_SEQUENCE)
    }
}
