package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 7 of the Death pathway (Spirit Medium).
 *
 * One source of truth for the tier: Spirit Perception (passive), Spirit Summoning (Illusory
 * Eye, Frost Shadow, Earth Spirit), Voice of the Dead (two interrogation branches) and the
 * Corpse Guise. Pure math — no platform; the effects live in `pathways.death.abilities`.
 *
 * Seq. 7 is the first of this tier, so every base number is stated for it and [growth] only
 * raises them for stronger Sequences (at Seq. 7 the multiplier is exactly 1.0). The numbers
 * of weaker tiers live separately, in [GravediggerLore] and [CorpseCollectorLore].
 */
object SpiritMediumLore {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 7

    // --- Spirit medium body (PhysicalEnhancement, shared identity "death_physique") ---
    /** Base bonus HP. A deliberately small step above Seq. 8 (6): the wiki says not significant. */
    const val PHYSIQUE_HP_BASE = 7

    // --- Spirit Perception (permanent passive, free) ---
    private const val PERCEPTION_RADIUS = 24.0
    private const val PERCEPTION_RADIUS_CAP = 48.0

    // --- Voice of the Dead: a soul lingering at the place of death ---
    /**
     * How long the soul hangs at the place of death (sec). It does NOT depend on the Sequence:
     * this is the soul's own lifetime, not the medium's power. Not persisted — a restart wipes it.
     */
    const val SOUL_TTL_SECONDS = 30 * 60

    // --- Spirit Summoning: no shared cost is set, each spirit has its own ---

    // --- Illusory Eye (scouting) ---
    const val ILLUSORY_EYE_COST = 30
    private const val ILLUSORY_EYE_COOLDOWN_SECONDS = 40
    private const val ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS = 15
    private const val ILLUSORY_EYE_DURATION_SECONDS = 15
    private const val ILLUSORY_EYE_RADIUS = 48.0
    private const val ILLUSORY_EYE_RADIUS_CAP = 64.0

    // --- Frost Shadow (a domain of cold + ice armour + scythe) ---
    const val FROST_SHADOW_COST = 90
    private const val FROST_COOLDOWN_SECONDS = 50
    private const val FROST_COOLDOWN_FLOOR_SECONDS = 30
    private const val FROST_DURATION_SECONDS = 12

    /** The domain radius grows MODERATE precisely to reach the wiki's 10-20 meter range. */
    private const val FROST_RADIUS = 10.0
    private const val FROST_RADIUS_CAP = 20.0
    private const val FROST_TICK_DAMAGE = 1.0
    private const val FROST_TICK_DAMAGE_CAP = 3.0

    /** Damage bonus of the frost scythe above 1.0: Seq. 7 = +40%. */
    private const val SCYTHE_DAMAGE_BONUS = 0.4
    private const val SCYTHE_DAMAGE_MULTIPLIER_CAP = 2.5

    // --- Earth Spirit (dragging into the ground; costs blood) ---
    const val EARTH_SPIRIT_COST = 80

    /** Blood price in HP (2 = a heart). Constant: blood is blood, a stronger medium pays no less. */
    const val EARTH_SPIRIT_BLOOD_HP = 4
    private const val EARTH_COOLDOWN_SECONDS = 55
    private const val EARTH_COOLDOWN_FLOOR_SECONDS = 20
    private const val EARTH_HOLD_SECONDS = 5

    /** Radius of the mire around the caught target (blocks): neighbours bog down but do not sink. */
    const val EARTH_SWAMP_RADIUS = 4.0
    private const val EARTH_RANGE = 12.0
    private const val EARTH_RANGE_CAP = 24.0

    // --- Voice of the Dead ---
    const val VOICE_COST = 100
    private const val VOICE_DEAD_COOLDOWN_SECONDS = 500
    private const val VOICE_DEAD_COOLDOWN_FLOOR_SECONDS = 40
    private const val VOICE_DEAD_RADIUS = 8.0
    private const val VOICE_DEAD_RADIUS_CAP = 16.0
    private const val VOICE_LIVING_COOLDOWN_SECONDS = 60
    private const val VOICE_LIVING_COOLDOWN_FLOOR_SECONDS = 20
    private const val VOICE_LIVING_RANGE = 16.0
    private const val VOICE_LIVING_RANGE_CAP = 32.0

    /** Sanity per Sequence of the target's advantage; a stronger will throws the interrogation back hard. */
    private const val VOICE_BACKLASH_PER_LEVEL = 8
    private const val VOICE_BACKLASH_CAP = 40

    // --- Corpse Guise (toggle) ---
    const val GUISE_ACTIVATION_COST = 10

    /** Periodic upkeep of the toggle per second; independent of the Sequence. */
    const val GUISE_PERIODIC_COST = 10

    /** Anti-spam for the toggle, not power — hence constant. */
    const val GUISE_COOLDOWN_SECONDS = 5

    /** Spirit Perception radius (blocks): Seq. 7 = 24, grows WEAK up to a 48 ceiling. */
    fun perceptionRadius(sequence: Sequence): Double =
        min(PERCEPTION_RADIUS_CAP, PERCEPTION_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Illusory Eye cooldown (sec): Seq. 7 = 45, shortens WEAK, never below the floor. */
    fun illusoryEyeCooldownSeconds(sequence: Sequence): Int =
        shrink(ILLUSORY_EYE_COOLDOWN_SECONDS, ILLUSORY_EYE_COOLDOWN_FLOOR_SECONDS, sequence)

    /** How long the eye lights the target (sec): Seq. 7 = 15, grows MODERATE. */
    fun illusoryEyeDurationSeconds(sequence: Sequence): Int =
        (ILLUSORY_EYE_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Radius in which the eye highlights the living (blocks): Seq. 7 = 48, grows WEAK up to a 64 ceiling. */
    fun illusoryEyeRadius(sequence: Sequence): Double =
        min(ILLUSORY_EYE_RADIUS_CAP, ILLUSORY_EYE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Frost Shadow cooldown (sec): Seq. 7 = 90, shortens WEAK, never below the floor. */
    fun frostCooldownSeconds(sequence: Sequence): Int =
        shrink(FROST_COOLDOWN_SECONDS, FROST_COOLDOWN_FLOOR_SECONDS, sequence)

    /** How long the domain holds (sec): Seq. 7 = 12, grows MODERATE. */
    fun frostDurationSeconds(sequence: Sequence): Int =
        (FROST_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Radius of the frozen domain (blocks): Seq. 7 = 10, grows MODERATE up to the wiki's 20 ceiling. */
    fun frostRadius(sequence: Sequence): Double =
        min(FROST_RADIUS_CAP, FROST_RADIUS * growth(sequence, ScalingStrategy.MODERATE))

    /** Cold damage per second inside the domain: Seq. 7 = 1.0 (half a heart), grows WEAK up to a 3.0 ceiling. */
    fun frostTickDamage(sequence: Sequence): Double =
        min(FROST_TICK_DAMAGE_CAP, FROST_TICK_DAMAGE * growth(sequence, ScalingStrategy.WEAK))

    /** Melee multiplier of the frost scythe: Seq. 7 = 1.4, the bonus grows WEAK up to a 2.5 ceiling. */
    fun scytheDamageMultiplier(sequence: Sequence): Double {
        val bonus = SCYTHE_DAMAGE_BONUS * growth(sequence, ScalingStrategy.WEAK)
        return min(SCYTHE_DAMAGE_MULTIPLIER_CAP, 1.0 + bonus)
    }

    /** Earth Spirit cooldown (sec): Seq. 7 = 60, shortens WEAK, never below the floor. */
    fun earthCooldownSeconds(sequence: Sequence): Int =
        shrink(EARTH_COOLDOWN_SECONDS, EARTH_COOLDOWN_FLOOR_SECONDS, sequence)

    /** How long the target is held in the ground (sec): Seq. 7 = 5, grows MODERATE. */
    fun earthHoldSeconds(sequence: Sequence): Int =
        (EARTH_HOLD_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Aim range of the Earth Spirit (blocks): Seq. 7 = 12, grows WEAK up to a 24 ceiling. */
    fun earthRange(sequence: Sequence): Double =
        min(EARTH_RANGE_CAP, EARTH_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Cooldown of interrogating the dead (sec): Seq. 7 = 120, shortens WEAK, never below the floor. */
    fun voiceDeadCooldownSeconds(sequence: Sequence): Int =
        shrink(VOICE_DEAD_COOLDOWN_SECONDS, VOICE_DEAD_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Radius for finding a soul around the medium (blocks): Seq. 7 = 8, grows WEAK up to a 16 ceiling. */
    fun voiceDeadRadius(sequence: Sequence): Double =
        min(VOICE_DEAD_RADIUS_CAP, VOICE_DEAD_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Cooldown of interrogating the living (sec): Seq. 7 = 60, shortens WEAK, never below the floor. */
    fun voiceLivingCooldownSeconds(sequence: Sequence): Int =
        shrink(VOICE_LIVING_COOLDOWN_SECONDS, VOICE_LIVING_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Range of interrogating the living (blocks): Seq. 7 = 16, grows WEAK up to a 32 ceiling. */
    fun voiceLivingRange(sequence: Sequence): Double =
        min(VOICE_LIVING_RANGE_CAP, VOICE_LIVING_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /**
     * Sanity lost when a living target could not be interrogated: 8 for every Sequence the
     * target is stronger than the medium, up to a 40 ceiling. An equal or weaker target
     * costs nothing (0).
     */
    fun voiceBacklashSanity(caster: Sequence, target: Sequence): Int {
        val levelsStronger = caster.level - target.level
        if (levelsStronger <= 0) {
            return 0
        }
        return min(VOICE_BACKLASH_CAP, VOICE_BACKLASH_PER_LEVEL * levelsStronger)
    }

    /** A cooldown that shortens WEAK from the base down to the floor. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** How far power at the given Sequence exceeds power at Seq. 7 (exactly 1.0 there). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
