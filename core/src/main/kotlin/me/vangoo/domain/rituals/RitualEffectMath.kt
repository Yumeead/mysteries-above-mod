package me.vangoo.domain.rituals

import kotlin.math.min

/**
 * The base balance numbers of ritual effects (before Sequence scaling) and the Bestowment
 * chance formula. Kept apart from the effect layer, because balance math does not belong to
 * the runner.
 */
object RitualEffectMath {

    /** 5 min */
    const val LUCK_BASE_TICKS = 6000

    /** a symbolic repair, not a full restoration */
    const val SANCTIFY_BASE_DURABILITY = 50

    /** 30 min */
    const val EVENTS_BASE_WINDOW_SECONDS = 1800

    /** 30 s */
    const val WALL_BASE_TICKS = 600

    fun bestowmentChance(sequenceLevel: Int): Double = min(0.9, 0.5 + 0.06 * (9 - sequenceLevel))
}
