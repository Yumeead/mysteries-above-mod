package me.vangoo.mysteries.pathways.fool

import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.creature.ai.ChaseGoal
import me.vangoo.mysteries.creature.ai.StanceGoal
import me.vangoo.mysteries.entity.MarionetteEntity.Order
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import java.util.EnumSet

/**
 * A seized mob's whole will. It stands where it was left, walks to a spot, keeps at the
 * Marionettist's heels or goes for the enemy the threads name, and does nothing else. Its own
 * pathfinding does the walking, and there is no teleport anywhere: a puppet left behind runs,
 * hops the step and drops off the ledge after its master like anything with legs.
 *
 * A fight is the one thing it hands back. A body that knows how to fight (a zombie's grab, a
 * skeleton's bow, a creeper's fuse, a blaze's fireballs, a creature's techniques) keeps those goals
 * when it is seized ([fights]); once the threads name a target, this goal steps aside and they do
 * the fighting. A body that does not (a pig, a villager) is walked up to the enemy here and strikes
 * with what it has.
 *
 * Above every vanilla goal, as the creature engine's overrides are: vanilla's own start at 0.
 * Below the creature engine's windup, so a technique being wound up still holds the body still.
 */
internal class PuppetGoal(private val mob: Mob) : Goal() {

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    /** Whether the body brought a fight of its own. The goals are fixed when it is tamed, so once is enough. */
    private val fightsItself = mob.goalSelector.availableGoals.any { fights(it.goal) }

    private var repathAt = 0
    private var swingReadyAt = 0

    override fun canUse(): Boolean = !(fightsItself && Marionettes.stepOf(mob) is Order.Attack)

    override fun canContinueToUse(): Boolean = canUse()

    override fun requiresUpdateEveryTick(): Boolean = true

    override fun stop() {
        mob.navigation.stop()
    }

    override fun tick() {
        when (val step = Marionettes.stepOf(mob)) {
            is Order.GoTo -> if (mob.position().closerThan(step.target, ARRIVAL)) {
                mob.navigation.stop()
                Marionettes.command(mob, Order.Stand)
            } else {
                walk(step.target.x, step.target.y, step.target.z, WALK_SPEED)
            }
            is Order.Follow -> Marionettes.ownerBeside(mob)?.let(::follow) ?: mob.navigation.stop()
            is Order.Attack -> Marionettes.victimOf(mob, step)?.let(::strike) ?: mob.navigation.stop()
            else -> mob.navigation.stop()
        }
    }

    private fun follow(owner: ServerPlayer) {
        mob.lookControl.setLookAt(owner, LOOK_SPEED, mob.maxHeadXRot.toFloat())
        val distance = mob.distanceTo(owner)
        if (distance <= FOLLOW_GAP) {
            mob.navigation.stop()
            return
        }
        walk(owner.x, owner.y, owner.z, if (distance > RUN_GAP) RUN_SPEED else WALK_SPEED)
    }

    /** Up to the enemy and a blow: its own damage if the body has any, a creature's strike, else a shove. */
    private fun strike(victim: LivingEntity) {
        mob.lookControl.setLookAt(victim, LOOK_SPEED, mob.maxHeadXRot.toFloat())
        if (!mob.isWithinMeleeAttackRange(victim)) {
            walk(victim.x, victim.y, victim.z, RUN_SPEED)
            return
        }
        mob.navigation.stop()
        if (mob.tickCount < swingReadyAt) return
        swingReadyAt = mob.tickCount + SWING_TICKS
        val level = mob.level() as? ServerLevel ?: return
        val mind = Creatures.mindOf(mob)
        when {
            mind != null -> mind.strike(victim)
            mob.getAttribute(Attributes.ATTACK_DAMAGE) != null -> {
                mob.swing(InteractionHand.MAIN_HAND)
                mob.doHurtTarget(level, victim)
            }
            else -> {
                mob.swing(InteractionHand.MAIN_HAND)
                victim.hurtServer(level, level.damageSources().mobAttack(mob), BARE_BLOW)
            }
        }
    }

    /**
     * Paths there, again every half second or when the path runs out: the Marionettist keeps moving.
     * Where no path exists (a flier in the air, a ledge it cannot path down) it heads straight at it.
     *
     * ponytail: a phantom steers by its own swoop point and ignores both, so a seized phantom only
     * circles. Give it a goal of its own if phantoms are ever taken on the threads.
     */
    private fun walk(x: Double, y: Double, z: Double, speed: Double) {
        if (mob.tickCount < repathAt && !mob.navigation.isDone) return
        repathAt = mob.tickCount + REPATH_TICKS
        if (!mob.navigation.moveTo(x, y, z, speed)) mob.moveControl.setWantedPosition(x, y, z, speed)
    }

    companion object {

        const val PRIORITY = -1

        /**
         * A goal by which a body fights: kept when the mob is seized. Read off the class name, as
         * the creature engine does, because vanilla's attack goals share no base class: a zombie's
         * `ZombieAttackGoal`, a creeper's `SwellGoal`, a spider's `LeapAtTargetGoal`, a ghast's
         * `GhastShootFireballGoal`, an evoker's spell goals, a vex's charge.
         */
        fun fights(goal: Goal): Boolean =
            goal is ChaseGoal || goal is StanceGoal || FIGHTING.any { it in goal.javaClass.simpleName }

        private val FIGHTING = listOf("Attack", "Swell", "Leap", "Shoot", "Spell", "Charge")

        /** Arrival, and how close following comes before it stops; further than [RUN_GAP] it runs. */
        private const val ARRIVAL = 1.2
        private const val FOLLOW_GAP = 3f
        private const val RUN_GAP = 8f

        private const val WALK_SPEED = 1.0
        private const val RUN_SPEED = 1.4
        private const val REPATH_TICKS = 10
        private const val LOOK_SPEED = 10f

        /** A blow about as often as a zombie's, and what a body without any attack deals. */
        private const val SWING_TICKS = 20
        private const val BARE_BLOW = 1f
    }
}
