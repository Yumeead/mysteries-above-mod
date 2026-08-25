package me.vangoo.domain.valueobjects

/**
 * Тимчасовий множник шкоди (Sun `Notarization`); [isActive] перевіряє момент часу
 * без окремого таску.
 */
data class AmplificationBuff(val multiplier: Double, val expiresAtMillis: Long) {

    fun isActive(nowMillis: Long): Boolean = nowMillis < expiresAtMillis
}
