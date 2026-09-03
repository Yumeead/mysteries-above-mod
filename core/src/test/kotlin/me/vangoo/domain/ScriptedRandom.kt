package me.vangoo.domain

import kotlin.random.Random

/**
 * Pre-recorded rolls. Needed wherever a specific probability step is under test rather than
 * "average" behaviour: the sanity penalty has six steps, and each must be checked on its own.
 */
class ScriptedRandom(
    doubles: List<Double> = emptyList(),
    booleans: List<Boolean> = emptyList(),
) : Random() {

    private val doubles = ArrayDeque(doubles)
    private val booleans = ArrayDeque(booleans)

    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double =
        checkNotNull(doubles.removeFirstOrNull()) { "Сценарій вичерпав nextDouble()" }

    override fun nextBoolean(): Boolean =
        checkNotNull(booleans.removeFirstOrNull()) { "Сценарій вичерпав nextBoolean()" }
}
