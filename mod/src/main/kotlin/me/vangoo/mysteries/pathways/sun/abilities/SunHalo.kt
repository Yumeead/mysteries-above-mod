package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 7 (Solar High Priest): **Halo of the Sun**.
 *
 * The halo does not shine steadily — it **strikes in pulses**. Every [PULSE_PERIOD_TICKS] ticks a
 * ring spreads out from the High Priest, and any unclean it catches takes a blow.
 *
 * ## How it differs from Blessing
 *
 * Both powers are sustained and both shine around, so the difference had to be made mechanical
 * rather than descriptive:
 *
 * | | Blessing (Seq. 8) | Halo (Seq. 7) |
 * |---|---|---|
 * | to whom | allies — healing | the unclean — damage |
 * | how | evenly, a little every second | in a pulse once every two seconds, hard |
 * | on activation | nothing | an instant cleanse of debuffs |
 *
 * An even flow against a pulse feels different in a fight: under a Blessing you stand, under a
 * Halo you advance between the ring's beats.
 */
class SunHalo : ActiveAbility(AbilityIdentity.of("sun_halo")) {

    override val displayName: Component = Component.literal("Німб Сонця")

    /** The cast is free: the price is the upkeep, and putting the halo out must cost nothing. */
    override val spiritualityCost: Int = 0

    /**
     * The upkeep grows with the Sequence — and it must, because **regeneration** grows with it
     * too: 3 points/s at Seq. 7 against 6 at Seq. 5 (and 9 at full mastery). A flat price would
     * make the halo free at the top.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Німб святого світла: при активації миттєво знімає з вас усе шкідливе, далі " +
            "б'є пульсом.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Пульс раз на ${PULSE_PERIOD_TICKS / TICKS_PER_SECOND} с: ${pulseDamage(sequence)} по нечисті\n" +
            "▪ Витрата: ${periodicCost(sequence)} духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Німб згас")
        }

        // Activation cleanses — that is what the halo is taken for at the moment things are
        // already bad.
        val cleaned = cleanse(context.caster)

        val radius = range(context.sequence)
        val hit = pulseDamage(context.sequence)
        val mote = ModParticles.mote(context.effects.pathwayColor)
        // The halo is a steady shape, so the mote is steady too: a spark shimmers and floats up,
        // and a ring made of them read as a cloud. See `SpiritGlyphParticle`.
        val glyph = ModParticles.glyph(context.effects.pathwayColor)
        val messaging = context.messaging
        val beyonders = context.beyonders

        // The tick is frequent — the halo itself lives by it; the pulse is counted in ticks while
        // the upkeep stays per second (`sustain` takes it from `upkeepPeriodTicks`).
        var beat = 0

        context.session {
            try {
                val end = sustain(periodTicks = HALO_PERIOD_TICKS, upkeep = periodicCost) { priest ->
                    halo(priest, glyph, beat)
                    beat++
                    if (beat % BEATS_PER_PULSE != 0) return@sustain

                    pulse(priest)
                    val level = priest.level()
                    level.getEntitiesOfClass(
                        LivingEntity::class.java,
                        AABB.ofSize(priest.position(), radius * 2, radius * 2, radius * 2),
                    )
                        .filter { it !== priest && it.position().distanceToSqr(priest.position()) <= radius * radius }
                        .filter { it.isProfane(beyonders) }
                        .forEach { smite(level, priest, beyonders, it, hit) }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Німб згас: духовність скінчилась"))
                }
            } finally {
                owner?.let { priest ->
                    priest.level().playSound(
                        null, priest.x, priest.y, priest.z,
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f,
                    )
                }
            }
        }

        return AbilityOutcome.done(
            if (cleaned > 0) "Німб спалахнув, знято шкідливих ефектів: $cleaned" else "Німб спалахнув",
        )
    }

    /**
     * The **halo** — a ring above the head that holds its shape.
     *
     * ## Why it used to scatter upwards
     *
     * The spirit mote has negative gravity (`SpiritMoteParticle.GRAVITY` — spirit matter floats
     * up) and lives 16–28 ticks. The ring, meanwhile, was redrawn every four ticks at a new angle
     * and a new bob height — i.e. at any moment five to seven rings hung in the air, each at its
     * own angle, its own height and already risen. Together that read as a cloud floating up
     * rather than as a halo.
     *
     * Fixed by three things, and all three are about **steadiness**:
     *
     * 1. A mote is born with a small **downward** velocity ([HALO_SINK]) that cancels its own
     *    floating. The `sendParticles` form with `count = 0` exists exactly for this: then the
     *    three deltas are not a spread but a **velocity**.
     * 2. The height bobbing was removed entirely: a halo hangs, it does not sway.
     * 3. The rotation was slowed threefold so that the ring does not smear into an arc within a
     *    mote's lifetime.
     *
     * The points are then the same each time, so redrawing **reinforces** the same circle instead
     * of drawing a new one beside it.
     */
    private fun halo(priest: ServerPlayer, glyph: ParticleOptions, beat: Int) {
        val level = priest.level()
        val spin = beat * HALO_SPIN
        // The position in the packet is the crown itself: by it the glyph finds its owner. Where
        // exactly to stand is said by the **offset**, and that travels separately, so network
        // delay does not shift the ring.
        val head = priest.y + priest.bbHeight

        repeat(HALO_POINTS) { i ->
            val angle = TAU * i / HALO_POINTS + spin
            level.sendParticles(
                glyph,
                priest.x, head, priest.z,
                // count = 0 — a point with no spread; the three deltas arrive unchanged and are
                // the offset from the crown rather than a velocity (see `SpiritGlyphParticle`).
                0, HALO_RADIUS * cos(angle), HALO_LIFT, HALO_RADIUS * sin(angle), 1.0,
            )
        }
    }

    /**
     * A pulse strike — **the chime only**.
     *
     * Circles on the ground were the first thing removed from here: `Blessing` already has that
     * same handwriting, and the Sun's two sustained powers looked identical. The second, wider
     * ring above the head that replaced them turned out no better: on the chime a second rim
     * flashed around the halo, and instead of a strike it read as a glitch.
     *
     * The halo stays a single ring **always**. The strike is announced by the sound and its
     * consequences by the target losing health; there is no need to draw it a second time.
     */
    private fun pulse(priest: ServerPlayer) {
        priest.level().playSound(
            null, priest.x, priest.y, priest.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.9f,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun pulseDamage(sequence: Sequence): Int =
        scale(BASE_PULSE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_RANGE = 8
        const val BASE_PULSE_DAMAGE = 5
        const val BASE_PERIODIC_COST = 20
        const val COOLDOWN_SECONDS = 4
        const val PULSE_PERIOD_TICKS = 2 * TICKS_PER_SECOND

        /**
         * Once every two ticks — and that is enough, ever since the glyph **rides its owner**.
         *
         * While the shape was static in the world, the tick had to be kept every-tick so the ring
         * would not jump, and that cost ten particles a tick. Now the smoothness comes not from
         * the frequency but from the glyph latching onto the head: the tick is left only so that
         * generations change before their lifetime runs out.
         */
        const val HALO_PERIOD_TICKS = 2
        const val BEATS_PER_PULSE = PULSE_PERIOD_TICKS / HALO_PERIOD_TICKS

        /** Above the crown rather than on it: a halo is supposed to hang. */
        const val HALO_LIFT = 0.30
        /** A little wider than the head, and no more: a halo, not a hoop around the shoulders. */
        const val HALO_RADIUS = 0.28
        const val HALO_POINTS = 10
        /** Slowly: the ring must not smear into an arc within the glyph's two-tick lifetime. */
        const val HALO_SPIN = 0.03
        const val TAU = 2.0 * Math.PI
    }
}
