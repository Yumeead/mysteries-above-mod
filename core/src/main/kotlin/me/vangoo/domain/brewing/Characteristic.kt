package me.vangoo.domain.brewing

/** A crystalline essence of power, keyed by pathway + sequence. */
data class Characteristic(val pathwayName: String, val sequence: Int) {
    /** The canonical ingredient key of the Characteristic. */
    fun itemKey(): String = characteristicKey(pathwayName, sequence)
}

/** The canonical Characteristic key — the only place its format lives. */
internal fun characteristicKey(pathwayName: String, sequence: Int): String =
    "characteristic:$pathwayName:$sequence"
