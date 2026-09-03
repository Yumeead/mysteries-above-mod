package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 6 of the Error pathway (Prometheus).
 *
 * One source of truth for Power Theft, for Prometheus' tier of Material Theft and for the
 * treasure tier of Superior Observation. Pure math — no platform; the effects live in
 * `pathways.error.abilities`.
 *
 * The key rule from the wiki: the better you understand the target, the easier it is to steal
 * exactly what you need. Modelled through [successChance] — unlocked recipes of the victim's
 * pathway raise the chance, a stronger victim lowers it.
 */
object PrometheusTheft {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 6

    // --- Power Theft ---
    const val POWER_THEFT_COST = 200
    const val POWER_THEFT_COOLDOWN_SECONDS = 200
    private const val POWER_THEFT_COOLDOWN_FLOOR_SECONDS = 120L
    const val POWER_THEFT_RANGE = 50.0

    /** How long a stolen ability lives with the thief (real time). */
    const val STOLEN_DURATION_MILLIS = 10L * 60_000L

    /** How long the victim cannot cast the stolen ability (from the moment of the theft). */
    const val SUPPRESSION_DURATION_MILLIS = 30L * 60_000L

    /** One stolen ability at a time — its own slot, independent of `Analysis`. */
    const val MAX_CONCURRENT_STOLEN = 1

    // --- Success chance ---
    private const val BASE_CHANCE = 0.40
    private const val CHANCE_PER_RECIPE = 0.03
    private const val MAX_RECIPE_BONUS = 0.25
    private const val CHANCE_PER_VICTIM_LEVEL = 0.08
    private const val MIN_CHANCE = 0.10
    private const val MAX_CHANCE = 0.95

    // --- Prometheus' tier of inherited abilities ---
    /** Material Theft range at Seq 6 (against 5 at Seq 8/7). */
    const val MATERIAL_THEFT_RANGE = 50.0

    /** Search radius for a flame to steal fire from (wiki 3b.1) — a cube over blocks, hence small. */
    const val FIRE_THEFT_RADIUS = 10.0

    /** How many seconds of fire resistance the stolen flame grants. */
    const val FIRE_THEFT_RESISTANCE_SECONDS = 20

    /** Radius for sensing valuables: containers, drops, other people's inventories. */
    const val VALUABLES_RADIUS = 50.0

    /** Ore scan radius (smaller — this is a cubic pass over blocks). */
    const val ORE_SCAN_RADIUS = 16.0

    /** From how many valuable units a stash counts as rich. */
    const val TREASURE_RICH = 10

    /** From how many — as average; below that it is a trifle. */
    const val TREASURE_MEDIUM = 3

    // --- Mental Resilience ---
    /** How much of the incoming sanity loss remains under Mental Resilience. */
    const val MENTAL_FORTITUDE_SANITY_MULTIPLIER = 0.5

    /** Once every this many seconds Mental Resilience restores 1 sanity on its own. */
    const val MENTAL_FORTITUDE_RECOVERY_SECONDS = 45

    /** Power Theft cooldown (sec): Seq 6 = 300, shortens MODERATE, never below the floor. */
    fun powerTheftCooldownSeconds(sequence: Sequence): Int {
        val seconds = POWER_THEFT_COOLDOWN_SECONDS * relative(sequence, ScalingStrategy.MODERATE)
        return max(POWER_THEFT_COOLDOWN_FLOOR_SECONDS, seconds.roundToLong()).toInt()
    }

    /**
     * The chance of a successful power theft.
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

    /** Whether the menu will show the victim's real ability name (wiki 3a.2). */
    fun revealsAbilityNames(unlockedVictimPathwayRecipes: Int): Boolean =
        unlockedVictimPathwayRecipes > 0

    /** How much sanity flows from the victim to the thief in the Madness slot: Seq 6 = 3, grows WEAK. */
    fun corruptionTransfer(sequence: Sequence): Int =
        (3.0 / relative(sequence, ScalingStrategy.WEAK)).roundToInt()

    /** Ratio of power at Seq 6 to power at the given Sequence (below 1.0 for stronger ones). */
    private fun relative(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy) /
            SequenceScaler.calculateMultiplier(sequence.level, strategy)
}
