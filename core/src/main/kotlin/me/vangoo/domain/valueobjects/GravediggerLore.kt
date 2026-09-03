package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 8 of the Death pathway (Gravedigger).
 *
 * One source of truth for the tier: Spirit Communication (recruiting/summoning a retinue and
 * the spirit's grip) and the Eye of Death (a node — one strengthened blow). Pure math — no
 * platform; the effects live in `pathways.death.abilities`. The spirits' own stats (HP,
 * damage, speed) do NOT live here — they belong to the MythicMobs pack
 * (`mythic-pack/Mobs/death.yml`).
 *
 * Seq. 8 is the first of this tier, so every base number is stated for it and
 * [growth] only raises them for stronger Sequences (at Seq. 8 the multiplier is exactly 1.0).
 * The Seq. 9 numbers live separately, in [CorpseCollectorLore].
 */
object GravediggerLore {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 8

    // --- Gravedigger body (PhysicalEnhancement, shared identity "death_physique") ---
    /** Base for the bonus HP; twice that of Seq. 9 — stronger and more agile. */
    const val PHYSIQUE_HP_BASE = 6

    // --- Spirit Communication: costs ---
    /** Summon a spirit from the Spirit World (none nearby). */
    const val SPIRIT_SUMMON_COST = 75

    /** Strike a deal with a spirit already wandering nearby — cheaper than summoning. */
    const val SPIRIT_RECRUIT_COST = 25

    private const val SPIRIT_COOLDOWN_SECONDS = 30
    private const val SPIRIT_COOLDOWN_FLOOR_SECONDS = 15L

    // --- Spirit Communication: retinue ---
    private const val SPIRIT_LIMIT = 2
    private const val SPIRIT_LIMIT_CAP = 6L
    private const val SPIRIT_RECRUIT_RADIUS = 16.0
    private const val SPIRIT_RECRUIT_RADIUS_CAP = 48.0

    /** Radius of the circle the spirit weaves ABOVE the owner's head — so it does not catch his blows. */
    const val SPIRIT_FOLLOW_DISTANCE = 2.0

    /** Fall further behind and it hops rather than runs (otherwise it would get lost behind walls and water). */
    const val SPIRIT_TELEPORT_DISTANCE = 32.0

    // --- Spirit's grip (the retinue's reaction to the owner's blow) ---
    /** How long the grip lasts after a blow; EVERY new blow restarts the countdown from zero. */
    private const val GRASP_SECONDS = 10

    /**
     * The length of the spirit's "arms" HORIZONTALLY: the spirit hangs above the victim's head
     * and the arms reach down, so height does not count towards the distance. Independent of
     * the Sequence.
     */
    const val GRASP_REACH = 3.0

    /** Slowness level of the grip (0 = I), i.e. Slowness IV. This is the grip's strength, not the spirit's. */
    const val GRASP_SLOWNESS_AMPLIFIER = 3

    // --- Eye of Death (node) ---
    const val EYE_COST = 50
    private const val EYE_COOLDOWN_SECONDS = 14
    private const val EYE_COOLDOWN_FLOOR_SECONDS = 5L
    private const val EYE_RANGE = 24.0
    private const val EYE_RANGE_CAP = 64.0

    /** Damage bonus above 1.0 against the DEAD (undead, spirit, rampager): Seq. 8 = +120%. */
    private const val EYE_DAMAGE_BONUS = 1.2
    private const val EYE_DAMAGE_MULTIPLIER_CAP = 3.5

    /** One node per caster — recasting moves it to the new target. */
    const val MAX_CONCURRENT_NODES = 1

    /**
     * Technical lifetime ceiling of the node. To the player it looks open-ended (until it is
     * spent), but the hit subscription must have a TTL: without one a relog would leave a
     * listener hanging forever.
     */
    const val NODE_TTL_SECONDS = 30 * 60

    /** Spirit Communication cooldown (sec): Seq. 8 = 15, shortens WEAK, never below the floor. */
    fun spiritCooldownSeconds(sequence: Sequence): Int {
        val seconds = SPIRIT_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(SPIRIT_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** How many spirits are held at once: Seq. 8 = 2, grows MODERATE up to a 6 ceiling. */
    fun spiritLimit(sequence: Sequence): Int {
        val limit = SPIRIT_LIMIT * growth(sequence, ScalingStrategy.MODERATE)
        return min(SPIRIT_LIMIT_CAP, limit.roundToLong()).toInt()
    }

    /** Search radius for a free spirit to recruit (blocks): Seq. 8 = 16, grows WEAK up to a 48 ceiling. */
    fun spiritRecruitRadius(sequence: Sequence): Double = min(
        SPIRIT_RECRUIT_RADIUS_CAP,
        SPIRIT_RECRUIT_RADIUS * growth(sequence, ScalingStrategy.WEAK),
    )

    /** How long a spirit holds one target (sec): Seq. 8 = 6, grows MODERATE. */
    fun graspSeconds(sequence: Sequence): Int =
        (GRASP_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Eye of Death cooldown (sec): Seq. 8 = 14, shortens WEAK, never below the floor. */
    fun eyeCooldownSeconds(sequence: Sequence): Int {
        val seconds = EYE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(EYE_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** Range of the gaze that places the node (blocks): Seq. 8 = 24, grows WEAK up to a 64 ceiling. */
    fun eyeRange(sequence: Sequence): Double =
        min(EYE_RANGE_CAP, EYE_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * Multiplier of the blow into the node against the DEAD (undead, spirit, rampager):
     * Seq. 8 = 2.2, the bonus grows WEAK up to a 3.5 ceiling.
     */
    fun eyeDeadDamageMultiplier(sequence: Sequence): Double {
        val bonus = EYE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(EYE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /**
     * Multiplier against the LIVING: half the bonus of the dead one (Seq. 8 = 1.6). Derived
     * from [eyeDeadDamageMultiplier] so that the two numbers do not drift apart when the
     * balance changes.
     */
    fun eyeLivingDamageMultiplier(sequence: Sequence): Double =
        1.0 + (eyeDeadDamageMultiplier(sequence) - 1.0) / 2.0

    /** How far power at the given Sequence exceeds power at Seq. 8 (exactly 1.0 there). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
