package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.net.payload.DoorKind
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Door, Seq. 9 (Apprentice): **Door Opening**.
 *
 * The pathway's first and cheapest power, and already it does what the Door is: it shortens
 * distance. The Apprentice steps up to a wall, and for a moment the wall is not there — exactly
 * for its own thickness.
 *
 * ## The passage is not for oneself but **for the party**
 *
 * From Seq. 7 the passage stays open for five seconds, and anyone who comes up walks through it.
 * That is the same threshold as in the plugin, and it is worth it: a power that saves only
 * yourself and a power that takes a party through a wall are different powers, not one stronger
 * one.
 *
 * Each person goes through **once**: without that, a player who stayed standing by the entrance
 * would be shuttled back and forth every tick.
 *
 * ## The door opens before anyone goes through
 *
 * The first revisions moved the Apprentice in the very tick of the cast, so they never saw their
 * own door. Now a crack runs round the arch, the wall's own face swings open on its jambs, and only
 * then — [DoorKind.WALL]'s `opened`, under half a second — does the passage take whoever stands at
 * it (the user's call, 14.11.2026). `Blink` stays instant: it is a dodge, this is a door.
 */
class DoorOpening : ActiveAbility(AbilityIdentity.of("door_opening")) {

    override val displayName: Component = Component.literal("Відкриття дверей")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Примарні двері в перешкоді перед вами.\n" +
            "▪ Стіна до ${thickness(sequence)} бл. завтовшки\n" +
            (
                if (sequence.level <= SHARED_FROM_SEQUENCE) {
                    "▪ Прохід лишається на ${PORTAL_TICKS / TICKS_PER_SECOND} с — крізь нього " +
                        "пройде будь-хто поблизу\n"
                } else {
                    "▪ З Посл. $SHARED_FROM_SEQUENCE прохід лишається відкритим для інших\n"
                }
                ) +
            "▪ Відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val hit = wallInFront(context)
            ?: return AbilityOutcome.failed("Підійдіть упритул до суцільної стіни")
        // The passage runs square through the face that was touched, not along the gaze: a door
        // stands square in its wall, and its leaves are that wall's own face.
        val outward = hit.direction
        if (outward.axis.isVertical) return AbilityOutcome.failed("Двері відчиняються лише в стіні")
        val through = Vec3(-outward.stepX.toDouble(), 0.0, -outward.stepZ.toDouble())
        // A door stands on the floor the Apprentice stands on, not at the height their eyes met the
        // wall: a glance straight ahead strikes the block above the floor.
        val feet = context.caster.blockPosition().y
        val wall = hit.blockPos.let { if (it.y == feet + 1 && context.level.getBlockState(it.below()).isSolid) it.below() else it }

        val maxThickness = thickness(context.sequence)
        val exit = exitBehind(context.level, wall, outward.opposite, maxThickness)
            ?: run {
                context.level.playSound(
                    null, context.origin.x, context.origin.y, context.origin.z,
                    SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.5f,
                )
                return AbilityOutcome.failed(
                    "Ця стіна товща за $maxThickness бл. або за нею немає місця",
                )
            }

        // Both doors stand at the same height, so the two halves of the passage are level with each
        // other: one on the face in front, one on the far face behind the exit, opening the other way.
        val shared = context.sequence.level <= SHARED_FROM_SEQUENCE
        val hold = if (shared) PORTAL_TICKS else DOOR_FLASH_TICKS
        val depth = corridor(context.sequence)
        val color = context.effects.pathwayColor
        val entrance = Vec3.atBottomCenterOf(wall).subtract(through.scale(0.5))
        val far = Vec3.atBottomCenterOf(exit).subtract(through.scale(0.5))
        doorway(context.scheduling, context.level, entrance, through, hold, color, DoorKind.WALL, depth)
        doorway(context.scheduling, context.level, far, through.reverse(), hold, color, DoorKind.WALL, depth)

        // Who has already gone through. State of one cast, not a field of the ability: the ability
        // instance is shared by every Apprentice on the server.
        val passed = HashSet<UUID>()
        context.session {
            // The leaves open first: a door is walked through, it is not a button.
            waitTicks(DoorKind.WALL.opened)
            sustain(periodTicks = WATCH_PERIOD_TICKS, durationTicks = hold) { opener ->
                val level = opener.level() as? ServerLevel ?: return@sustain
                val reach = AABB.ofSize(entrance, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2)
                // Up to Seq. 7 the door is the Apprentice's alone; from it, anyone who comes up walks through.
                val walkers = if (shared) level.getEntitiesOfClass(ServerPlayer::class.java, reach) else listOf(opener)
                walkers.forEach { walker ->
                    if (walker.boundingBox.intersects(reach) && passed.add(walker.uuid)) step(walker, exit)
                }
            }
        }

        return AbilityOutcome.Done()
    }

    /** A solid block at arm's length; liquids and grass do not become doors. */
    private fun wallInFront(context: CastContext): BlockHitResult? {
        val hit = context.caster.pick(ACTIVATION_RANGE, 1.0f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return hit.takeIf { context.level.getBlockState(it.blockPos).isSolid }
    }

    /**
     * The first place beyond the wall, straight through it along [through], where the player fits:
     * air is needed under the feet and under the head. No footing is required — the Apprentice
     * comes out where the passage comes out, not where it is convenient.
     */
    private fun exitBehind(
        level: ServerLevel,
        wall: BlockPos,
        through: Direction,
        maxThickness: Int,
    ): BlockPos? = (1..maxThickness)
        .map { wall.relative(through, it) }
        .firstOrNull { fits(level, it) }

    private fun fits(level: ServerLevel, at: BlockPos): Boolean = open(level, at) && open(level, at.above())

    /** Air, or the door's own light: the far door kindles one right in the exit. */
    private fun open(level: ServerLevel, at: BlockPos): Boolean =
        level.getBlockState(at).let { it.isAir || it.`is`(Blocks.LIGHT) }

    private fun step(walker: ServerPlayer, exit: BlockPos) {
        val level = walker.level() as? ServerLevel ?: return
        // Someone may have walled the far side up while the leaves were opening.
        if (!fits(level, exit)) return
        val at = Vec3.atBottomCenterOf(exit)
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 1.5f)
        walker.teleportTo(at.x, at.y, at.z)
    }

    /** How far the corridor behind the leaves seems to run: deeper than the wall, and deeper the higher the Sequence. */
    private fun corridor(sequence: Sequence): Float = 1.5f + (9 - sequence.level) * 1.125f

    /** The pathway's specialty, hence `STRONG`: this is what the Door is kept for. */
    private fun thickness(sequence: Sequence): Int =
        scale(BASE_THICKNESS, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_THICKNESS = 10
        const val ACTIVATION_RANGE = 2.5
        const val COOLDOWN_SECONDS = 15
        const val SHARED_FROM_SEQUENCE = 7
        const val PORTAL_TICKS = 5 * TICKS_PER_SECOND
        /** How long the door stands open when it is the Apprentice's alone. */
        const val DOOR_FLASH_TICKS = 2 * TICKS_PER_SECOND
        const val WATCH_PERIOD_TICKS = 5
        const val ENTRY_RADIUS = 3.0
    }
}
