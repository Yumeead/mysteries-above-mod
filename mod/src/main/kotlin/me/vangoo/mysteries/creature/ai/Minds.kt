package me.vangoo.mysteries.creature.ai

import me.vangoo.mysteries.creature.Combat
import me.vangoo.mysteries.creature.Mind
import me.vangoo.mysteries.creature.Role
import me.vangoo.mysteries.creature.Temperament
import me.vangoo.mysteries.entity.HumanEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.OpenDoorGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.player.Player

/**
 * Lays a [Mind]'s goals onto its body.
 *
 * ## What is kept from vanilla, and why
 *
 * A vanilla body already knows how to move itself: the vex flies through walls at its target, the
 * guardian charges its beam, the slime hops, the phantom swoops. Thirty bodies would mean thirty
 * movement systems to rewrite, and MythicMobs never rewrote them either. So the mind takes over
 * exactly two things and leaves the rest:
 *
 * | | vanilla | the mind |
 * |---|---|---|
 * | whom to fight | cleared | [ChooseTargetGoal] — grudges, Sequence pressure, temperament |
 * | which technique, when | — | [CastGoal], [ChannelGoal] |
 * | how to move in a fight | its own attack goal | only when it has none, or fights at range |
 * | running away, a day's routine | — | [FleeGoal], [RoutineGoal], [LeashGoal] |
 *
 * A body without an attack of its own — a goat, a chicken, every brain-driven mob, a human — gets
 * the whole set: [ChaseGoal] and the idle goals a monster would have had.
 *
 * Priorities follow vanilla's rule: a lower number wins the flags it needs. Fleeing and a wound-up
 * technique outrank fighting; fighting outranks the routine; the routine outranks idling.
 */
object Minds {

    fun install(mind: Mind) {
        val mob = mind.mob
        val kit = mind.kit
        val goals = mob.goalSelector
        val ranged = kit?.stance != null
        val ownBody = mob is HumanEntity || mind.spec.mind.combat == Combat.OWN ||
            (mind.spec.mind.combat == Combat.AUTO && !hasNativeAttack(mob))

        mob.targetSelector.removeAllGoals { true }
        // Brain-driven bodies (the goat, the frog, the piglin, the zoglin) would steer against every goal below.
        mob.brain.removeAllBehaviors()
        if (ownBody || ranged) goals.removeAllGoals { true }

        goals.addGoal(0, HeartbeatGoal(mind))
        if (ownBody || ranged) goals.addGoal(0, FloatGoal(mob))
        goals.addGoal(1, ChannelGoal(mind))
        if (mob is PathfinderMob) {
            goals.addGoal(1, FleeGoal(mind, mob))
            if (mind.spec.boss != null) goals.addGoal(1, LeashGoal(mind))
        }
        goals.addGoal(2, CastGoal(mind))
        when {
            ranged -> goals.addGoal(3, StanceGoal(mind, kit.stance))
            ownBody && mind.temperament != Temperament.TIMID -> goals.addGoal(3, ChaseGoal(mind))
        }
        if (mob is PathfinderMob && (mind.role == Role.CITIZEN || mind.role == Role.GUARD)) {
            goals.addGoal(4, RoutineGoal(mind, mob))
        }
        if (mob is HumanEntity) {
            goals.addGoal(4, OpenDoorGoal(mob, true))
            goals.addGoal(6, SocialGoal(mind))
        }
        if (ownBody || ranged) {
            if (mob is PathfinderMob && mind.role != Role.CITIZEN && mind.role != Role.GUARD) {
                goals.addGoal(5, WaterAvoidingRandomStrollGoal(mob, STROLL_SPEED))
            }
            // A human looks at people through `SocialGoal`; a vanilla glance would turn a sleeper's head.
            if (mob !is HumanEntity) {
                goals.addGoal(7, LookAtPlayerGoal(mob, Player::class.java, LOOK_DISTANCE))
                goals.addGoal(8, RandomLookAroundGoal(mob))
            }
        }

        if (mind.temperament != Temperament.WILLLESS) mob.targetSelector.addGoal(1, ChooseTargetGoal(mind))
    }

    /**
     * Whether vanilla gave this body an attack goal: a zombie's, a wolf's, a guardian's beam, a
     * vex's charge, a witch's potions. Read off the goal's class name because the attack goals share
     * no base class — `MeleeAttackGoal`, `RangedAttackGoal`, `VexChargeAttackGoal`, `SlimeAttackGoal`…
     */
    private fun hasNativeAttack(mob: Mob): Boolean =
        mob.goalSelector.availableGoals.any { "Attack" in it.goal.javaClass.simpleName }

    private const val STROLL_SPEED = 1.0
    private const val LOOK_DISTANCE = 8f
}
