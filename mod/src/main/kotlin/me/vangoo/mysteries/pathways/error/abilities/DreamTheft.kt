package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Error, Seq. 5 (Dream Stealer): **Dream Theft** — the manifestation the Sequence is named after.
 *
 * *"Dream Stealers are able to steal, store and transfer Dreams"*, and *"infuse scenes within the
 * Dream to influence their target's future decisions"*. Two casts, therefore, and the second one
 * is the point: a stolen dream is worth nothing until it is poured into somebody else.
 *
 * ## The carrier is the slot, not an item
 *
 * A dream in a bottle could be dropped, traded, duplicated or kept through death. So the dream is
 * held where the Dream Stealer's other thefts are held — in the single slot — and it can no more
 * be taken off them than a thought can. The mark, meanwhile, cannot sleep at all: their nights
 * belong to someone else until the slot frees.
 *
 * The sleep refusal is a ready Fabric hook (`EntitySleepEvents.ALLOW_SLEEPING`, hung once in
 * [ConceptualTheft.install]); whether it bites is decided by the registry.
 *
 * ## What the scene does
 *
 * Poured into a third party, the dream leaves a night's worth of certainty behind: a stretch of
 * Regeneration and Strength if the Dream Stealer means well by them. The wiki's "influence their
 * future decisions" has no honest Minecraft referent beyond that — a mod does not get to steer a
 * player's choices, only to make one of them feel better.
 */
class DreamTheft : ActiveAbility(ConceptualTheft.DREAM) {

    override val displayName: Component = Component.literal("Крадіжка сну")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Перший каст забирає чужий сон, другий — вливає його комусь іншому.\n" +
            "▪ Ціль під поглядом, до ${ConceptualTheft.RANGE.toInt()} бл.\n" +
            "▪ Поки сон у вас, його власник не засне взагалі — до ${HOLD_MILLIS / 60_000} хв\n" +
            "▪ Влитий сон дає ${SCENE_TICKS / TICKS_PER_SECOND} с відновлення й сили\n" +
            "▪ Сон займає ваш єдиний слот украденого\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? =
        if (carrying(context)) null else ConceptualTheft.mark(context)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = ConceptualTheft.mark(context)
            ?: return AbilityOutcome.failed("Наведіть погляд на того, з ким маєте справу")

        return if (carrying(context)) pour(context, target) else take(context, target)
    }

    /** The first cast: the mark's nights become the thief's property. */
    private fun take(context: CastContext, target: LivingEntity): AbilityOutcome {
        ConceptualTheft.slotTaken(context)?.let { return it }

        ConceptualTheft.hold(context, target, ConceptualTheft.DREAM, HOLD_MILLIS)
        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, "Сон більше не ваш — ліжко вас не прийме")
        return AbilityOutcome.done("Сон ${target.name.string} у вас")
    }

    /** The second cast: the dream is poured into somebody else, and the slot frees. */
    private fun pour(context: CastContext, target: LivingEntity): AbilityOutcome {
        val held = TheftRegistry.heldBy(context.caster.uuid)
        if (held != null && held.victimId == target.uuid) {
            return AbilityOutcome.failed("Це його власний сон — вливати його назад немає сенсу")
        }

        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, SCENE_TICKS, 0, false, true, true))
        target.addEffect(MobEffectInstance(MobEffects.STRENGTH, SCENE_TICKS, 0, false, true, true))
        TheftRegistry.release(context.caster.uuid)

        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, "Вам наснилось щось чуже — і воно було добрим")
        return AbilityOutcome.done("Сон перелито в ${target.name.string}")
    }

    private fun carrying(context: CastContext): Boolean =
        TheftRegistry.heldBy(context.caster.uuid)?.identity == ConceptualTheft.DREAM

    private companion object {
        const val HOLD_MILLIS = 30L * 60 * 1000
        const val SCENE_TICKS = 60 * TICKS_PER_SECOND
    }
}
