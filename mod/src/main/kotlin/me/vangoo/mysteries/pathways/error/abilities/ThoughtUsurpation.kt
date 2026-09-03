package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Error, Seq. 5 (Dream Stealer): **Thought Usurpation**.
 *
 * The only one of the seven manifestations the wiki bothers to name. You take the thought your
 * opponent was about to act on; they are left blank for a moment, and **you** do the thing they
 * meant to.
 *
 * Lore: Mobet Zoroast took Ulyssan's intent to breathe an attack — Mobet spat, and Ulyssan stood
 * there confused.
 *
 * ## Dullness is three vanilla things, and the third is the real one
 *
 * Slowness and Mining Fatigue read as "the body did not get the message". What actually costs the
 * victim the fight is the cast lock: for those three seconds none of their powers answer, and
 * `Cooldowns.lockAll` already expresses exactly that.
 *
 * ## The lunge is not decoration
 *
 * The wiki is explicit that at this stage a stolen thought **cannot be kept** — the thief must
 * perform the corresponding action instead. So the caster is thrown forward, into the deed that
 * was not theirs.
 */
class ThoughtUsurpation : ActiveAbility(ConceptualTheft.key("thought")) {

    override val displayName: Component = Component.literal("Крадіжка думки")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви забираєте намір, який ціль щойно збиралась втілити, — і втілюєте його самі.\n" +
            "▪ Ціль під поглядом, до ${ConceptualTheft.RANGE.toInt()} бл.\n" +
            "▪ ${dullTicks(sequence) / TICKS_PER_SECOND} с ціль порожня: не біжить, не б'є як слід, не кличе сил\n" +
            "▪ Вас кидає вперед — украдену думку не втримати, її треба зробити\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = ConceptualTheft.mark(context)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = ConceptualTheft.mark(context)
            ?: return AbilityOutcome.failed("Наведіть погляд на того, чию думку берете")

        val dull = dullTicks(context.sequence)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, dull, SLOWNESS_LEVEL, false, true, true))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, dull, 1, false, true, true))
        (target as? ServerPlayer)?.let {
            context.cooldowns.lockAll(it.uuid, dull / TICKS_PER_SECOND)
        }

        // The stolen intent is spent immediately: the thief is carried into the deed.
        context.caster.push(context.look.x * LUNGE, LUNGE_LIFT, context.look.z * LUNGE)
        context.caster.hurtMarked = true

        ConceptualTheft.reach(context, target)
        ConceptualTheft.tell(context, target, "Ви забули, що саме збирались зробити…")
        return AbilityOutcome.done("Думку ${target.name.string} забрано — робіть її самі")
    }

    private fun dullTicks(sequence: Sequence): Int =
        scale(BASE_DULL_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DULL_TICKS = 3 * TICKS_PER_SECOND
        const val SLOWNESS_LEVEL = 2
        const val LUNGE = 1.4
        const val LUNGE_LIFT = 0.25
    }
}
