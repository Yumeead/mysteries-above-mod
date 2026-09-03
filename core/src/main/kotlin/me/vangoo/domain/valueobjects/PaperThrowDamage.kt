package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.ceil

/**
 * Balance numbers for the passive plain-paper throw (Seq 8 Clown of the Fool pathway).
 *
 * The throw costs nothing, has a short cooldown and deals damage that grows with the
 * Beyonder's power. Pure math — no platform; the throw effect lives in
 * `pathways.fool.abilities.PaperCutter` + `PaperThrowListener`.
 */
object PaperThrowDamage {

    /** Base damage of the dagger at Seq 9 (before scaling). */
    const val BASE_DAMAGE = 6

    /** Cooldown between throws — 0.5s (10 ticks). */
    const val THROW_COOLDOWN_TICKS = 10

    /** Damage of one paper projectile at the current Sequence. */
    fun damageFor(sequence: Sequence): Int {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.MODERATE,
        )
        return ceil(BASE_DAMAGE * multiplier).toInt()
    }
}
