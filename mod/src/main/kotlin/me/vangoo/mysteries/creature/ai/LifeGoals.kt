package me.vangoo.mysteries.creature.ai

import me.vangoo.domain.creatures.DailyRoutine
import me.vangoo.domain.creatures.DailyRoutine.Activity
import me.vangoo.mysteries.creature.Mind
import me.vangoo.mysteries.creature.Role
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

/** What a town-dweller should be doing at this hour. */
internal fun Mind.activity(): Activity {
    val time = level.defaultClockTime
    return if (role == Role.GUARD) {
        // Half the guards stand the night watch; which half is fixed by the person, not rolled nightly.
        DailyRoutine.guard(time, nightWatch = mob.uuid.leastSignificantBits and 1L == 0L)
    } else {
        DailyRoutine.citizen(time)
    }
}

/**
 * A day in a town: to work in the morning, out among people in the afternoon, home at dusk, to
 * bed at night; for a guard, rounds about the post. The places are the person's own
 * ([me.vangoo.mysteries.creature.Anchors]) — found once when they were made, kept with them.
 *
 * The hours are the vanilla villager's, so a town of humans and a village of villagers wake and
 * sleep together.
 */
class RoutineGoal(private val mind: Mind, private val mob: PathfinderMob) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    private var nextStepAt = 0L

    override fun canUse(): Boolean =
        mob.target == null && mind.channel == null && !mind.isOwned && !mind.isPanicking()

    override fun requiresUpdateEveryTick(): Boolean = true

    override fun stop() {
        if (mob.isSleeping && mind.activity() != Activity.SLEEP) mob.stopSleeping()
    }

    override fun tick() {
        val activity = mind.activity()
        if (mob.isSleeping) {
            if (activity != Activity.SLEEP) mob.stopSleeping() else return
        }
        if (mind.now < nextStepAt) return
        nextStepAt = mind.now + STEP_TICKS + mob.random.nextInt(STEP_TICKS.toInt())
        val anchors = mind.anchors
        when (activity) {
            Activity.SLEEP -> anchors.bed?.let(::goToBed) ?: wander(anchors.home, HOME_RADIUS)
            Activity.HOME -> wander(anchors.home, HOME_RADIUS)
            Activity.WORK -> work(anchors.work ?: anchors.home)
            Activity.GATHER -> wander(anchors.gather ?: anchors.home, GATHER_RADIUS)
            Activity.PATROL -> wander(anchors.work ?: anchors.home, PATROL_RADIUS)
        }
    }

    private fun goToBed(bed: BlockPos) {
        val state = mob.level().getBlockState(bed)
        if (!state.`is`(BlockTags.BEDS)) {
            wander(mind.anchors.home, HOME_RADIUS)
            return
        }
        if (mob.distanceToSqr(Vec3.atCenterOf(bed)) <= BED_REACH * BED_REACH) {
            mob.navigation.stop()
            mob.startSleeping(bed)
        } else {
            mob.navigation.moveTo(bed.x + 0.5, bed.y.toDouble(), bed.z + 0.5, WALK_SPEED)
        }
    }

    /** Busy at the workstation: stands by it, turns to it, works it with their hands now and then. */
    private fun work(place: BlockPos) {
        val spot = Vec3.atCenterOf(place)
        if (mob.distanceToSqr(spot) > WORK_REACH * WORK_REACH) {
            mob.navigation.moveTo(spot.x, spot.y, spot.z, WALK_SPEED)
            return
        }
        mob.lookControl.setLookAt(spot)
        if (mob.random.nextInt(3) == 0) mob.swing(InteractionHand.MAIN_HAND)
        if (mob.random.nextInt(WORK_TALK_ODDS) == 0) mind.say(mind.spec.speech.work)
    }

    private fun wander(around: BlockPos, radius: Int) {
        val dx = mob.random.nextInt(radius * 2 + 1) - radius
        val dz = mob.random.nextInt(radius * 2 + 1) - radius
        mob.navigation.moveTo(around.x + dx + 0.5, around.y.toDouble(), around.z + dz + 0.5, WALK_SPEED)
    }

    private companion object {
        const val STEP_TICKS = 100L
        const val WALK_SPEED = 0.7
        const val HOME_RADIUS = 3
        const val GATHER_RADIUS = 8
        const val PATROL_RADIUS = 16
        const val BED_REACH = 2.0
        const val WORK_REACH = 2.5
        const val WORK_TALK_ODDS = 12
    }
}

/**
 * Turning to a person who came near and having a word with them — a greeting by day, a hushed
 * line by night. Fellow townsfolk at the square get a look too: a crowd that never looks at itself
 * reads as a crowd of statues.
 */
class SocialGoal(private val mind: Mind) : Goal() {
    init {
        flags = EnumSet.of(Flag.LOOK)
    }

    private var other: LivingEntity? = null
    private var until = 0L
    private var restUntil = 0L

    override fun canUse(): Boolean {
        val mob = mind.mob
        if (mob.target != null || mob.isSleeping || mind.now < restUntil || mob.random.nextInt(LOOK_ODDS) != 0) return false
        other = mob.level().getNearestPlayer(mob, PLAYER_RANGE)?.takeIf { !it.isSpectator }
            ?: mob.level().getEntitiesOfClass(PathfinderMob::class.java, mob.boundingBox.inflate(PEER_RANGE)) {
                it !== mob && me.vangoo.mysteries.creature.Creatures.areAllies(mob, it)
            }.randomOrNull()
        return other != null
    }

    override fun canContinueToUse(): Boolean =
        other?.isAlive == true && mind.mob.target == null && !mind.mob.isSleeping && mind.now < until

    override fun start() {
        until = mind.now + LOOK_TICKS + mind.mob.random.nextInt(LOOK_TICKS.toInt())
        val player = other as? ServerPlayer ?: return
        val night = mind.activity().let { it == DailyRoutine.Activity.SLEEP || it == DailyRoutine.Activity.HOME }
        val lines = if (night) mind.spec.speech.night.ifEmpty { mind.spec.speech.greet } else mind.spec.speech.greet
        mind.greet(player, lines)
    }

    override fun stop() {
        other = null
        restUntil = mind.now + REST_TICKS
    }

    override fun tick() {
        other?.let { mind.mob.lookControl.setLookAt(it, 10f, mind.mob.maxHeadXRot.toFloat()) }
    }

    private companion object {
        const val LOOK_ODDS = 20
        const val PLAYER_RANGE = 6.0
        const val PEER_RANGE = 4.0
        const val LOOK_TICKS = 40L
        const val REST_TICKS = 60L
    }
}
