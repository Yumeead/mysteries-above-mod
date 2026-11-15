package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RecentDamageHeal
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.pathways.fool.RecentDamageLedger
import me.vangoo.mysteries.pathways.fool.scraps
import me.vangoo.mysteries.pathways.fool.thread
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Fool, Seq. 7 (Magician): **Damage Transfer**.
 *
 * Reworked, same as the plugin reworked it: not a hostile power that moves a hit onto someone
 * else, but a heal that closes half of whatever wound is still fresh. A plain cast heals the
 * Magician's own recent damage; sneaking and looking at someone heals **their** recent damage
 * instead — the pool comes from [RecentDamageLedger], not from watching this one cast.
 *
 * ## The wound comes off as paper
 *
 * A Magician's trick, not a priest's blessing: the wound peels off the body in red-stained scraps
 * that flutter down and are gone, and the skin under them is whole. Healing someone else, a thread
 * runs from the Magician's hand to them first, and the scraps peel off at its end.
 */
class DamageTransfer : ActiveAbility(AbilityIdentity.of("fool_damage_transfer")) {

    override val displayName: Component = Component.literal("Перенос шкоди")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Загоює половину шкоди, отриманої за останні ${RecentDamageHeal.DAMAGE_WINDOW_MS / 1000} с " +
            "(до ${RecentDamageHeal.healCeiling(sequence).roundToInt()} HP, не до фулл).\n" +
            "▪ Shift + погляд на ціль — лікує її з її ж недавньої шкоди",
    )

    override fun cast(context: CastContext): AbilityOutcome =
        if (context.caster.isShiftKeyDown) healTarget(context) else healSelf(context)

    private fun healSelf(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val heal = RecentDamageHeal.healAmount(RecentDamageLedger.take(caster.uuid), context.sequence)
        if (heal <= 0) return AbilityOutcome.failed("Ви не отримували шкоди останнім часом")

        caster.heal(heal.toFloat())
        val at = caster.position()
        peel(context, caster, heal)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f)

        return AbilityOutcome.Done()
    }

    private fun healTarget(context: CastContext): AbilityOutcome {
        val target: LivingEntity = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі в межах $MAX_RANGE бл.")

        val heal = RecentDamageHeal.healAmount(RecentDamageLedger.take(target.uuid), context.sequence)
        if (heal <= 0) return AbilityOutcome.failed("Ціль не отримувала шкоди останнім часом")

        thread(context.level, context.caster, target, context.effects.pathwayColor, THREAD_TICKS)
        target.heal(heal.toFloat())
        val at = target.position()
        peel(context, target, heal)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 1.2f)

        return AbilityOutcome.Done()
    }

    /** The wound off [body] in stained scraps — more of them the more was healed. */
    private fun peel(context: CastContext, body: LivingEntity, heal: Double) {
        val count = (MIN_SCRAPS + heal * SCRAPS_PER_HP).toInt().coerceAtMost(MAX_SCRAPS)
        scraps(context.level, body.position().add(0.0, body.bbHeight * 0.6, 0.0), count, 0.25, 0.04, WOUND)
    }

    private companion object {
        const val BASE_COST = 100
        const val BASE_COOLDOWN_SECONDS = 45
        const val MAX_RANGE = 8.0
        const val THREAD_TICKS = 12
        const val MIN_SCRAPS = 4
        const val SCRAPS_PER_HP = 1.0
        const val MAX_SCRAPS = 16
        val WOUND: RgbColor = RgbColor.of(184, 70, 62)
    }
}
