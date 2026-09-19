package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.MarionetteTiming
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.fool.Marionettes
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob

/**
 * Fool, Seq. 5 (Marionettist): **Marionette Control**.
 *
 * Threads go out of the Marionettist's fingers and into a living thing. Hold them long enough and
 * the thing stops being its own.
 *
 * ## Two phases, and the second one is long on purpose
 *
 * Fixation takes 20 seconds at no mastery and 5 at full, and it needs the victim within five
 * blocks the whole time — that is a hand on someone, not a spell at range. Conversion is longer
 * still: fifteen seconds for a beast, **five minutes** for a person, scaled down by Sequence.
 * These are the plugin's own numbers and they are kept: at this Sequence the power is not meant
 * to be used in a fight, it is meant to be used on somebody who cannot get away.
 *
 * A single blow of 6 damage or more on the victim snaps the threads. So the way out of this is
 * for someone else to hit your friend once, hard.
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
            "▪ Ціль під прицілом, до ${selectRange(sequence).toInt()} бл.; далі тримайте її за $TETHER бл. від себе\n" +
            "▪ Фіксація 20→5 с (за засвоєнням), злам: звір ~15 с, людина ~5 хв (швидшає з Послідовністю)\n" +
            "▪ Один удар від $BREAK шкоди по жертві рве нитки; ціна знімається лише за готову маріонетку\n" +
            "▪ Звір лишається живим; людина гине, а її Характеристика назавжди переходить у маріонетку",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Ви відпустили Нитки")
        }

        val beyonder = context.beyonder
        val limit = Marionettes.limitFor(beyonder)
        if (Marionettes.of(context.caster.uuid).size >= limit) {
            return AbilityOutcome.failed(
                "Ниток більше немає: ви тримаєте $limit. Обріжте одну або підніміть засвоєння",
            )
        }

        val target = context.targeting.lookingAt(selectRange(context.sequence))
            ?: return AbilityOutcome.failed("Немає цілі — гляньте на живу істоту")
        if (Marionettes.isPuppet(target)) {
            return AbilityOutcome.failed("Ця істота вже на чиїхось нитках")
        }
        if (target !is ServerPlayer && target !is Mob) {
            return AbilityOutcome.failed("За це тіло нитки не беруться")
        }

        weave(context, target)
        return AbilityOutcome.Awaiting(Component.literal("Нитки натягуються на ${target.name.string}"))
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
        val targetName = target.name.string
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
                caster.level().playSound(
                    null, caster.x, caster.y, caster.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 0.5f,
                )

                if (!hold(lockTicks, targetId, targetName, "Фіксація ниток", messaging) { snapped }) return@session
                if (!hold(breakTicks, targetId, targetName, "Злам волі", messaging) { snapped }) return@session

                val caster2 = owner ?: return@session
                val victim = resolve(caster2, targetId) ?: return@session

                // Only now is there something to pay for.
                ticket.complete()
                when (victim) {
                    is ServerPlayer -> Marionettes.convert(caster2, victim)
                    is Mob -> Marionettes.seize(caster2, victim)
                    else -> return@session
                }
                messaging.tell(caster2, Component.literal("$targetName тепер ваша маріонетка"))
                Marionettes.pushState(caster2)
            } finally {
                watch.cancel()
            }
        }
    }

    /**
     * One phase: keep the victim on the tether for [ticks], or fail.
     *
     * Returns `false` the moment the grip is lost — too far, gone, dead, or struck hard enough to
     * snap the strands — and says why. The stupor deepens as the phase runs on: this is what the
     * victim feels while it is happening to them.
     */
    private suspend fun me.vangoo.mysteries.ability.SessionScope.hold(
        ticks: Int,
        targetId: java.util.UUID,
        targetName: String,
        phase: String,
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        snapped: () -> Boolean,
    ): Boolean {
        var held = 0
        while (held < ticks) {
            waitTicks(STEP_TICKS)
            held += STEP_TICKS

            val caster = owner ?: return false
            if (snapped()) {
                messaging.actionBar(caster, Component.literal("Нитки збито ударом по жертві"))
                return false
            }
            val victim = resolve(caster, targetId)
            if (victim == null || !victim.isAlive) {
                messaging.actionBar(caster, Component.literal("Ціль зникла — нитки обірвались"))
                return false
            }
            if (caster.distanceToSqr(victim) > TETHER * TETHER) {
                messaging.actionBar(caster, Component.literal("Задалеко — нитки зісковзнули"))
                return false
            }

            stupor(victim)
            strands(caster, victim)
            messaging.actionBar(
                caster,
                Component.literal("$phase: $targetName — ${percent(held, ticks)}%"),
            )
        }
        return true
    }

    /** The victim's share of it: heavy limbs, a swimming head, no strength in the arms. */
    private fun stupor(victim: LivingEntity) {
        victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, STUPOR_TICKS, SLOWNESS_LEVEL, true, false))
        victim.addEffect(MobEffectInstance(MobEffects.NAUSEA, STUPOR_TICKS, 0, true, false))
        victim.addEffect(MobEffectInstance(MobEffects.WEAKNESS, STUPOR_TICKS, 1, true, false))
    }

    /** The threads themselves, drawn hand to victim so both sides can see what is happening. */
    private fun strands(caster: ServerPlayer, victim: LivingEntity) {
        val level = caster.level()
        val from = caster.eyePosition
        val to = victim.position().add(0.0, victim.bbHeight * 0.6, 0.0)
        for (step in 0..STRAND_POINTS) {
            val at = from.lerp(to, step.toDouble() / STRAND_POINTS)
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun resolve(caster: ServerPlayer, id: java.util.UUID): LivingEntity? =
        (caster.level() as ServerLevel).getEntityInAnyDimension(id) as? LivingEntity

    private fun percent(done: Int, total: Int): Int = (done * 100 / total.coerceAtLeast(1)).coerceAtMost(100)

    private fun selectRange(sequence: Sequence): Double =
        BASE_SELECT_RANGE * SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val COST = 500
        const val COOLDOWN_SECONDS = 120
        const val BASE_SELECT_RANGE = 30.0
        const val TETHER = 5.0
        const val BREAK = MarionetteTiming.BREAK_DAMAGE_THRESHOLD.toFloat()

        /** Four times a second: fine enough that walking out of the tether is felt at once. */
        const val STEP_TICKS = 5

        const val STUPOR_TICKS = 3 * TICKS_PER_SECOND
        const val SLOWNESS_LEVEL = 9
        const val STRAND_POINTS = 10
    }
}
