package me.vangoo.domain.creatures

import kotlin.random.Random

/**
 * Правило темпу кастів істоти: глобальний кулдаун (ГКД) між будь-якими двома кастами
 * + власний кулдаун кожної здібності. Коли готові кілька — вибір ЗВАЖЕНО-ВИПАДКОВИЙ
 * серед готових: вага = позиція у списку (перша — найважча; фірмову здібність найнижчої
 * послідовності ставлять першою). Випадковість дає різноманітну ротацію замість
 * детермінованого циклу, де топ-здібність монополізує кожне вікно ГКД.
 *
 * Одиниці часу довільні (тіки/мс) — [now][pickNext] і кулдауни мають бути в одних одиницях.
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

    fun pickNext(now: Long): String? {
        if (now < gcdReadyAt) return null

        val readyIndexes = kit.indices.filter { now >= (skillReadyAt[kit[it].skillName] ?: Long.MIN_VALUE) }
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

    /** Вага за позицією: перша у списку (фірмова) — найважча, остання — 1. */
    private fun weightOf(index: Int): Int = kit.size - index
}
