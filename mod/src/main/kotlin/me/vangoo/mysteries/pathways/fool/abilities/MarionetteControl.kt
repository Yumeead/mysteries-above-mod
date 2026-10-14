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
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.event.IncomingDamage
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
            "▪ Під час фіксації жертва нічого не помічає; під час зламу її стрибок слабшає\n" +
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

        val target = context.targeting.lookingAt(REACH)
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
                // For the caster's ears only: a sound in the world would be the victim's warning.
                caster.connection.send(
                    ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_ACTIVATE),
                        SoundSource.PLAYERS, caster.x, caster.y, caster.z, 0.6f, 0.5f, caster.random.nextLong(),
                    ),
                )

                if (!hold(lockTicks, targetId, targetName, "Фіксація ниток", messaging, restrain = false, grips = 0f..FIXATION_SHARE) { snapped }) return@session
                if (!hold(breakTicks, targetId, targetName, "Злам волі", messaging, restrain = true, grips = FIXATION_SHARE..1f) { snapped }) return@session

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
        targetName: String,
        phase: String,
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        restrain: Boolean,
        grips: ClosedFloatingPointRange<Float>,
        snapped: () -> Boolean,
    ): Boolean {
        var held = 0.0
        while (held < ticks) {
            waitTicks(STEP_TICKS)

            val caster = owner ?: return false
            if (snapped()) {
                messaging.actionBar(caster, Component.literal("Нитки збито ударом по жертві"))
                return false
            }
            val victim = resolve(caster, targetId)
            if (victim == null || !victim.isAlive || victim.level() !== caster.level()) {
                messaging.actionBar(caster, Component.literal("Ціль зникла — нитки обірвались"))
                return false
            }
            val distance = caster.distanceTo(victim).toDouble()
            if (distance > REACH) {
                messaging.actionBar(caster, Component.literal("Задалеко — нитки зісковзнули"))
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
            messaging.actionBar(caster, Component.literal("$phase: $targetName — ${(fraction * 100).toInt()}%"))
        }
        return true
    }

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
    }
}
