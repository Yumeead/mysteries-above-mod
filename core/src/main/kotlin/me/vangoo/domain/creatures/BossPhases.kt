package me.vangoo.domain.creatures

/**
 * Which act of a boss fight is playing.
 *
 * A phase begins when health first drops to its threshold and never ends early: healing does not
 * take a boss back to a calmer act. Only a full reset — the fight abandoned, the arena left —
 * starts it over from the first.
 *
 * @param thresholds the health fraction at which each phase begins; the first is `1.0`, the rest
 * strictly descending
 */
class BossPhases(val thresholds: List<Float>) {

    init {
        require(thresholds.isNotEmpty() && thresholds.first() == 1f) { "Перша фаза боса починається з повного здоров'я" }
        require(thresholds.zipWithNext().all { (a, b) -> b < a }) { "Пороги фаз мусять спадати: $thresholds" }
    }

    /** The phase that [fraction] of health has reached, never earlier than [current]. */
    fun at(fraction: Float, current: Int): Int = maxOf(current, thresholds.indexOfLast { fraction <= it })
}
