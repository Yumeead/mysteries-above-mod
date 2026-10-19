package me.vangoo.mysteries.creature.ai

import me.vangoo.mysteries.creature.Mind
import me.vangoo.mysteries.creature.Stance
import me.vangoo.mysteries.creature.flatDirection
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import java.util.EnumSet

/**
 * The mind's own pulse: auras, boss bars, grudges fading, the despawn check. No flags — it never
 * takes a limb from anything, it just has to run every tick the body does.
 */
class HeartbeatGoal(private val mind: Mind) : Goal() {
    override fun canUse(): Boolean = true
    override fun requiresUpdateEveryTick(): Boolean = true
    override fun tick() = mind.heartbeat()
}

/**
 * Whom to fight. Rescans twice a second; keeps the current target while it is still worth it, so a
 * target someone else gave the creature — a summoner's victim, a Retinue order — is not dropped for
 * want of a grudge.
 */
class ChooseTargetGoal(private val mind: Mind) : Goal() {
    init {
        flags = EnumSet.of(Flag.TARGET)
    }

    override fun canUse(): Boolean = !mind.isOwned
    override fun canContinueToUse(): Boolean = !mind.isOwned

    /**
     * Every tick, and the scan counts its own ten. Vanilla runs a goal's full tick only on every
     * other tick, and which other is decided by `tickCount + entityId`: a `tickCount % 10` test
     * here met it only for creatures with an even id, and the other half never chose a target at
     * all. The game tests caught it as a "flaky" hunt.
     */
    override fun requiresUpdateEveryTick(): Boolean = true

    override fun tick() {
        if (mind.now % SCAN_EVERY != 0L) return
        val current = mind.mob.target
        if (current != null && !mind.stillWorthFighting(current)) mind.engage(null)
        val choice = mind.chooseTarget()
        if (choice != null) mind.engage(choice)
    }

    override fun stop() {
        // Owned now: the will is someone else's, and so is the target.
        if (mind.isOwned) return
        mind.engage(null)
    }

    private companion object {
        const val SCAN_EVERY = 10L
    }
}

/** Picks techniques from the rotation and fires the reflexes. No flags: it casts while other goals move. */
class CastGoal(private val mind: Mind) : Goal() {
    override fun canUse(): Boolean = mind.mob.target != null && mind.kit != null
    override fun requiresUpdateEveryTick(): Boolean = true
    override fun tick() = mind.combatTick()
}

/**
 * A wound-up technique holds the body still: it takes the legs and the head for exactly as long
 * as the windup lasts, so the telegraph is honest — the caster is not secretly walking on.
 */
class ChannelGoal(private val mind: Mind) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)
    }

    override fun canUse(): Boolean = mind.channel != null
    override fun isInterruptable(): Boolean = false
    override fun requiresUpdateEveryTick(): Boolean = true

    override fun start() {
        mind.mob.navigation.stop()
    }

    override fun tick() = mind.channelTick()
}

/**
 * The plugin's `rangedstance`: keep the target in a band, cast from it, step back when it closes
 * in — and charge when provoked, because a caster cornered at arm's length fights with its hands.
 */
class StanceGoal(private val mind: Mind, private val stance: Stance) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    private var swingReadyAt = 0L

    override fun canUse(): Boolean = mind.mob.target?.isAlive == true
    override fun requiresUpdateEveryTick(): Boolean = true

    override fun stop() {
        mind.mob.navigation.stop()
    }

    override fun tick() {
        val mob = mind.mob
        val target = mob.target ?: return
        val distance = mob.distanceTo(target)
        mob.lookControl.setLookAt(target, LOOK_SPEED, LOOK_SPEED)
        if (distance < PROVOKE_RANGE) mind.provokedUntil = mind.now + PROVOKED_TICKS
        when {
            mind.now < mind.provokedUntil -> {
                mob.navigation.moveTo(target, CHARGE_SPEED)
                if (mob.isWithinMeleeAttackRange(target) && mind.now >= swingReadyAt) {
                    swingReadyAt = mind.now + SWING_TICKS
                    mind.strike(target)
                }
            }
            distance > stance.max -> mob.navigation.moveTo(target, APPROACH_SPEED)
            distance < stance.min -> backOff()
            else -> mob.navigation.stop()
        }
    }

    private fun backOff() {
        val mob = mind.mob
        val target = mob.target ?: return
        if (mind.now % REPATH_EVERY != 0L && mob.navigation.isInProgress) return
        val away = if (mob is PathfinderMob) DefaultRandomPos.getPosAway(mob, BACKOFF_RANGE, 4, target.position()) else null
        val spot = away ?: mob.position().add(flatDirection(target.position(), mob.position()).scale(BACKOFF_RANGE.toDouble()))
        mob.navigation.moveTo(spot.x, spot.y, spot.z, BACKOFF_SPEED)
    }

    private companion object {
        const val LOOK_SPEED = 30f
        const val PROVOKE_RANGE = 3f
        const val PROVOKED_TICKS = 80L
        const val SWING_TICKS = 20L
        const val REPATH_EVERY = 10L
        const val BACKOFF_RANGE = 7
        const val APPROACH_SPEED = 1.05
        const val CHARGE_SPEED = 1.15
        const val BACKOFF_SPEED = 1.15
    }
}

/** Chase and strike with the body — for bodies vanilla gave no attack: a goat, a chicken, a human. */
class ChaseGoal(private val mind: Mind) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    private var swingReadyAt = 0L

    override fun canUse(): Boolean = mind.mob.target?.isAlive == true
    override fun requiresUpdateEveryTick(): Boolean = true

    override fun stop() {
        mind.mob.navigation.stop()
    }

    override fun tick() {
        val mob = mind.mob
        val target = mob.target ?: return
        mob.lookControl.setLookAt(target, 30f, 30f)
        if (mind.now % REPATH_EVERY == 0L || mob.navigation.isDone) mob.navigation.moveTo(target, CHASE_SPEED)
        if (mob.isWithinMeleeAttackRange(target) && mob.sensing.hasLineOfSight(target) && mind.now >= swingReadyAt) {
            swingReadyAt = mind.now + SWING_TICKS
            mind.strike(target)
        }
    }

    private companion object {
        const val REPATH_EVERY = 10L
        const val SWING_TICKS = 20L
        const val CHASE_SPEED = 1.2
    }
}

/**
 * Running for one's life. What from is the mind's answer ([Mind.shouldFlee]): a wound too deep, a
 * Beyonder two Sequences up, a monster at a citizen's elbow.
 */
class FleeGoal(private val mind: Mind, private val mob: PathfinderMob) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    private var checkedAt = Long.MIN_VALUE / 2
    private var from: net.minecraft.world.entity.LivingEntity? = null

    override fun canUse(): Boolean {
        if (mind.now - checkedAt < CHECK_EVERY) return false
        checkedAt = mind.now
        from = mind.shouldFlee() ?: return false
        return run()
    }

    override fun canContinueToUse(): Boolean {
        // Asked only on vanilla's full goal ticks, whose parity is the entity's own — so by elapsed time, not by `now % n`.
        if (mind.now - checkedAt >= CHECK_EVERY) {
            checkedAt = mind.now
            from = mind.shouldFlee()
        }
        val danger = from ?: return false
        return !mob.navigation.isDone || run(danger)
    }

    override fun start() {
        mind.say(if (mind.isPanicking()) mind.spec.speech.panic else mind.spec.speech.flee)
    }

    override fun stop() {
        from = null
    }

    private fun run(danger: net.minecraft.world.entity.LivingEntity? = from): Boolean {
        val away = DefaultRandomPos.getPosAway(mob, FLEE_RANGE, 7, (danger ?: return false).position()) ?: return false
        return mob.navigation.moveTo(away.x, away.y, away.z, FLEE_SPEED)
    }

    private companion object {
        const val CHECK_EVERY = 10L
        const val FLEE_RANGE = 16
        const val FLEE_SPEED = 1.35
    }
}

/** A boss past its leash walks home; [Mind.reset] has already made it whole and forgetful. */
class LeashGoal(private val mind: Mind) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean = mind.returning

    override fun requiresUpdateEveryTick(): Boolean = true

    override fun tick() {
        if (mind.now % 20L != 0L && mind.mob.navigation.isInProgress) return
        val home = mind.anchors.homeVec
        if (!mind.mob.navigation.moveTo(home.x, home.y, home.z, 1.2)) {
            // No path back (it was knocked off a cliff, walled in): it is a boss, it simply returns.
            mind.mob.teleportTo(home.x, home.y, home.z)
        }
    }
}
