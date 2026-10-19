package me.vangoo.domain.creatures

/**
 * The instinct a Sequence carries: a weaker Beyonder — man or beast — feels the higher one before
 * it sees them, and the gap decides who runs.
 *
 * The plugin's creatures charged a Sequence 5 player exactly as they charged a mortal. Here the
 * same pressure that makes Retinue servants rebel near a stronger Death Beyonder drives every
 * creature with a Sequence: it will not start a fight it is sure to lose, and a predatory one
 * picks the weak.
 *
 * A `null` Sequence is someone with no characteristic at all — an ordinary person, a creature of
 * the Spirit World that has no place on a pathway. Pressure works only between two Sequences.
 */
object SequencePressure {

    /** Two Sequences apart is where the instinct speaks; one apart is a fair fight. */
    const val GAP = 2

    /** [own] dreads [other]: the other stands at least [GAP] Sequences higher (a smaller number). */
    fun dreads(own: Int?, other: Int?): Boolean = own != null && other != null && own - other >= GAP

    /** [other] is prey to [own]: at least [GAP] Sequences below it. */
    fun preysOn(own: Int?, other: Int?): Boolean = own != null && other != null && other - own >= GAP
}
