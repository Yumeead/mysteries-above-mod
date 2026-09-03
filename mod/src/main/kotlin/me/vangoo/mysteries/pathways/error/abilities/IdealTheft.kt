package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import kotlin.math.roundToInt

/**
 * Error, Seq. 5 (Dream Stealer): **Ideal Theft**.
 *
 * *"By stealing a target's ideal they can make the target lose motivation."* What a Beyonder's
 * motivation is made of is not a metaphor here: it is their spirituality, and it moves across.
 *
 * ## Two effects, because the wiki describes two things
 *
 * The apathy is what the line says outright — a body that no longer sees the point moves and
 * strikes worse. The transfer is what "took their ideal" has to mean for a Beyonder: fifteen
 * per cent of what they were running on is now running the thief. Against someone who is not a
 * Beyonder only the apathy lands, and that is correct — there was nothing there to take.
 *
 * Instant, so it takes no slot: an ideal cannot be held, only spent.
 */
class IdealTheft : ActiveAbility(ConceptualTheft.key("ideal")) {

    override val displayName: Component = Component.literal("Крадіжка ідеалу")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви забираєте те, заради чого ціль узагалі рухається.\n" +
            "▪ Ціль під поглядом, до ${ConceptualTheft.RANGE.toInt()} бл.\n" +
            "▪ ${apathyTicks(sequence) / TICKS_PER_SECOND} с байдужості: слабкість, млявість, важкі руки\n" +
            "▪ З Потойбічного перетікає ${(TRANSFER * 100).roundToInt()}% його духовності — до вас\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = ConceptualTheft.mark(context)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = ConceptualTheft.mark(context)
            ?: return AbilityOutcome.failed("Наведіть погляд на того, чий ідеал берете")

        val apathy = apathyTicks(context.sequence)
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, apathy, 1, false, true, true))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, apathy, 1, false, true, true))
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, apathy, 0, false, true, true))

        val victim = context.beyonders.of(target)
        val taken = victim?.let { (it.spirituality.current * TRANSFER).roundToInt() } ?: 0
        if (victim != null && taken > 0) {
            victim.setSpirituality(victim.spirituality.decrement(taken))
            context.beyonder.setSpirituality(context.beyonder.spirituality.increment(taken))
        }

        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, "Раптом стало байдуже, навіщо це все…")
        return AbilityOutcome.done(
            if (taken > 0) "Ідеал забрано, і разом із ним $taken духовності" else "Ідеал забрано",
        )
    }

    private fun apathyTicks(sequence: Sequence): Int =
        scale(BASE_APATHY_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_APATHY_TICKS = 60 * TICKS_PER_SECOND
        const val TRANSFER = 0.15
    }
}
