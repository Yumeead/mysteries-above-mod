package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An aura on the body: strands that rise from the feet to the shoulders and **travel with the
 * target**.
 *
 * The first shape in the effects vocabulary. The form is generic — "living matter wrapping
 * someone" — and what it actually is, the colour says: the Sailor's water, the Sun's light, the
 * Death's ash. Hence there is nothing watery here beyond the default [counterFlow], which every
 * caller supplies for itself.
 *
 * ## Why not a circle underfoot
 *
 * Tried on 28.08.2026 and rejected: an even geometric figure reads as a layer on top of the
 * game rather than as part of the world. A strand of separate motes stays pixelated — that is,
 * at home in a game where everything else is a pixel too.
 *
 * The mote here is the mod's own ([ModParticles.SPIRIT_MOTE]) rather than a vanilla `DUST`: each
 * one plays its own flash at the start and fade at the end, so the strand need not extinguish
 * anything by hand — it only sows.
 *
 * ## Why the shape schedules itself
 *
 * A session belongs to an ability and goes out with it; an aura does not. So it takes its own
 * job in [Scheduling] and cancels itself when the term expires. The ability is not responsible
 * for it and never sees the tick counter.
 */
fun bodyAura(
    scheduling: Scheduling,
    target: LivingEntity,
    durationTicks: Int,
    color: RgbColor,
    /** What falls downwards while the strands go up. Without a counter-flow this is rotation, not a current. */
    counterFlow: ParticleOptions? = null,
) {
    require(durationTicks > 0) { "Тривалість покриву мусить бути додатною: $durationTicks" }

    val mote = ModParticles.mote(color)
    val follow = follower(target)
    var step = 0

    lateinit var handle: TaskHandle
    handle = scheduling.every(PERIOD_TICKS) {
        if (step * PERIOD_TICKS >= durationTicks) return@every handle.cancel()
        draw(follow() ?: return@every, mote, counterFlow, step)
        step++
    }
}

/**
 * How to find the target every tick.
 *
 * Do not cache the player: after a death or a dimension change the server creates a **new**
 * `ServerPlayer`, and the aura would be left on an object nobody can see any more. Other
 * entities live on as the same object, so it is enough to ask whether they are still in the world.
 */
private fun follower(target: LivingEntity): () -> LivingEntity? {
    if (target !is ServerPlayer) return { target.takeIf { !it.isRemoved } }
    val id = target.uuid
    val players = target.level().server.playerList
    return { players.getPlayer(id) }
}

private fun draw(target: LivingEntity, mote: ParticleOptions, counterFlow: ParticleOptions?, step: Int) {
    val level = target.level() as? ServerLevel ?: return
    val foot = target.position()
    val height = target.bbHeight * TOP

    for (strand in 0 until STRANDS) {
        // The strands are phase-shifted, so at any moment they are at different heights: an
        // identical phase would give a ring jumping upwards — that is, the same circle, only vertical.
        val climb = (step * RISE_PER_STEP + strand.toFloat() / STRANDS) % 1f
        val angle = TAU * (climb * TURNS + strand.toFloat() / STRANDS)
        val radius = RADIUS * (1f - NARROWING * climb)
        level.sendParticles(
            mote,
            foot.x + radius * cos(angle),
            foot.y + climb * height,
            foot.z + radius * sin(angle),
            1, JITTER, JITTER, JITTER, 0.0,
        )
    }

    if (counterFlow != null && step % COUNTER_EVERY_STEPS == 0) {
        val angle = TAU * (step * COUNTER_TURN)
        level.sendParticles(
            counterFlow,
            foot.x + RADIUS * cos(angle),
            foot.y + height * COUNTER_HEIGHT,
            foot.z + RADIUS * sin(angle),
            1, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

private const val TAU = (2.0 * PI).toFloat()

/** Every two ticks: 1.5 motes per tick — the aura is dense but not spam. */
private const val PERIOD_TICKS = 2
private const val STRANDS = 3
/** A full rise takes 20 steps, i.e. two seconds. */
private const val RISE_PER_STEP = 0.05f
private const val TURNS = 1.5f
private const val RADIUS = 0.55f
/** Towards the top the aura draws in to the shoulders rather than running as a column. */
private const val NARROWING = 0.35f
private const val TOP = 1.05f
private const val JITTER = 0.02

private const val COUNTER_EVERY_STEPS = 5
private const val COUNTER_TURN = 0.37f
private const val COUNTER_HEIGHT = 0.85f
