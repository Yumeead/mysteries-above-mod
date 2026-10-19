package me.vangoo.domain.creatures

/**
 * Whom a creature is angriest at — the memory behind every choice of target that is not simply
 * "the nearest player".
 *
 * MythicMobs targeted `@NearestPlayer`: whoever stood closest got the skill, so a ranged player
 * could whittle a creature down while a friend kept it busy, and a boss turned on whoever walked
 * past. Here a creature remembers who hurt it and how badly, and fights that one; a citizen
 * remembers who struck them, and the guards they call inherit the grudge.
 *
 * Generic over the key so that the rule is checked without a world (`UUID` in the game).
 */
class ThreatTable<K : Any> {

    private val threat = HashMap<K, Double>()

    fun add(who: K, amount: Double) {
        require(amount >= 0.0) { "Загроза не буває від'ємною: $amount" }
        threat.merge(who, amount, Double::plus)
    }

    fun of(who: K): Double = threat[who] ?: 0.0

    fun forget(who: K) {
        threat.remove(who)
    }

    fun clear() = threat.clear()

    val isEmpty: Boolean get() = threat.isEmpty()

    /** Everyone with a grudge against them, angriest first. */
    fun ranked(): List<K> = threat.entries.sortedByDescending { it.value }.map { it.key }

    /**
     * Grudges fade: every one is multiplied by [factor], and those that fall below [FORGOTTEN] are
     * dropped — a creature that lost its attacker does not chase a ghost forever.
     */
    fun decay(factor: Double) {
        require(factor in 0.0..1.0) { "Згасання — частка: $factor" }
        threat.replaceAll { _, value -> value * factor }
        threat.values.removeIf { it < FORGOTTEN }
    }

    /**
     * Whom to fight among [present]. The current target keeps its place until someone exceeds it by
     * [SWITCH_MARGIN] — the classic 110 % rule: without it two players trading hits would make the
     * creature turn on its heel every second, and no one could hold its attention on purpose.
     *
     * @return `null` when nobody present has hurt it at all
     */
    fun pick(current: K?, present: Collection<K>): K? {
        val best = present.maxByOrNull(::of) ?: return null
        if (of(best) <= 0.0) return null
        if (current != null && current in present && of(best) <= of(current) * SWITCH_MARGIN) return current
        return best
    }

    companion object {
        const val SWITCH_MARGIN = 1.1
        const val FORGOTTEN = 0.5
    }
}
