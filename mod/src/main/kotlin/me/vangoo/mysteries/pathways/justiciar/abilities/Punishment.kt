package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.justiciar.Punishments
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity

/**
 * Justiciar, Seq. 5 (Disciplinary Paladin): **Punishment**.
 *
 * The novel's Paladin selects a target for Punishment, and from then on shines with a dawn-like glow, grows taller in
 * the target's eyes, and holds its feet in unseen shackles, its blows against it the heavier. That is this power now
 * (the user's call, 17.11.2026): an active mark, not the plugin's passive that waited for a broken Prohibition and
 * chose its penalty by the item in the off hand. A broken Prohibition still marks its breaker, by itself
 * ([Punishments.onBreach]): the Paladin need not have seen it happen.
 */
class Punishment : ActiveAbility(Punishments.IDENTITY) {

    override val displayName: Component = Component.literal("Покарання")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви обираєте ціль під прицілом для покарання на ${Punishments.MARK_TICKS / 20} с (дальність $RANGE бл.).\n" +
            "▪ Ви сяєте світлом світанку, а в очах цілі виростаєте\n" +
            "▪ Невидимі кайдани сковують їй ноги\n" +
            "▪ Ваші удари по ній у півтора раза сильніші\n" +
            "▪ Хто порушить вашу Заборону, буде обраний сам",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE) ?: return AbilityOutcome.failed("Наведіть на ціль")
        context.caster.swing(InteractionHand.MAIN_HAND, true)
        Punishments.mark(context.caster, target, context.effects.pathwayColor)
        return AbilityOutcome.Done()
    }

    private companion object {
        const val RANGE = 20.0
        const val COOLDOWN_SECONDS = 40
    }
}
