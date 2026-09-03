package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 6 of the Death pathway (Spirit Guide).
 *
 * Only what either depends on the Sequence or is shared by several files lives here (the cost
 * is seen by both the ability and the menu; the corpse lifetime by both the registry and
 * Resurrection). One-off constants without scaling live in their own classes.
 *
 * Seq. 6 is the first of this tier, so every base number is stated for it and [growth] only
 * raises them for stronger Sequences (at Seq. 6 the multiplier is exactly 1.0). The numbers
 * of weaker tiers are in [SpiritMediumLore], [GravediggerLore], [CorpseCollectorLore].
 */
object SpiritGuideLore {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 6

    // --- Spirit guide body (PhysicalEnhancement, shared identity "death_physique") ---
    const val PHYSIQUE_HP_BASE = 8

    // --- The corpse at the place of death (shared: the LingeringSouls registry + Resurrection) ---
    /** How long the corpse lies where it died (sec). The soul hangs for 30 min, the body rots faster. */
    const val CORPSE_TTL_SECONDS = 5 * 60

    // --- Tongue of the Dead (tearing the soul out) ---
    const val LANGUAGE_COST = 100
    private const val LANGUAGE_COOLDOWN_SECONDS = 20
    private const val LANGUAGE_COOLDOWN_FLOOR_SECONDS = 5
    private const val LANGUAGE_DURATION_SECONDS = 6
    private const val LANGUAGE_RANGE = 20.0
    private const val LANGUAGE_RANGE_CAP = 32.0

    // --- Resurrection (corpse -> servant) ---
    const val RESURRECTION_COST = 150
    private const val RESURRECTION_COOLDOWN_SECONDS = 80
    private const val RESURRECTION_COOLDOWN_FLOOR_SECONDS = 20

    // --- Retinue ---
    /**
     * Retinue ceiling. A deliberate departure from the wiki's thousands — that many entities
     * would kill the server. Shared by all three entry points (Resurrection / Subjugation /
     * summoning creatures).
     */
    const val RETINUE_CAP = 10

    // --- Subjugating undead (area) ---
    const val SUBJUGATION_COST = 150
    private const val SUBJUGATION_COOLDOWN_SECONDS = 45
    private const val SUBJUGATION_COOLDOWN_FLOOR_SECONDS = 20
    private const val SUBJUGATION_RADIUS = 12.0
    private const val SUBJUGATION_RADIUS_CAP = 24.0

    // --- Pact with the spirits of the Spirit World: those found in Hell, the price of joining
    // the retinue (the prices are fixed: the creature's power is its own growth) ---
    const val LIVING_SHADOW_COST = 50
    const val SHADOW_PYTHON_COST = 60
    const val DEATH_KNIGHT_COST = 70
    const val LAKE_GODDESS_COST = 80
    private const val PACT_COOLDOWN_SECONDS = 30
    private const val PACT_COOLDOWN_FLOOR_SECONDS = 10

    // --- Spirit exchange (a shield against soul effects at the cost of a servant) ---
    const val SPIRIT_SWAP_COST = 110

    /** How many servants the exchange eats. */
    const val SPIRIT_SWAP_SERVANT_COST = 1
    private const val SPIRIT_SWAP_WARD_SECONDS = 30
    private const val SPIRIT_SWAP_COOLDOWN_SECONDS = 90
    private const val SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS = 40

    // --- Lift-off (the undead throws the guide upwards) ---
    const val SOAR_COST = 15

    /** Anti-spam, not power — hence constant. */
    const val SOAR_COOLDOWN_SECONDS = 20

    // --- Mutiny (D3): the retinue flees near a stronger Death Beyonder ---
    const val REBELLION_RADIUS = 24.0
    private const val REBELLION_CHANCE_PER_LEVEL = 0.05

    /** Tongue of the Dead cooldown (sec): Seq. 6 = 70, shortens WEAK, never below the floor. */
    fun languageCooldownSeconds(sequence: Sequence): Int =
        shrink(LANGUAGE_COOLDOWN_SECONDS, LANGUAGE_COOLDOWN_FLOOR_SECONDS, sequence)

    /** How long the soul stays outside the body (sec): Seq. 6 = 5, grows MODERATE. */
    fun languageDurationSeconds(sequence: Sequence): Int =
        (LANGUAGE_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Tongue of the Dead range (blocks): Seq. 6 = 20, grows WEAK up to a 32 ceiling. */
    fun languageRange(sequence: Sequence): Double =
        min(LANGUAGE_RANGE_CAP, LANGUAGE_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Resurrection cooldown (sec): Seq. 6 = 12, shortens WEAK, never below the floor. */
    fun resurrectionCooldownSeconds(sequence: Sequence): Int =
        shrink(RESURRECTION_COOLDOWN_SECONDS, RESURRECTION_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Subjugate Undead cooldown (sec): Seq. 6 = 45, shortens WEAK, never below the floor. */
    fun subjugationCooldownSeconds(sequence: Sequence): Int =
        shrink(SUBJUGATION_COOLDOWN_SECONDS, SUBJUGATION_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Spirit Pact cooldown (sec): Seq. 6 = 30, shortens WEAK, never below the floor. */
    fun pactCooldownSeconds(sequence: Sequence): Int =
        shrink(PACT_COOLDOWN_SECONDS, PACT_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Subjugation radius (blocks): Seq. 6 = 12, grows WEAK up to a 24 ceiling. */
    fun subjugationRadius(sequence: Sequence): Double =
        min(SUBJUGATION_RADIUS_CAP, SUBJUGATION_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** How long the spirit-exchange shield holds (sec): Seq. 6 = 30, grows MODERATE. */
    fun spiritSwapWardSeconds(sequence: Sequence): Int =
        (SPIRIT_SWAP_WARD_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    /** Spirit exchange cooldown (sec): Seq. 6 = 90, shortens WEAK, never below the floor. */
    fun spiritSwapCooldownSeconds(sequence: Sequence): Int =
        shrink(SPIRIT_SWAP_COOLDOWN_SECONDS, SPIRIT_SWAP_COOLDOWN_FLOOR_SECONDS, sequence)

    /**
     * Chance per second that a servant betrays the guide near another Death Beyonder: 5% for
     * every Sequence of the rival's advantage. An equal or weaker rival does not touch the
     * retinue (0). There is no ceiling and none is needed: the guide is Seq. 6, so the gap is
     * never larger than 6 (30%).
     */
    fun rebellionChancePerSecond(guide: Sequence, rival: Sequence): Double {
        val levelsStronger = guide.level - rival.level
        if (levelsStronger <= 0) {
            return 0.0
        }
        return REBELLION_CHANCE_PER_LEVEL * levelsStronger
    }

    /** A cooldown that shortens WEAK from the base down to the floor. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** How far power at the given Sequence exceeds power at Seq. 6 (exactly 1.0 there). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
