package me.vangoo.mysteries.core

/**
 * Послідовність Потойбічного: 9 — найслабша, 0 — найсильніша.
 *
 * Перший мешканець модуля core. Тут немає й не буде жодного імпорту Minecraft:
 * модуль збирається без гри, тож будь-яке протікання платформи ламає збірку.
 */
@JvmInline
value class Sequence(val level: Int) : Comparable<Sequence> {

    init {
        require(level in STRONGEST_LEVEL..WEAKEST_LEVEL) {
            "Sequence must be within $STRONGEST_LEVEL..$WEAKEST_LEVEL, got $level"
        }
    }

    /** Наступний щабель угору; null — якщо вже 0. */
    fun advanced(): Sequence? = if (level == STRONGEST_LEVEL) null else Sequence(level - 1)

    /** Сильніша послідовність має МЕНШИЙ номер, тож порядок обернений. */
    override fun compareTo(other: Sequence): Int = other.level - level

    override fun toString(): String = "Sequence $level"

    companion object {
        const val STRONGEST_LEVEL = 0
        const val WEAKEST_LEVEL = 9

        val STRONGEST = Sequence(STRONGEST_LEVEL)
        val WEAKEST = Sequence(WEAKEST_LEVEL)
    }
}
