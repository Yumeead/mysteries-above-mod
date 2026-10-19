package me.vangoo.domain.creatures

import kotlin.random.Random

/**
 * The pacing rule for a creature's casts: a global cooldown (GCD) between any two casts plus
 * each ability's own cooldown. When several are ready, the choice is WEIGHTED-RANDOM among
 * them: the weight is the position in the list (the first is the heaviest; the signature
 * ability of the lowest sequence goes first). Randomness gives a varied rotation instead of a
 * deterministic cycle where the top ability monopolises every GCD window.
 *
 * The time units are arbitrary (ticks/ms) — [now][pickNext] and the cooldowns must be in the
 * same units.
 */
class AbilityCastPlanner internal constructor(
    kit: List<KitEntry>,
    private val gcd: Long,
    private val random: Random,
) {
    constructor(kit: List<KitEntry>, gcd: Long) : this(kit, gcd, Random.Default)

    data class KitEntry(val skillName: String, val cooldown: Long)

    private val kit: List<KitEntry> = kit.toList()
    private var gcdReadyAt: Long = 0
    private val skillReadyAt = HashMap<String, Long>()

    /**
     * @param usable whether a ready skill makes sense right now (range, a Beyonder target…). A skill
     * that fails it is skipped **without** spending its cooldown or the GCD — the plugin burnt both
     * on a cast its condition then refused, which made creatures look idle against ordinary people.
     */
    fun pickNext(now: Long, usable: (String) -> Boolean = { true }): String? {
        if (now < gcdReadyAt) return null

        val readyIndexes = kit.indices.filter {
            now >= (skillReadyAt[kit[it].skillName] ?: Long.MIN_VALUE) && usable(kit[it].skillName)
        }
        if (readyIndexes.isEmpty()) return null

        var roll = random.nextInt(readyIndexes.sumOf { weightOf(it) })
        var chosenIndex = readyIndexes.last()
        for (i in readyIndexes) {
            roll -= weightOf(i)
            if (roll < 0) {
                chosenIndex = i
                break
            }
        }

        val chosen = kit[chosenIndex]
        gcdReadyAt = now + gcd
        skillReadyAt[chosen.skillName] = now + chosen.cooldown
        return chosen.skillName
    }

    /** Weight by position: the first in the list (the signature one) is the heaviest, the last is 1. */
    private fun weightOf(index: Int): Int = kit.size - index
}
