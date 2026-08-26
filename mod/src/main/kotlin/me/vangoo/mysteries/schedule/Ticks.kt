package me.vangoo.mysteries.schedule

import kotlin.math.max

/** Тіків на секунду. Ванільна константа, але вписувати `20` по місцях не варто. */
const val TICKS_PER_SECOND: Int = 20

/** Мілісекунд на тік — курс, за яким `delay()` переводиться в тіки. */
const val MILLIS_PER_TICK: Long = 50L

/** Секунди в тіки. Від'ємне часу не займає. */
fun seconds(count: Int): Int = max(0, count) * TICKS_PER_SECOND

/**
 * Мілісекунди в тіки, вгору.
 *
 * Округлення саме вгору: `delay(1)` мусить чекати тік, а не пройти наскрізь — інакше
 * `while (true) { delay(1) }` заклинить тік сервера намертво.
 */
fun millisToTicks(millis: Long): Int = when {
    millis <= 0L -> 0
    else -> ((millis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
