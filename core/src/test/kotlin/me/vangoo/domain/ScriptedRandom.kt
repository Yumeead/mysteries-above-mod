package me.vangoo.domain

import kotlin.random.Random

/**
 * Заздалегідь записані кидки. Потрібен там, де перевіряється саме сходинка
 * ймовірності, а не поведінка «в середньому»: сходинок штрафу глузду шість, і кожна
 * має бути перевірена окремо.
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
