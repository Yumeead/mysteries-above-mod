package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.MarionetteTiming
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.common.SoulWard
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 5 (Marionettist): **Marionette Control**.
 *
 * Threads go out of the Marionettist's fingers and into a living thing. Hold them long enough and
 * the thing stops being its own.
 *
 * ## Two phases, and the second one is long on purpose
 *
 * Fixation takes 20 seconds at no mastery and 5 at full; conversion takes fifteen seconds for a
 * beast and **five minutes** for a person, scaled down by Sequence — the plugin's own numbers.
 * The threads reach 100 blocks, but they tighten at full speed only within 10: past that the
 * progress slows in proportion to the distance, so a grip from across a valley takes ten times
 * as long as one from arm's reach.
 *
 * ## The victim does not know, until the second phase
 *
 * Fixation is silent: no effect, no sound at the victim's ears, and the victim's threads darkening
 * and turning towards the caster are seen only by Marionettists — see `ThreadSight`. Only when the will
 * starts to break does the body notice, and what it notices is its legs: the jump shrinks as the
 * phase runs on. A transient attribute modifier, taken off in `finally` whatever ends the grip.
 *
 * A single blow of 6 damage or more on the victim snaps the threads. So the way out of this is
 * for someone else to hit your friend once, hard.
 *
 * ## The body is jerked like a puppet's
 *
 * The threads are seen by Marionettists only, but what they do is seen by everyone: while the will
 * is breaking, every couple of seconds the victim's body is jerked a step towards the Marionettist,
 * with the click of a string pulled taut — the sign, for whoever stands beside them, that it is
 * time to hit their friend. When the will gives, the body is jerked three times on the beat, and
 * goes still; then it is a puppet. Progress itself is drawn in the threads, not written anywhere.
 *
 * ## The ticket is settled at the end, not at the start
 *
 * 500 spirituality is the most expensive thing on the pathway, and the fixation can fail for a
 * dozen reasons — the victim walks off, someone hits them, the Marionettist is interrupted.
 * `Awaiting` keeps the ticket open until the puppet actually stands up, so every failure is free.
 *
 * ## What happens to a beast is not what happens to a person
 *
 * A mob is never killed: it keeps its body and its pathfinding and loses only the will to pick
 * its own enemies. A player's body cannot be borrowed, so it dies and a puppet wearing their face
 * gets up — and their Characteristic goes with it, permanently. See `Marionettes`.
 */
class MarionetteControl : ActiveAbility(AbilityIdentity.of("fool_marionettist_control")) {

    override val displayName: Component = Component.literal("Контроль Маріонетки")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Нитки з ваших пальців беруть живе тіло під владу. Двофазний захват: спершу фіксація, потім злам волі.\n" +
            "▪ Ціль під прицілом, до ${REACH.toInt()} бл.; найшвидше — ближче ніж ${FAST_RADIUS.toInt()} бл., далі повільніше\n" +
            "▪ Фіксація 20→5 с (за засвоєнням), злам: звір ~15 с, людина ~5 хв (швидшає з Послідовністю)\n" +
            "▪ Під час фіксації жертва нічого не помічає; під час зламу її тіло смикає до вас — це видно всім\n" +
            "▪ Один удар від $BREAK шкоди по жертві рве нитки; ціна знімається лише за готову маріонетку\n" +
            "▪ Звір лишається живим; людина гине, а її Характеристика назавжди переходить у маріонетку\n" +
            "▪ Керування — вкладка «Нитки» в колесі (утримуйте R): накази, вселення, обмін місцями, обрізати нитку\n" +
            "▪ Швидкий наказ — тримайте G і ПКМ: на істоту — бити, на блок — іти, в повітря — за вами",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // Letting go is free: the threads were never paid for, and they snap where they were tied.
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.Awaiting()
        }

        val beyonder = context.beyonder
        val limit = Marionettes.limitFor(beyonder)
        if (Marionettes.of(context.caster.uuid).size >= limit) {
            return AbilityOutcome.failed(
                "Ниток більше немає: ви тримаєте $limit. Обріжте одну (колесо R → «Нитки») або підніміть засвоєння",
            )
        }

        val target = context.targeting.lookingAt(REACH)
            ?: return AbilityOutcome.failed("Немає цілі — гляньте на живу істоту")
        if (Marionettes.isPuppet(target)) {
            return AbilityOutcome.failed("Ця істота вже на чиїхось нитках")
        }
        if (target !is ServerPlayer && target !is Mob) {
            return AbilityOutcome.failed("За це тіло нитки не беруться")
        }
        // Death's Spirit Swap: the soul body here is a servant's, and a thread has nothing to tie to.
        if (SoulWard.isWarded(target.uuid, context.scheduling.tick)) {
            return AbilityOutcome.failed("Нитка ковзає: душа цілі підмінена мерцем")
        }

        weave(context, target)
        return AbilityOutcome.Awaiting()
    }

    /**
     * The whole grip, from the first strand to the puppet standing up.
     *
     * Everything the session needs is taken out of the context **before** it starts: a context
     * carries the identity of one cast and lives exactly that long, while this runs for minutes.
     */
    private fun weave(context: CastContext, target: LivingEntity) {
        val ticket = context.cast
        val messaging = context.messaging
        val targetId = target.uuid
        val isPerson = target is ServerPlayer
        val lockTicks = MarionetteTiming.phase1LockTicks(context.beyonder.mastery.value)
        val breakTicks = if (isPerson) {
            MarionetteTiming.playerConvertTicks(context.sequence)
        } else {
            MarionetteTiming.mobSwapTicks(context.sequence)
        }

        // The threads snap on a real blow, and a blow is an event — not something to notice by
        // sampling the victim's health on our own beat, which is what the plugin had to do.
        var snapped = false
        val watch = context.events.on<IncomingDamage> { damage ->
            if (damage.victim.uuid == targetId && damage.original >= BREAK) snapped = true
        }

        context.session {
            try {
                val caster = owner ?: return@session
                // For the caster's ears only: a sound in the world would be the victim's warning.
                caster.connection.send(
                    ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_ACTIVATE),
                        SoundSource.PLAYERS, caster.x, caster.y, caster.z, 0.6f, 0.5f, caster.random.nextLong(),
                    ),
                )

                if (!hold(lockTicks, targetId, messaging, restrain = false, grips = 0f..FIXATION_SHARE) { snapped }) return@session
                if (!hold(breakTicks, targetId, messaging, restrain = true, grips = FIXATION_SHARE..1f) { snapped }) return@session

                // The will gives: three jerks on the beat, and then the body is still.
                repeat(FINAL_JERKS) {
                    val holder = owner ?: return@session
                    resolve(holder, targetId)?.let { jerk(it, holder, FINAL_PULL) }
                    waitTicks(FINAL_BEAT_TICKS)
                }
                waitTicks(STILL_TICKS)

                val caster2 = owner ?: return@session
                val victim = resolve(caster2, targetId) ?: return@session

                // Only now is there something to pay for.
                ticket.complete()
                when (victim) {
                    is ServerPlayer -> Marionettes.convert(caster2, victim)
                    is Mob -> Marionettes.seize(caster2, victim)
                    else -> return@session
                }
                Marionettes.pushState(caster2)
            } finally {
                watch.cancel()
                Marionettes.unweave(targetId)
                owner?.let { resolve(it, targetId) }?.revokeAttribute(Attributes.JUMP_STRENGTH, FETTER)
            }
        }
    }

    /**
     * One phase: keep the victim on the threads for [ticks] of progress, or fail.
     *
     * Progress runs at full speed within [FAST_RADIUS] and slows in proportion to the distance
     * past it, so the same phase from 100 blocks takes ten times as long. Returns `false` the
     * moment the grip is lost — out of reach, gone, dead, or struck hard enough to snap the
     * strands — and says why. With [restrain] the victim's jump weakens as the phase runs on.
     *
     * The progress is also what Spirit Vision draws: [grips] is the stretch of the whole grip
     * this phase covers, reported to [Marionettes.weave] so every Marionettist nearby sees the
     * victim's threads darken and turn towards the caster.
     */
    private suspend fun me.vangoo.mysteries.ability.SessionScope.hold(
        ticks: Int,
        targetId: java.util.UUID,
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        restrain: Boolean,
        grips: ClosedFloatingPointRange<Float>,
        snapped: () -> Boolean,
    ): Boolean {
        var held = 0.0
        var untilJerk = jerkInterval()
        while (held < ticks) {
            waitTicks(STEP_TICKS)

            val caster = owner ?: return false
            if (snapped()) {
                resolve(caster, targetId)?.let { snap(it) }
                messaging.hint(caster, Component.literal("Нитки збито ударом по жертві"), Fizzle.NONE)
                return false
            }
            val victim = resolve(caster, targetId)
            if (victim == null || !victim.isAlive || victim.level() !== caster.level()) {
                messaging.hint(caster, Component.literal("Ціль зникла — нитки обірвались"), Fizzle.NONE)
                return false
            }
            val distance = caster.distanceTo(victim).toDouble()
            if (distance > REACH) {
                snap(victim)
                messaging.hint(caster, Component.literal("Задалеко — нитки зісковзнули"), Fizzle.NONE)
                return false
            }

            held += STEP_TICKS * pace(distance)
            val fraction = (held / ticks).coerceAtMost(1.0)
            if (restrain) {
                victim.grantAttribute(
                    Attributes.JUMP_STRENGTH,
                    FETTER,
                    -(FETTER_START + (FETTER_FULL - FETTER_START) * fraction),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                )
            }
            Marionettes.weave(victim, caster, grips.start + (grips.endInclusive - grips.start) * fraction.toFloat())
            if (restrain) {
                untilJerk -= STEP_TICKS
                if (untilJerk <= 0) {
                    jerk(victim, caster, PULL)
                    untilJerk = jerkInterval()
                }
            }
        }
        return true
    }

    /**
     * The body jerked [pull] blocks towards [caster], with no wind-up and a string's click: the one
     * thing about the grip that everyone can see. A player feels it — the push reaches their client.
     */
    private fun jerk(victim: LivingEntity, caster: ServerPlayer, pull: Double) {
        val toward = caster.position().subtract(victim.position()).let { Vec3(it.x, 0.0, it.z) }
        if (toward.lengthSqr() > 1e-4) {
            val push = toward.normalize().scale(pull)
            victim.push(push.x, LIFT, push.z)
            victim.hurtMarked = true
        }
        victim.level().playSound(null, victim.x, victim.y + 1.0, victim.z, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.9f, 0.6f)
    }

    /** The threads tearing loose from a body: a string's snap where they were tied. */
    private fun snap(victim: LivingEntity) {
        victim.level().playSound(null, victim.x, victim.y + 1.0, victim.z, SoundEvents.TRIPWIRE_DETACH, SoundSource.PLAYERS, 1.0f, 1.4f)
    }

    /** Every two to three seconds, never on a steady beat: a steady beat would read as an effect, not a hand. */
    private fun jerkInterval(): Int = JERK_MIN_TICKS + kotlin.random.Random.nextInt(JERK_SPREAD_TICKS)

    /** 1 within [FAST_RADIUS], then `FAST_RADIUS / distance` — a tenth of the speed at [REACH]. */
    private fun pace(distance: Double): Double = if (distance <= FAST_RADIUS) 1.0 else FAST_RADIUS / distance

    private fun resolve(caster: ServerPlayer, id: java.util.UUID): LivingEntity? =
        (caster.level() as ServerLevel).getEntityInAnyDimension(id) as? LivingEntity

    private companion object {
        const val COST = 500
        const val COOLDOWN_SECONDS = 120
        /** How far the threads reach, both to catch and to hold. */
        const val REACH = 100.0

        /** Within this the threads tighten at full speed. */
        const val FAST_RADIUS = 10.0
        const val BREAK = MarionetteTiming.BREAK_DAMAGE_THRESHOLD.toFloat()

        /** Four times a second: fine enough that walking out of the tether is felt at once. */
        const val STEP_TICKS = 5

        /** The victim's jump is weakened by this share at the start of the second phase… */
        const val FETTER_START = 0.1

        /** …and by this share at its end: a full block is out of reach by then. */
        const val FETTER_FULL = 0.4
        val FETTER = modifierId("fool_marionettist_control", "jump_strength")

        /** How much of the drawn grip the fixation covers; the will breaking is the rest. */
        const val FIXATION_SHARE = 0.35f

        const val JERK_MIN_TICKS = 40
        const val JERK_SPREAD_TICKS = 21
        /** How hard a jerk pulls: about a third of a block, felt but not a leash. */
        const val PULL = 0.3
        const val LIFT = 0.12
        const val FINAL_JERKS = 3
        const val FINAL_PULL = 0.2
        const val FINAL_BEAT_TICKS = 5
        const val STILL_TICKS = 8
    }
}
