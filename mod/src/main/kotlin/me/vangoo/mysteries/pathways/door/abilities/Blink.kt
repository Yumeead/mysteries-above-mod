package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.net.payload.DoorKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * Door, Seq. 5 (Traveller): **Instant Step**.
 *
 * The Traveller's simplest power and their most frequent: fifteen blocks in the direction of
 * the gaze, in a blink.
 *
 * ## A door, a passage, a door
 *
 * The first revisions teleported in the tick of the cast, and the power was a jump cut. Now space
 * opens a pale door just ahead of the Traveller and another at the landing, folds a passage between
 * them — arch after arch of the door's own outline — and the Traveller **rushes along it**: a few
 * ticks of real motion, a sharp start easing into the arrival, the body seen flying by everyone and
 * the camera carried with it. The door behind shuts, the one ahead lets them out and shuts behind them,
 * and the passage goes out after them.
 *
 * The motion is the server pushing the player each tick ([ServerPlayer.hurtMarked] sends it to their
 * own client, which moves itself); the last tick puts them exactly on the landing.
 *
 * ## A safe spot is searched for, not guaranteed
 *
 * The ray gives a point, and a point can be in lava, in a ceiling or in mid-air over a chasm.
 * The search is the same as in the plugin — up to three blocks down and up — and its reason for
 * existing is the same: without it the power would regularly kill its owner, and instead of
 * "blink" the player would be thinking "or should I not blink into lava".
 *
 * Footing underneath is deliberately **not** required: otherwise stepping off a cliff would
 * become impossible, and that is half of what the power is for.
 */
class Blink : ActiveAbility(AbilityIdentity.of("door_blink")) {

    override val displayName: Component = Component.literal("Миттєвий крок")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ривок крізь простір у бік погляду.\n" +
            "▪ Дальність ${distance(sequence).toInt()} бл.; упреться в стіну — станете перед нею\n" +
            "▪ Відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val reach = distance(context.sequence)
        val look = context.look
        // A step back from the wall: the player does not fit exactly in its plane.
        val aim = context.caster.pick(reach, 1.0f, false).location.subtract(look.scale(CLEARANCE))

        val landing = safeSpot(context.level, aim)
            ?: return AbilityOutcome.failed("Там немає безпечного місця")

        val from = context.origin
        val ahead = horizontal(look)
        val pale = context.effects.pathwayColor.shade(PALE)
        val flight = flightTicks(from.distanceTo(landing))
        val opened = DoorKind.BLINK.opened

        // Both doors open the way the Traveller goes: they push through the first, and the second
        // shuts behind them. The passage runs between the two.
        val departure = from.add(ahead.scale(DOOR_GAP))
        val arrival = landing.subtract(ahead.scale(DOOR_GAP))
        doorway(context.scheduling, context.level, departure, ahead.reverse(), flight, pale, DoorKind.BLINK)
        doorway(context.scheduling, context.level, arrival, ahead.reverse(), flight, pale, DoorKind.BLINK)
        passage(context.level, departure, arrival, pale, opened, flight)
        sound(context.level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f)

        context.session {
            // The door opens first, then they go.
            waitTicks(opened)
            val start = owner?.position() ?: return@session
            owner?.let { sound(it.level() as ServerLevel, start, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.3f) }
            try {
                for (tick in 1..flight) {
                    val traveller = owner ?: return@session
                    // Open loop: each tick's step along the curve, whatever the server last heard of
                    // where the client is — a late packet must not turn into a lurch.
                    val behind = WorldEffectPayload.Passage.travelled((tick - 1).toFloat() / flight)
                    val next = WorldEffectPayload.Passage.travelled(tick.toFloat() / flight)
                    traveller.deltaMovement = landing.subtract(start).scale((next - behind).toDouble())
                    traveller.hurtMarked = true
                    traveller.resetFallDistance()
                    waitTicks(1)
                }
            } finally {
                owner?.let { arrive(it, landing) }
            }
        }

        return AbilityOutcome.Done()
    }

    /** Puts the Traveller exactly on the landing, at rest — unless it was walled up on the way. */
    private fun arrive(traveller: ServerPlayer, landing: Vec3) {
        val level = traveller.level() as? ServerLevel ?: return
        traveller.deltaMovement = Vec3.ZERO
        traveller.hurtMarked = true
        traveller.resetFallDistance()
        // Not `teleportTo(x, y, z)`: that one keeps the client's own velocity (`Relative.DELTA`), and the
        // Traveller would sail on a few blocks past the landing. Here the velocity is set to nothing.
        if (fits(level, BlockPos.containing(landing)) != null) {
            traveller.connection.teleport(PositionMoveRotation(landing, Vec3.ZERO, 0f, 0f), Relative.ROTATION)
        }
        sound(level, landing, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f)
    }

    /**
     * A spot where the player fits: air is needed under the feet and under the head.
     *
     * The point itself is checked, then downwards to [SEARCH] and, failing that, upwards —
     * because the commonest miss is "hit the floor", not "hit the ceiling".
     */
    private fun safeSpot(level: ServerLevel, aim: Vec3): Vec3? {
        val base = BlockPos.containing(aim)
        for (dy in 0..SEARCH) {
            fits(level, base.below(dy))?.let { return it }
            if (dy > 0) fits(level, base.above(dy))?.let { return it }
        }
        return null
    }

    private fun fits(level: ServerLevel, pos: BlockPos): Vec3? {
        if (!open(level, pos) || !open(level, pos.above())) return null
        return Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
    }

    /** Air, or a door's light: another Traveller's door may stand lit right there. */
    private fun open(level: ServerLevel, pos: BlockPos): Boolean =
        level.getBlockState(pos).let { it.isAir || it.`is`(Blocks.LIGHT) }

    private fun sound(level: ServerLevel, at: Vec3, event: SoundEvent, pitch: Float) =
        level.playSound(null, at.x, at.y + 1.0, at.z, event, SoundSource.PLAYERS, 1.0f, pitch)

    /** About [SPEED] blocks a tick on average — fast enough to be a blink, slow enough to be seen. */
    private fun flightTicks(distance: Double): Int = ceil(distance / SPEED).toInt().coerceIn(MIN_FLIGHT, MAX_FLIGHT)

    private fun distance(sequence: Sequence): Double =
        scale(BASE_DISTANCE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private companion object {
        const val BASE_DISTANCE = 15
        const val COOLDOWN_SECONDS = 8
        const val CLEARANCE = 0.6
        const val SEARCH = 3
        /** How far ahead of the Traveller the first door stands, and behind them the second. */
        const val DOOR_GAP = 0.6
        /**
         * Blocks a tick, on average. The curve starts at twice that, and the server takes a client
         * moving more than ten blocks a tick for a cheat — so it stays well under.
         */
        const val SPEED = 4.0
        const val MIN_FLIGHT = 3
        const val MAX_FLIGHT = 8
        /** The doors and the passage are paler than the pathway: space itself, not the Traveller's light. */
        const val PALE = 0.35
    }
}
