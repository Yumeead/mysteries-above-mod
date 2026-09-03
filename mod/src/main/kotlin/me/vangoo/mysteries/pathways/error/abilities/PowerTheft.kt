package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.EvidenceLog
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Error, Seq. 6 (Prometheus): **Power Theft**.
 *
 * The tier that gives the pathway its name. Everything below took things: an item, attention, a
 * truth. Prometheus takes the **power itself** — for ten minutes it is his, and for half an hour
 * its owner is without it.
 *
 * ## You steal what you saw them do
 *
 * The plugin asked twice in a chest GUI: first which target, then which of their powers — the
 * unknown ones shown as anonymous coloured lights you picked blind, because the wiki says *"the
 * more ignorant one is, the closer it is to random"*. Both menus have a world answer here, and
 * the second one is the pathway's own idea:
 *
 * | plugin | here |
 * |---|---|
 * | a menu of nearby heads | the gaze — and the target sees who is staring |
 * | a menu of anonymous lights | **the last power the victim was seen using** ([EvidenceLog]) |
 * | «+3 % per unlocked recipe» | +5 % per trace the victim left, capped |
 *
 * So ignorance still costs, but it costs the right thing: a Prometheus who watched his mark work
 * takes the power he watched, with better odds. One who walks up cold takes whatever comes to
 * hand. That is the wiki's rule expressed as a **thing that happened in the world**, and it makes
 * the Cryptologist's evidence log load-bearing one Sequence later.
 *
 * ## Stolen power is weaker in a lesser hand
 *
 * The power is cast at its own price and its own cooldown, resolved against **the thief's**
 * Sequence. Stealing from a Sequence 3 does not hand a Sequence 6 a Sequence 3's strength — the
 * ability scales to whoever holds it, which is what "with proficiency" has to mean without
 * breaking the ladder.
 *
 * ## Madness is the other thing that can be taken
 *
 * Sneak-cast steals the victim's **derangement** instead: their sanity loss drops, the thief's
 * rises by the same. The wiki's "at one's own risk" is literal, and it needs no menu slot —
 * crouching says it.
 */
class PowerTheft : ActiveAbility(AbilityIdentity.of("error_power_theft")) {

    override val displayName: Component = Component.literal("Крадіжка сили")

    override val spiritualityCost: Int = 90

    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE))
            .roundToInt()
            .coerceAtLeast(MIN_COOLDOWN_SECONDS)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви забираєте чужу силу собі — і той, у кого ви її взяли, лишається без неї.\n" +
            "▪ Ціль під поглядом, до ${reach(sequence).toInt()} бл.\n" +
            (
                if (sequence.level <= DREAM_STEALER_TIER) {
                    "▪ Невзяте в діло чекає до ${RESERVE_MILLIS / 3_600_000} год; перший каст лишає вам " +
                        "${DREAM_HOLD_MILLIS / 60_000} хв, а жертва без сили ${DREAM_SUPPRESSION_MILLIS / 60_000} хв\n"
                } else {
                    "▪ Ваша ${HOLD_MILLIS / 60_000} хв володіння, її ${SUPPRESSION_MILLIS / 60_000} хв без сили\n"
                }
                ) +
            "▪ Береться та сила, яку ви бачили в ділі; не бачили — візьметься навмання\n" +
            "▪ Шанс $BASE_CHANCE_PERCENT% + $TRACE_BONUS_PERCENT% за кожен її свіжий слід " +
            "(до $MAX_TRACE_BONUS_PERCENT%), мінус $PER_LEVEL_PENALTY_PERCENT% за кожну Послідовність її переваги\n" +
            "▪ Присівши, ви крадете не силу, а божевілля\n" +
            "▪ Слот один: доки тримаєте вкрадене, другого не візьмете\n" +
            "▪ Відкат ${cooldownSeconds(sequence)} с; невдача коштує так само",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (TheftRegistry.heldBy(caster.uuid) != null) {
            return AbilityOutcome.failed("Ваш слот зайнятий: спершу витратьте вкрадене")
        }

        val dreamStealer = context.sequence.level <= DREAM_STEALER_TIER
        val target = context.targeting.lookingAt(reach(context.sequence))
            ?: return AbilityOutcome.failed("Наведіть погляд на того, у кого берете")
        val victim = context.beyonders.of(target)
            ?: return AbilityOutcome.failed("${target.name.string} — не Потойбічний, брати нема чого")

        if (caster.isShiftKeyDown) return stealMadness(context, target, victim.sanityLoss.scale)

        val seen = EvidenceLog.by(target.uuid).filter { it.kind == EvidenceLog.Trace.POWER }
        val prey = choosePower(victim.abilities, seen)
            ?: return AbilityOutcome.failed("У ${target.name.string} немає активної сили, яку можна взяти")

        val chance = chance(context.sequence, victim.sequence, seen.count { it.detail == prey.identity.id })
        thread(context, target)

        if (Random.nextDouble() >= chance) {
            context.messaging.actionBar(caster, Component.literal("Сила вислизнула — ${percent(chance)}% було замало"))
            return AbilityOutcome.done("Крадіжка зірвалась")
        }

        val stolen = context.beyonder.grantOffPathway(prey)
        if (!stolen) return AbilityOutcome.failed("Ця сила у вас уже є")
        if (dreamStealer) {
            // Unused, it waits on the long reserve; the first cast re-reckons it to half an hour.
            TheftRegistry.steal(
                caster.uuid, target.uuid, prey.identity,
                hold = RESERVE_MILLIS, suppression = DREAM_SUPPRESSION_MILLIS,
                activeHold = DREAM_HOLD_MILLIS,
            )
        } else {
            TheftRegistry.steal(caster.uuid, target.uuid, prey.identity, HOLD_MILLIS, SUPPRESSION_MILLIS)
        }

        context.messaging.tell(
            target.uuid,
            Component.literal(
                "У вас щось вирвали: «${prey.identity.id}» не відгукнеться " +
                    "${(if (dreamStealer) DREAM_SUPPRESSION_MILLIS else SUPPRESSION_MILLIS) / 60_000} хв",
            ),
        )
        return AbilityOutcome.done(
            if (dreamStealer) {
                "Ви взяли чуже: «${prey.identity.id}» — і воно чекатиме, доки не знадобиться"
            } else {
                "Ви взяли чуже: «${prey.identity.id}» на ${HOLD_MILLIS / 60_000} хв"
            },
        )
    }

    /**
     * What is taken: the power the thief actually saw being used, and otherwise whatever comes
     * to hand.
     *
     * A trace carries the identity of what was cast, so witnessing is not a flag but a fact
     * already written down by the evidence log the Cryptologist reads.
     */
    private fun choosePower(owned: List<AbilitySpec>, seen: List<EvidenceLog.Entry>): AbilitySpec? {
        val active = owned.filter { it.type == AbilityType.ACTIVE }
        if (active.isEmpty()) return null
        val witnessed = seen.asReversed().firstNotNullOfOrNull { trace ->
            active.firstOrNull { it.identity.id == trace.detail }
        }
        return witnessed ?: active.random()
    }

    /** Preparation improves the odds; the victim's own strength worsens them. */
    private fun chance(thief: Sequence, victim: Sequence, traces: Int): Double {
        val bonus = (traces * TRACE_BONUS_PERCENT).coerceAtMost(MAX_TRACE_BONUS_PERCENT)
        val penalty = (thief.level - victim.level).coerceAtLeast(0) * PER_LEVEL_PENALTY_PERCENT
        return ((BASE_CHANCE_PERCENT + bonus - penalty) / 100.0).coerceIn(MIN_CHANCE, MAX_CHANCE)
    }

    /**
     * The other theft: the victim's derangement moves into the thief.
     *
     * Nothing is registered for it — there is no power to give back and nothing to suppress, so
     * the slot stays free. Sanity heals and worsens on its own everywhere else in the mod, and it
     * does here too.
     */
    private fun stealMadness(context: CastContext, target: LivingEntity, victimSanity: Int): AbilityOutcome {
        if (victimSanity <= 0) return AbilityOutcome.failed("${target.name.string} у своєму розумі — брати нема чого")

        val taken = victimSanity.coerceAtMost(MADNESS_TRANSFER)
        context.beyonders.of(target)?.decreaseSanityLoss(taken)
        context.beyonder.increaseSanityLoss(taken)

        thread(context, target)
        context.messaging.tell(target.uuid, Component.literal("У голові прояснилось — хтось забрав це собі"))
        return AbilityOutcome.done("Ви взяли чуже божевілля: $taken")
    }

    /** The thread from the mark to the thief's hand. */
    private fun thread(context: CastContext, target: LivingEntity) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val from = target.position().add(0.0, target.bbHeight * 0.6, 0.0)
        val to = context.origin.add(0.0, HAND_HEIGHT, 0.0)
        val steps = (from.distanceTo(to) / THREAD_STEP).toInt().coerceIn(1, MAX_THREAD_STEPS)
        for (i in 0..steps) {
            val point: Vec3 = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(mote, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.7f, 0.6f,
        )
        (target as? ServerPlayer)?.let {
            context.level.playSound(
                null, it.x, it.y, it.z,
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 1.4f,
            )
        }
    }

    private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

    private fun reach(sequence: Sequence): Double =
        if (sequence.level <= DREAM_STEALER_TIER) DREAM_STEALER_RANGE else RANGE

    private companion object {
        const val RANGE = 50.0
        const val BASE_COOLDOWN_SECONDS = 300
        const val MIN_COOLDOWN_SECONDS = 120

        const val HOLD_MILLIS = 10L * 60 * 1000
        const val SUPPRESSION_MILLIS = 30L * 60 * 1000

        /** From the Dream Stealer the reach, the terms and the patience all grow. */
        const val DREAM_STEALER_TIER = 5
        const val DREAM_STEALER_RANGE = 80.0
        const val DREAM_HOLD_MILLIS = 30L * 60 * 1000
        const val DREAM_SUPPRESSION_MILLIS = 60L * 60 * 1000
        const val RESERVE_MILLIS = 6L * 60 * 60 * 1000

        const val BASE_CHANCE_PERCENT = 40
        const val TRACE_BONUS_PERCENT = 5
        const val MAX_TRACE_BONUS_PERCENT = 25
        const val PER_LEVEL_PENALTY_PERCENT = 8
        const val MIN_CHANCE = 0.10
        const val MAX_CHANCE = 0.95

        /** How much derangement moves across in one sneak-cast. */
        const val MADNESS_TRANSFER = 3

        const val HAND_HEIGHT = 1.2
        const val THREAD_STEP = 0.5
        const val MAX_THREAD_STEPS = 120
    }
}
