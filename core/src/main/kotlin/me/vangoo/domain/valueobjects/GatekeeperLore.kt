package me.vangoo.domain.valueobjects

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Balance numbers for Sequence 5 of the Death pathway (Gatekeeper).
 *
 * Seq. 5 is the first of this tier; the numbers of weaker tiers live in [SpiritGuideLore],
 * [SpiritMediumLore], [GravediggerLore], [CorpseCollectorLore]. The class fills up as the
 * plan's milestones land — numbers and [growth] are added together with the first ability
 * that actually needs them (at Seq. 5 the multiplier is 1.0).
 */
object GatekeeperLore {

    /** The Sequence the tier appears at; every base number is stated for it. */
    private const val BASE_SEQUENCE = 5

    // --- Gatekeeper body (PhysicalEnhancement, shared identity "death_physique") ---
    const val PHYSIQUE_HP_BASE = 10

    // --- Gatekeeper's Step (a dash forward) ---
    const val STEP_COST = 20
    private const val STEP_COOLDOWN_SECONDS = 8
    private const val STEP_COOLDOWN_FLOOR_SECONDS = 4
    private const val STEP_DISTANCE = 8.0
    private const val STEP_DISTANCE_CAP = 12.0
    private const val STEP_IMPACT_DAMAGE = 5

    /** Step cooldown (sec): Seq. 5 = 8, shortens WEAK, never below the floor. */
    fun stepCooldownSeconds(sequence: Sequence): Int =
        shrink(STEP_COOLDOWN_SECONDS, STEP_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Dash range (blocks): Seq. 5 = 8, grows WEAK up to a 12 ceiling. */
    fun stepDistance(sequence: Sequence): Double =
        min(STEP_DISTANCE_CAP, STEP_DISTANCE * growth(sequence, ScalingStrategy.WEAK))

    /** Landing damage: Seq. 5 = 5, grows MODERATE. */
    fun stepImpactDamage(sequence: Sequence): Int =
        (STEP_IMPACT_DAMAGE * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    // --- Door to the Underworld (cost and cooldown shared by all 4 modes) ---
    // Ability.getSpiritualityCost()/getCooldown(Sequence) see neither the caster nor the
    // current mode — the framework physically cannot give four modes four different
    // costs/cooldowns without duplicating AbilityResourceConsumer by hand. The same
    // compromise is already accepted by Verdict (Justiciar, Seq. 6): one cast = one cost
    // regardless of the chosen mode. The numbers come from DRAG — the heaviest mode and
    // the first one implemented.
    const val DOOR_COST = 200
    private const val DOOR_COOLDOWN_SECONDS = 40
    private const val DOOR_COOLDOWN_FLOOR_SECONDS = 10

    // --- Range at which the door can be placed (looking at a point) — shared by all four
    // modes, not just DRAG: no mode takes an entity target any more, each places a gate at
    // the point the player is looking at instead. ---
    private const val DOOR_PLACEMENT_RANGE = 20.0
    private const val DOOR_PLACEMENT_RANGE_CAP = 32.0

    // --- DRAG: suction radius near the gate, damage, execution threshold ---
    private const val DRAG_GATE_AOE_RADIUS = 8.0
    private const val DRAG_GATE_AOE_RADIUS_CAP = 14.0
    private const val DRAG_DAMAGE = 8

    /** Execution threshold — fixed, does not grow with the Sequence. */
    const val DRAG_EXECUTE_HP_FRACTION = 0.25

    /** Wither on the target (sec) — the pace of the effect, not its power, hence constant. */
    const val DRAG_WITHER_SECONDS = 4

    /** Slowness near the gate (sec) — constant for the same reason. */
    const val DRAG_SLOWNESS_SECONDS = 3

    /** Door cooldown (sec): Seq. 5 = 60, shortens WEAK, never below the floor. */
    fun doorCooldownSeconds(sequence: Sequence): Int =
        shrink(DOOR_COOLDOWN_SECONDS, DOOR_COOLDOWN_FLOOR_SECONDS, sequence)

    /** Gate placement range (blocks): Seq. 5 = 20, grows WEAK up to a 32 ceiling. */
    fun doorPlacementRange(sequence: Sequence): Double =
        min(DOOR_PLACEMENT_RANGE_CAP, DOOR_PLACEMENT_RANGE * growth(sequence, ScalingStrategy.WEAK))

    /** Suction radius around the gate (blocks): Seq. 5 = 8, grows WEAK up to a 14 ceiling. */
    fun dragGateAoeRadius(sequence: Sequence): Double =
        min(DRAG_GATE_AOE_RADIUS_CAP, DRAG_GATE_AOE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** DRAG damage on the main target: Seq. 5 = 8, grows MODERATE. */
    fun dragDamage(sequence: Sequence): Int =
        (DRAG_DAMAGE * growth(sequence, ScalingStrategy.MODERATE)).roundToInt()

    // --- PURGE: radius for clearing clouds/effects/fire around the caster ---
    private const val PURGE_RADIUS = 10.0
    private const val PURGE_RADIUS_CAP = 18.0

    /** Purge radius (blocks): Seq. 5 = 10, grows WEAK up to an 18 ceiling. */
    fun purgeRadius(sequence: Sequence): Double =
        min(PURGE_RADIUS_CAP, PURGE_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    // --- BIND: radius of the bound hands around the caster, duration and strength of the effects (fixed) ---
    private const val BIND_RADIUS = 6.0
    private const val BIND_RADIUS_CAP = 11.0

    /** The pace of control, not its power — fixed, like DRAG_SLOWNESS_SECONDS. */
    const val BIND_DURATION_SECONDS = 4

    /** Level index: 4 = Slowness V. */
    const val BIND_SLOWNESS_AMPLIFIER = 4

    /** Bound Hands radius (blocks): Seq. 5 = 6, grows WEAK up to an 11 ceiling. */
    fun bindRadius(sequence: Sequence): Double =
        min(BIND_RADIUS_CAP, BIND_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    // --- DESCEND: the zone's radius/duration grow; the undead bonus and the healing are
    // fixed design numbers and do not depend on the Sequence ---
    private const val DESCEND_RADIUS = 12.0
    private const val DESCEND_RADIUS_CAP = 20.0
    private const val DESCEND_DURATION_SECONDS = 12
    private const val DESCEND_DURATION_CAP_SECONDS = 20L

    /** Bonus to the strike of one's own undead inside the zone: +20%. */
    const val DESCEND_UNDEAD_DAMAGE_BONUS = 0.20

    /** Healing of the owner per second while he is in the zone: 1 HP/s. */
    const val DESCEND_OWNER_HEAL_PER_SECOND = 1.0

    /** Descent zone radius (blocks): Seq. 5 = 12, grows WEAK up to a 20 ceiling. */
    fun descendRadius(sequence: Sequence): Double =
        min(DESCEND_RADIUS_CAP, DESCEND_RADIUS * growth(sequence, ScalingStrategy.WEAK))

    /** Descent zone duration (sec): Seq. 5 = 12, grows MODERATE up to a 20 ceiling. */
    fun descendDurationSeconds(sequence: Sequence): Int = min(
        DESCEND_DURATION_CAP_SECONDS,
        (DESCEND_DURATION_SECONDS * growth(sequence, ScalingStrategy.MODERATE)).roundToLong(),
    ).toInt()

    // --- Inner Underworld: surcharge for an occupant — cheapest base + the difference by
    // hand, the same trick as SpiritPact.pay(); the cast cooldown is shared by all six.
    // Absorbing/evicting are free and have no cooldown: the single occupant slot already
    // stops them from being spammed. ---
    const val UNDERWORLD_CAST_BASE_COST = 300
    private const val UNDERWORLD_CAST_COOLDOWN_SECONDS = 100
    private const val UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS = 30

    const val DEATH_KNIGHT_CAST_COST = 40
    const val SHADOW_PYTHON_CAST_COST = 45
    const val LIVING_SHADOW_CAST_COST = 35
    const val LAKE_GODDESS_CAST_COST = 50
    const val WANDERING_SPIRIT_CAST_COST = 30
    const val RESURRECTED_SERVANT_CAST_COST = 30

    /** -2…-8 max HP depending on the occupant's power; independent of the Sequence. */
    const val DEATH_KNIGHT_EROSION_HP = 5.0
    const val SHADOW_PYTHON_EROSION_HP = 5.0
    const val LIVING_SHADOW_EROSION_HP = 3.0
    const val LAKE_GODDESS_EROSION_HP = 8.0
    const val WANDERING_SPIRIT_EROSION_HP = 2.0
    const val RESURRECTED_SERVANT_EROSION_HP = 3.0

    /** Sanity drain while the occupant lives inside: 1 per this period. */
    const val UNDERWORLD_SANITY_DRAIN_PERIOD_SECONDS = 15
    const val UNDERWORLD_SANITY_DRAIN_AMOUNT = 1

    /** Cooldown of casting the occupant's power (sec): Seq. 5 = 30, shortens WEAK, never below the floor. */
    fun underworldCastCooldownSeconds(sequence: Sequence): Int =
        shrink(UNDERWORLD_CAST_COOLDOWN_SECONDS, UNDERWORLD_CAST_COOLDOWN_FLOOR_SECONDS, sequence)

    // --- The four new spirits of the Spirit World — the price of the PACT (SpiritPact,
    // retinue), separate from the price of CASTING as an occupant above. The Envoy and the
    // Sea Wraith are combat assets (dearer than DEATH_KNIGHT_COST=70); Human Pus and the
    // Pale Girl are passive, cheaper than LIVING_SHADOW_COST=50 (SpiritGuideLore). The wiki
    // gives no exact numbers — this is interpretation. ---
    const val DEATH_ENVOY_RECRUIT_COST = 90
    const val PUS_OF_MAN_RECRUIT_COST = 60
    const val PALE_GIRL_RECRUIT_COST = 60
    const val SEA_BEASTS_RECRUIT_COST = 85

    // --- The same four as Inner Underworld occupants: castCost 0 = passive (the session
    // itself acts, performExecution is never entered on cast), otherwise — from the plan's
    // design table (60/55). ---
    const val DEATH_ENVOY_CAST_COST = 60
    const val SEA_BEASTS_CAST_COST = 55
    const val PUS_OF_MAN_CAST_COST = 0
    const val PALE_GIRL_CAST_COST = 0

    const val DEATH_ENVOY_EROSION_HP = 7.0
    const val SEA_BEASTS_EROSION_HP = 6.0
    const val PUS_OF_MAN_EROSION_HP = 4.0
    const val PALE_GIRL_EROSION_HP = 4.0

    // --- Human Pus: saves from a lethal blow once per 5 min. ---
    const val PUS_OF_MAN_WARD_COOLDOWN_SECONDS = 5 * 60
    const val PUS_OF_MAN_INVISIBILITY_SECONDS = 5
    const val PUS_OF_MAN_WEAKNESS_SECONDS = 10

    // --- Strengthened Spirit Summoning — retinue ceiling 10 -> 12 (slightly increased, but
    // it doesn't change much). The method belongs here rather than in SpiritGuideLore: the
    // step happens exactly at Seq. 5, and GatekeeperLore already looks down at the weaker
    // tiers as its base, whereas the reverse dependency (SpiritGuideLore -> GatekeeperLore)
    // would run against the tier layering. SpiritGuideLore.RETINUE_CAP stays as it is — it
    // is the base for Seq. 6 and weaker. ---
    const val RETINUE_CAP = 12

    /** Retinue ceiling (servants): 10 for Seq. 6+ ([SpiritGuideLore.RETINUE_CAP]), 12 from Seq. 5. */
    fun retinueCap(sequence: Sequence): Int =
        if (sequence.level <= BASE_SEQUENCE) RETINUE_CAP else SpiritGuideLore.RETINUE_CAP

    /** A cooldown that shortens WEAK from the base down to the floor. */
    private fun shrink(baseSeconds: Int, floorSeconds: Int, sequence: Sequence): Int {
        val seconds = baseSeconds / growth(sequence, ScalingStrategy.WEAK)
        return max(floorSeconds.toLong(), seconds.roundToLong()).toInt()
    }

    /** How far power at the given Sequence exceeds power at Seq. 5 (exactly 1.0 there). */
    private fun growth(sequence: Sequence, strategy: ScalingStrategy): Double =
        SequenceScaler.calculateMultiplier(sequence.level, strategy) /
            SequenceScaler.calculateMultiplier(BASE_SEQUENCE, strategy)
}
