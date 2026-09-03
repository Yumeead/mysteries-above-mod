package me.vangoo.domain.valueobjects

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Balance numbers for Sequence 5 of the Error pathway (Dream Stealer).
 *
 * One source of truth for Conceptual Theft (six modes), for the Seq-5 tier of Power Theft,
 * for the passive dispelling of illusions and for the Disguise. Pure math — no platform;
 * the effects live in `pathways.error.abilities`.
 *
 * The Seq. 6 numbers stay in [PrometheusTheft] and do not move here: that is Prometheus,
 * this is the Dream Stealer. All they share is that both tiers write into one ledger.
 */
object DreamStealerTheft {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 5

    // --- Shared by every Conceptual Theft mode ---
    /** Wiki: Theft ... within 80 meters range. */
    const val CONCEPTUAL_RANGE = 80.0
    const val CONCEPTUAL_COST = 200
    const val CONCEPTUAL_COOLDOWN_SECONDS = 5

    /** One long-running theft at a time — the same slot as Prometheus uses. */
    const val MAX_CONCURRENT_STOLEN = 1

    /** How much sanity a successful conceptual theft costs. */
    const val CONCEPTUAL_CORRUPTION = 1

    // --- Power Theft, Seq. 5 tier (wiki 3b.5) ---
    /** 50 m at Seq. 6 -> 80 m here. */
    const val POWER_THEFT_RANGE = CONCEPTUAL_RANGE

    /** can be used for half an hour — against Prometheus' 10 min. */
    const val POWER_STOLEN_DURATION_MILLIS = 30L * 60_000L

    /** The victim cannot call the stolen power back for twice as long as the thief holds it. */
    const val POWER_SUPPRESSION_DURATION_MILLIS = 60L * 60_000L

    /**
     * If ... has not yet used the Stolen power ... it can be kept for a week — the week is
     * compressed to 6 h of real time: the slot stays occupied that whole time.
     */
    const val POWER_UNUSED_HOLD_MILLIS = 6L * 60L * 60_000L

    // --- "Dream" mode ---
    /** How long the thief holds someone else's dream (and how long the victim cannot sleep). */
    const val DREAM_HOLD_MILLIS = 30L * 60_000L

    /** How long a stolen dream poured into a third person lasts (wiki: infuse scenes within the Dream). */
    const val DREAM_INFUSION_SECONDS = 15

    // --- "General abilities" mode ---
    /** Walking / flying / casting — taken away for this long. */
    const val GENERAL_ABILITY_SECONDS = 20

    // --- "Attack" mode ---
    /** The window in which the first incoming blow is absorbed whole. */
    const val ATTACK_WINDOW_SECONDS = 5

    /** How long the charge lives after the absorption, until the thief strikes. */
    const val ATTACK_CHARGE_SECONDS = 10

    /** Ceiling on the returned damage — so a fifty-point hall does not come back. */
    const val ATTACK_CHARGE_CAP = 20.0

    // --- "Heart" mode ---
    /** 4 hearts = 8.0 units of max health moved from the victim to the thief. */
    const val HEART_HEALTH_TRANSFER = 8.0
    const val HEART_DURATION_SECONDS = 60

    /** The victim keeps at least one heart: theft is not murder. */
    private const val HEART_MIN_REMAINING = 2.0

    // --- "Ideal" mode ---
    /** The share of the victim's CURRENT spirituality that flows to the thief. */
    private const val IDEAL_SPIRITUALITY_SHARE = 0.15

    /** How long the apathy lasts (no sprinting, no experience, weakness). */
    const val IDEAL_APATHY_SECONDS = 60

    // --- Illusion decryption, Seq. 5 tier ---
    /** How long until a hostile illusion on the Dream Stealer himself falls apart on its own. */
    const val ILLUSION_DISPEL_SECONDS = 2

    // --- Disguise ---
    const val DISGUISE_COST = 140
    const val DISGUISE_COOLDOWN_SECONDS = 600
    const val DISGUISE_DURATION_MILLIS = 10L * 60_000L

    /** The Disguise costs more sanity than an ordinary theft — it is a rite, not a flick of the hand. */
    const val DISGUISE_CORRUPTION = 3

    /** Damage multiplier of the stolen answer to a prayer while the Disguise holds. */
    const val DISGUISE_DAMAGE_MULTIPLIER = 1.25

    /** How long the exposure lasts: a failed rite leaves the thief without abilities. */
    const val DISGUISE_EXPOSURE_SECONDS = 500

    /** How many lit candles the Disguise altar needs (as in Ritual Magic). */
    const val DISGUISE_CANDLES_REQUIRED = 3

    private const val DISGUISE_BASE_CHANCE = 0.90
    private const val DISGUISE_CHANCE_PER_ACCESS = 0.06
    private const val DISGUISE_MIN_CHANCE = 0.25

    // --- Success chance ---
    private const val BASE_CHANCE = 0.50
    private const val CHANCE_PER_RECIPE = 0.03
    private const val MAX_RECIPE_BONUS = 0.25
    private const val CHANCE_PER_VICTIM_LEVEL = 0.08
    private const val MIN_CHANCE = 0.10
    private const val MAX_CHANCE = 0.95

    /**
     * The chance of a successful conceptual theft — the same shape as Prometheus', but with
     * a Seq. 5 base: knowing the victim's pathway raises it, a stronger victim lowers it.
     *
     * @param unlockedVictimPathwayRecipes how many recipes of the victim's pathway the thief has unlocked
     * @param victimSequence               the victim's Sequence
     * @return the chance within 0.10..0.95
     */
    fun successChance(unlockedVictimPathwayRecipes: Int, victimSequence: Sequence): Double {
        val knowledge = min(
            MAX_RECIPE_BONUS,
            max(0, unlockedVictimPathwayRecipes) * CHANCE_PER_RECIPE,
        )
        val levelsStronger = max(0, BASE_SEQUENCE - victimSequence.level)
        val resistance = levelsStronger * CHANCE_PER_VICTIM_LEVEL
        return (BASE_CHANCE + knowledge - resistance).coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    /**
     * How much spirituality actually flows over in "Ideal" mode.
     *
     * Two clamps. The first — take 15 % of the victim's CURRENT spirituality, not of the
     * maximum: a drained victim must not be a better target than a full one. The second —
     * never more than the thief is missing from his own maximum: the surplus burns away, so
     * an ally cannot be milked like a battery.
     *
     * @param victimCurrentSpirituality the victim's current spirituality
     * @param thiefMissingSpirituality  how much the thief is missing from his maximum
     * @return the transferred amount, never negative
     */
    fun idealSpiritualityTransfer(
        victimCurrentSpirituality: Int,
        thiefMissingSpirituality: Int,
    ): Int {
        val taken = (max(0, victimCurrentSpirituality) * IDEAL_SPIRITUALITY_SHARE).roundToInt()
        return min(taken, max(0, thiefMissingSpirituality))
    }

    /**
     * How much max health actually moves over in "Heart" mode.
     *
     * Less is taken from a weak victim: after the theft it keeps at least one heart,
     * otherwise the mode would just be murder on a delay.
     *
     * @param victimMaxHealth the victim's current max health
     * @return the transferred amount, never negative
     */
    fun heartTransfer(victimMaxHealth: Double): Double =
        max(0.0, min(HEART_HEALTH_TRANSFER, victimMaxHealth - HEART_MIN_REMAINING))

    /**
     * The chance the Disguise holds. Wiki: the higher the intelligence of the corresponding
     * True Deity, the greater the probability for the Disguise to fail — there are no deities
     * in the game, so the weight of the church itself stands in for intelligence: how many
     * pathways it holds under it.
     *
     * @param pathwayAccesses how many pathways are in the church's domain (`Institution.accesses()`)
     * @return the chance within 0.25..0.90
     */
    fun disguiseSuccessChance(pathwayAccesses: Int): Double {
        val penalty = max(0, pathwayAccesses) * DISGUISE_CHANCE_PER_ACCESS
        return (DISGUISE_BASE_CHANCE - penalty).coerceIn(DISGUISE_MIN_CHANCE, DISGUISE_BASE_CHANCE)
    }
}
