package me.vangoo.domain.valueobjects

import kotlin.math.max
import kotlin.math.min

data class RampageState(
    val phase: RampagePhase,
    val startTimeMillis: Long,
    val durationSeconds: Int,
) {
    enum class RampagePhase {
        NONE,         // Not in rampage
        BUILDING_UP,  // Warning phase (0-10s)
        CRITICAL,     // Critical phase (10-20s)
        TRANSFORMING, // Point of no return
    }

    /** Check if rampage is active */
    fun isActive(): Boolean = phase != RampagePhase.NONE

    /** Get elapsed time in seconds */
    fun getElapsedSeconds(): Int {
        if (!isActive()) return 0
        return ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
    }

    /** Get remaining time in seconds */
    fun getRemainingSeconds(): Int {
        if (!isActive()) return 0
        return max(0, durationSeconds - getElapsedSeconds())
    }

    /** Get progress percentage (0.0 to 1.0) */
    fun getProgress(): Double {
        if (!isActive()) return 0.0
        return min(1.0, getElapsedSeconds().toDouble() / durationSeconds)
    }

    /** Update phase based on elapsed time */
    fun updatePhase(): RampageState {
        if (!isActive()) return this

        val elapsed = getElapsedSeconds()
        val next = when {
            elapsed >= durationSeconds -> RampagePhase.TRANSFORMING
            elapsed >= WARNING_THRESHOLD -> RampagePhase.CRITICAL
            else -> RampagePhase.BUILDING_UP
        }
        return RampageState(next, startTimeMillis, durationSeconds)
    }

    /** Check if transformation should happen */
    fun shouldTransform(): Boolean =
        phase == RampagePhase.TRANSFORMING || getElapsedSeconds() >= durationSeconds

    /** Check if in critical phase (last 10 seconds) */
    fun isCritical(): Boolean =
        phase == RampagePhase.CRITICAL || phase == RampagePhase.TRANSFORMING

    /** Cancel rampage (rescue) */
    fun cancel(): RampageState = none()

    companion object {
        private const val WARNING_THRESHOLD = 10 // seconds

        fun none(): RampageState = RampageState(RampagePhase.NONE, 0, 0)

        fun start(durationSeconds: Int): RampageState = RampageState(
            RampagePhase.BUILDING_UP,
            System.currentTimeMillis(),
            durationSeconds,
        )
    }
}
