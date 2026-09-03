package me.vangoo.domain.valueobjects

/**
 * Classifies a pathway as "dark/undead" for the Sun's holy damage.
 * The set of pathways is a calibration constant (a tuning knob), not an architectural decision.
 */
object HolyAffinity {

    private val DARK_PATHWAYS = setOf("Death", "Darkness", "Chained", "HangedMan", "Abyss")

    private const val DARK_MULTIPLIER = 1.0
    private const val NEUTRAL_MULTIPLIER = 0.6

    fun isDark(pathwayName: String?): Boolean = pathwayName in DARK_PATHWAYS

    fun damageMultiplier(pathwayName: String?): Double = damageMultiplier(isDark(pathwayName))

    /** For targets outside the pathway classification (vanilla undead mobs) — classified on the effect side. */
    fun damageMultiplier(dark: Boolean): Double = if (dark) DARK_MULTIPLIER else NEUTRAL_MULTIPLIER
}
