package me.vangoo.domain.valueobjects

/**
 * Temporary damage multiplier (Sun `Notarization`); [isActive] checks a point in time
 * without a separate task.
 */
data class AmplificationBuff(val multiplier: Double, val expiresAtMillis: Long) {

    fun isActive(nowMillis: Long): Boolean = nowMillis < expiresAtMillis
}
