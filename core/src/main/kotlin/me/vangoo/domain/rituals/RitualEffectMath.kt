package me.vangoo.domain.rituals

import kotlin.math.min

/**
 * Балансні базові числа ефектів ритуалів (до Sequence-скейлу) та формула шансу Дарування.
 * Тримається окремо від ефект-шару, бо балансна математика не належить раннеру.
 */
object RitualEffectMath {

    /** 5 хв */
    const val LUCK_BASE_TICKS = 6000

    /** символічний ремонт, не повне відновлення */
    const val SANCTIFY_BASE_DURABILITY = 50

    /** 30 хв */
    const val EVENTS_BASE_WINDOW_SECONDS = 1800

    /** 30 с */
    const val WALL_BASE_TICKS = 600

    fun bestowmentChance(sequenceLevel: Int): Double = min(0.9, 0.5 + 0.06 * (9 - sequenceLevel))
}
