package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import kotlin.math.min

/**
 * Balance numbers for wall climbing (Seq 8 Clown of the Fool pathway — acrobatics).
 *
 * When the clown is pressed against a vertical wall and looking into it, he slowly
 * climbs upwards. The climbing speed grows with power. Pure math — the effect
 * (the wall check, `setVelocity`) lives in `pathways.fool.abilities.ClownAgility`.
 */
object WallClimbRules {

    /** Base vertical climbing speed (blocks/tick) at Seq 9. */
    private const val BASE_CLIMB_SPEED = 0.18

    /** Maximum distance to the wall to count as "pressed against it" (blocks). */
    const val WALL_REACH = 0.9

    /** Vertical climbing speed at the current Sequence (with a soft ceiling). */
    fun climbSpeed(sequence: Sequence): Double {
        val multiplier = SequenceScaler.calculateMultiplier(
            sequence.level, SequenceScaler.ScalingStrategy.WEAK,
        )
        return min(0.35, BASE_CLIMB_SPEED * multiplier)
    }
}
