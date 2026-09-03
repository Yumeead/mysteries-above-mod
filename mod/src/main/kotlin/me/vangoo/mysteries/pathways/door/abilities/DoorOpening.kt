package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
        if (Vec3(context.look.x, 0.0, context.look.z).lengthSqr() < 1.0e-6) {
            return AbilityOutcome.failed("Дивіться на стіну, а не вгору")
        }
        val forward = horizontal(context.look)

        val wall = wallInFront(context)
            ?: return AbilityOutcome.failed("Підійдіть упритул до суцільної стіни")

        val maxThickness = thickness(context.sequence)
        val exit = exitBehind(context.level, wall, forward, maxThickness)
            ?: run {
                context.level.playSound(
                    null, context.origin.x, context.origin.y, context.origin.z,
                    SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.5f,
                )
                return AbilityOutcome.failed(
                    "Ця стіна товща за $maxThickness бл. або за нею немає місця",
                )
            }

        // The door stands **on the face** of the wall rather than in the centre of the block the
        // ray hit. Otherwise the frame ends up inside the stone — and a particle inside a block's
        // body is not visible at all, so the power looks as if it had no effect.
        //
        // The Y is taken from `wall` rather than from `context.caster.y`: both halves of the
        // passage must stand at the same level. Previously the entrance was drawn at the
        // Apprentice's eye height while the exit was at the wall block's height (`exitBehind`
        // always keeps the wall's Y), and if the player was not standing exactly on a whole block
        // the doors turned out "not quite level with each other".
        val face = Vec3(wall.x + 0.5, wall.y.toDouble(), wall.z + 0.5).subtract(forward.scale(FACE_CLEARANCE))
        val open = if (context.sequence.level <= SHARED_FROM_SEQUENCE) PORTAL_TICKS else DOOR_FLASH_TICKS
        doorway(context.scheduling, context.level, face, forward, open, context.effects.pathwayColor)
        doorway(
            context.scheduling, context.level,
            exit.add(forward.scale(FACE_CLEARANCE)), forward, open, context.effects.pathwayColor,
        )

        step(context.caster, exit)

        if (context.sequence.level > SHARED_FROM_SEQUENCE) {
            return AbilityOutcome.done("Ви пройшли крізь стіну")
        }

        // Who has already gone through. State of one cast, not a field of the ability: the ability
        // instance is shared by every Apprentice on the server.
        val passed = HashSet<UUID>()
        context.session {
            sustain(periodTicks = WATCH_PERIOD_TICKS, durationTicks = PORTAL_TICKS) { opener ->
                val level = opener.level() as? ServerLevel ?: return@sustain
                level.getEntitiesOfClass(
                    ServerPlayer::class.java,
                    AABB.ofSize(face, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2),
                ).forEach { walker ->
                    if (!passed.add(walker.uuid)) return@forEach
                    step(walker, exit)
                }
            }
        }

        return AbilityOutcome.done("Прохід відкрито — крізь нього пройдуть й інші")
    }

    /** A solid block at arm's length; liquids and grass do not become doors. */
    private fun wallInFront(context: CastContext): BlockPos? {
        val hit = context.caster.pick(ACTIVATION_RANGE, 1.0f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return hit.blockPos.takeIf { context.level.getBlockState(it).isSolid }
    }

    /**
     * The first place beyond the wall where the player fits: air is needed under the feet and
     * under the head. No footing is required — the Apprentice comes out where the passage comes
     * out, not where it is convenient.
     *
     * The direction is rounded to one of the eight grid axes **once**, not at every step of the
     * depth. `Math.round(forward.x * depth)` rounded X and Z independently at every `depth` — and
     * for a slanted gaze (not exactly along an axis or a diagonal) that gave an uneven drift: a
     * step could move into a cell that was not in the previous step. The exit ended up not
     * opposite the entrance but somewhere off to the side, and the teleport aimed there too. A
     * fixed `(dx, dz)` multiplied by `depth` keeps every step on one straight grid line — the one
     * the Apprentice is looking along.
     */
    private fun exitBehind(
        level: ServerLevel,
        wall: BlockPos,
        forward: Vec3,
        maxThickness: Int,
    ): Vec3? {
        val dx = Math.round(forward.x).toInt()
        val dz = Math.round(forward.z).toInt()
        for (depth in 1..maxThickness) {
            val step = wall.offset(dx * depth, 0, dz * depth)
            if (level.getBlockState(step).isAir && level.getBlockState(step.above()).isAir) {
                return Vec3(step.x + 0.5, step.y.toDouble(), step.z + 0.5)
            }
        }
        return null
    }

    private fun step(walker: ServerPlayer, exit: Vec3) {
        val level = walker.level() as? ServerLevel ?: return
        level.playSound(null, exit.x, exit.y, exit.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 1.5f)
        walker.teleportTo(exit.x, exit.y, exit.z)
        level.sendParticles(ParticleTypes.PORTAL, exit.x, exit.y + 1.0, exit.z, 20, 0.3, 0.5, 0.3, 0.1)
    }

    /** The pathway's specialty, hence `STRONG`: this is what the Door is kept for. */
    private fun thickness(sequence: Sequence): Int =
        scale(BASE_THICKNESS, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_THICKNESS = 10
        const val ACTIVATION_RANGE = 2.5
        const val COOLDOWN_SECONDS = 15
        const val SHARED_FROM_SEQUENCE = 7
        const val PORTAL_TICKS = 5 * TICKS_PER_SECOND
        /** How long the door is visible when the passage is single-use: just enough to see what happened. */
        const val DOOR_FLASH_TICKS = 2 * TICKS_PER_SECOND
        const val WATCH_PERIOD_TICKS = 5
        const val ENTRY_RADIUS = 3.0
        /** How far the door is pushed out in front of the block's face: particles are invisible inside stone. */
        const val FACE_CLEARANCE = 0.55
    }
}
