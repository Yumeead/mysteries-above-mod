package me.vangoo.domain.valueobjects

import kotlin.math.min

/**
 * Balance formula for healing based on recently taken damage
 * (Seq 7 Magician of the Fool pathway — the "Damage Transfer" rework).
 *
 * Heals a share of the damage taken within the window, never above a ceiling that grows
 * with power. Does NOT heal to full — this is "closing" half a wound. Pure math.
 */
object RecentDamageHeal {

    /** The window over which recent damage is counted. */
    const val DAMAGE_WINDOW_MS = 10_000L

    private const val HEAL_FRACTION = 0.5
    private const val BASE_CEILING = 8

    /** 0 (Seq 9) … 9 (Seq 0). */
    private fun power(sequence: Sequence): Int = 9 - sequence.level

    /** Ceiling of a single heal (HP) at the current Sequence. */
    fun healCeiling(sequence: Sequence): Double = (BASE_CEILING + power(sequence)).toDouble()

    /** How much HP the ability restores from [recentDamage] taken within the window. */
    fun healAmount(recentDamage: Double, sequence: Sequence): Double {
        if (recentDamage <= 0) return 0.0
        return min(recentDamage * HEAL_FRACTION, healCeiling(sequence))
    }
}
