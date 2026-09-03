package me.vangoo.domain.valueobjects

import kotlin.math.floor
import kotlin.math.min

/**
 * Balance numbers for the paper substitute dolls (Seq 7 Magician of the Fool pathway).
 *
 * A cast creates a batch of dolls (3 by default, up to 6 with power), spending paper.
 * Dolls accumulate up to a ceiling and absorb heavy blows. Pure math.
 */
object DollBatch {

    /** How much paper one doll in a batch costs. */
    const val PAPER_PER_DOLL = 3

    /** The "heavy blow" threshold a doll absorbs (HP). */
    const val DAMAGE_THRESHOLD = 4.0

    /** Radius of the random teleport after an absorption (blocks). */
    const val TELEPORT_RADIUS = 6

    private const val BASE_DOLLS = 3
    private const val MAX_DOLLS_PER_CAST = 6

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /** How many dolls one cast creates at the current Sequence (3…6). */
    fun dollsPerCast(sequence: Sequence): Int {
        val dolls = BASE_DOLLS + floor(power(sequence) * 0.75).toInt()
        return min(MAX_DOLLS_PER_CAST, dolls)
    }

    /** How much paper one cast costs. */
    fun paperCost(sequence: Sequence): Int = dollsPerCast(sequence) * PAPER_PER_DOLL

    /** Accumulation ceiling for dolls (more than one batch may be stored). */
    fun maxStored(sequence: Sequence): Int = dollsPerCast(sequence) + 3
}
