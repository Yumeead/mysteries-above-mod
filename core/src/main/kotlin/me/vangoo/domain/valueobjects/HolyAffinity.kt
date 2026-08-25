package me.vangoo.domain.valueobjects

/**
 * Класифікація pathway як "темного/нежиті" для святого урону Sun.
 * Набір pathways — калібрувальна константа (тюнінг-важіль), не архітектурне рішення.
 */
object HolyAffinity {

    private val DARK_PATHWAYS = setOf("Death", "Darkness", "Chained", "HangedMan", "Abyss")

    private const val DARK_MULTIPLIER = 1.0
    private const val NEUTRAL_MULTIPLIER = 0.6

    fun isDark(pathwayName: String?): Boolean = pathwayName in DARK_PATHWAYS

    fun damageMultiplier(pathwayName: String?): Double = damageMultiplier(isDark(pathwayName))

    /** Для цілей поза pathway-класифікацією (ванільні нежить-моби) — класифікація на боці ефекту. */
    fun damageMultiplier(dark: Boolean): Double = if (dark) DARK_MULTIPLIER else NEUTRAL_MULTIPLIER
}
