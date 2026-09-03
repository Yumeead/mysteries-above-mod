package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 9 of the Death pathway (Corpse Collector).
 *
 * One source of truth for all five abilities of the tier: Knowledge (Undead) — weak-spot
 * mark and corpse autopsy, Spirit Sight, Spirit Vision, Resistance to Corpse Auras. Pure
 * math — no platform; the effects live in `pathways.death.abilities`.
 *
 * Seq. 9 is the weakest, so every base number is stated for it and [growth] only raises
 * them for stronger Sequences (at Seq. 9 the multiplier is exactly 1.0).
 */
object CorpseCollectorLore {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 9

    // --- Corpse collector body (PhysicalEnhancement) ---
    /** Base for the bonus HP; the tier grants no potion effects — the body is cold, not fast. */
    const val PHYSIQUE_HP_BASE = 3

    // --- Knowledge (Undead): shared by both cast modes ---
    const val KNOWLEDGE_COST = 40
    private const val KNOWLEDGE_COOLDOWN_SECONDS = 12
    private const val KNOWLEDGE_COOLDOWN_FLOOR_SECONDS = 4L

    // --- "Weak spot mark" mode (undead only) ---
    private const val MARK_RANGE = 20.0
    private const val MARK_RANGE_CAP = 60.0
    private const val MARK_DURATION_SECONDS = 20

    /** Damage bonus above 1.0: Seq. 9 = +35%. */
    private const val MARK_DAMAGE_BONUS = 0.35
    private const val MARK_DAMAGE_MULTIPLIER_CAP = 2.5

    /** One mark per caster — recasting moves it to the new target. */
    const val MAX_CONCURRENT_MARKS = 1

    // --- "Corpse autopsy" mode (DEATH events from CoreProtect) ---
    private const val AUTOPSY_RADIUS = 10.0
    private const val AUTOPSY_RADIUS_CAP = 40.0
    private const val AUTOPSY_WINDOW_SECONDS = 30 * 60
    private const val AUTOPSY_MAX_RECORDS = 5

    // --- Spirit Sight (toggle) ---
    private const val SPIRIT_VISION_RANGE = 20.0
    private const val SPIRIT_VISION_RANGE_CAP = 60.0

    /** Periodic upkeep of the toggle per second; independent of the Sequence. */
    const val SPIRIT_VISION_PERIODIC_COST = 3

    // --- Spirit Vision (permanent, free) ---
    private const val DEATH_SIGHT_RANGE = 16.0
    private const val DEATH_SIGHT_RANGE_CAP = 48.0

    // --- Grim presence ---
    /**
     * How many seconds the undead remembers that the corpse collector struck first. While it
     * remembers, it aggroes like on anyone else; once it forgets, grim presence makes it his
     * own again. Independent of the Sequence: this is a behaviour rule, not power.
     */
    const val PROVOKE_MEMORY_SECONDS = 10
    private const val GLOOM_RADIUS = 24.0
    private const val GLOOM_RADIUS_CAP = 64.0

    // --- Resistance to corpse auras ---
    /** How many times faster Wither and Poison decay: Seq. 9 = twice. */
    private const val DECAY_ACCELERATION = 2.0

    /** Knowledge cooldown (sec): Seq. 9 = 12, shortens WEAK, never below the floor. */
    fun knowledgeCooldownSeconds(sequence: Sequence): Int {
        val seconds = KNOWLEDGE_COOLDOWN_SECONDS / growth(sequence, ScalingStrategy.WEAK)
        return max(KNOWLEDGE_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /** Aim range of the mark (blocks): Seq. 9 = 20, grows WEAK up to a 60 ceiling. */
    fun markRange(sequence: Sequence): Double =
        min(MARK_RANGE_CAP, MARK_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** How long the mark holds (sec): Seq. 9 = 20, grows MODERATE. */
    fun markDurationSeconds(sequence: Sequence): Int =
        (MARK_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /**
     * Caster's damage multiplier against marked undead: Seq. 9 = 1.35, the bonus grows WEAK
     * up to a 2.5 ceiling. On non-undead the mark is never placed — there is no multiplier here.
     */
    fun markDamageMultiplier(sequence: Sequence): Double {
        val bonus = MARK_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(MARK_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /** Radius for reading a death scene (blocks): Seq. 9 = 10, grows WEAK up to a 40 ceiling. */
    fun autopsyRadius(sequence: Sequence): Double =
        min(AUTOPSY_RADIUS_CAP, AUTOPSY_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** How long ago a death may have happened and still be readable (sec): Seq. 9 = 30 min, grows MODERATE. */
    fun autopsyWindowSeconds(sequence: Sequence): Int =
        (AUTOPSY_WINDOW_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** How many deaths one autopsy shows: Seq. 9 = 5, grows WEAK. */
    fun autopsyMaxRecords(sequence: Sequence): Int =
        (AUTOPSY_MAX_RECORDS * growth(sequence, ScalingStrategy.WEAK)).roundToInt()

    /**
     * Radius in which grim presence makes undead drop a target it already took (blocks):
     * Seq. 9 = 24, grows WEAK up to a 64 ceiling.
     */
    fun gloomRadius(sequence: Sequence): Double =
        min(GLOOM_RADIUS_CAP, GLOOM_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Spirit Sight radius (blocks): Seq. 9 = 20, grows WEAK up to a 60 ceiling. */
    fun spiritVisionRange(sequence: Sequence): Double =
        min(SPIRIT_VISION_RANGE_CAP, SPIRIT_VISION_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Permanent Spirit Vision radius (blocks): Seq. 9 = 16, grows WEAK up to a 48 ceiling. */
    fun deathSightRange(sequence: Sequence): Double =
        min(DEATH_SIGHT_RANGE_CAP, DEATH_SIGHT_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * How many times faster Wither and Poison decay on the caster: Seq. 9 = 2.0, grows WEAK.
     * Freezing is outside this math — immunity to it is absolute from the very first
     * Sequence.
     */
    fun decayAcceleration(sequence: Sequence): Double =
        DECAY_ACCELERATION * growth(sequence, ScalingStrategy.WEAK)

    /**
     * How many extra Wither/Poison ticks are eaten for every second the effect is carried.
     * An acceleration of 2.0 means "two seconds burn per second" — that is 20 ticks on top
     * of the vanilla clock.
     */
    fun decayExtraTicksPerSecond(sequence: Sequence): Int =
        (20 * (decayAcceleration(sequence) - 1.0)).roundToInt()

    /** How far power at the given Sequence exceeds power at Seq. 9 (exactly 1.0 there). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
