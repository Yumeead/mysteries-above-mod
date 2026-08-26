package me.vangoo.domain.brewing

/** Кристалічна есенція сили, ключована шляхом+послідовністю. */
data class Characteristic(val pathwayName: String, val sequence: Int) {
    /** Канонічний ключ-інгредієнт Характеристики. */
    fun itemKey(): String = characteristicKey(pathwayName, sequence)
}

/** Канонічний ключ Характеристики — єдине місце, де живе його формат. */
internal fun characteristicKey(pathwayName: String, sequence: Int): String =
    "characteristic:$pathwayName:$sequence"
