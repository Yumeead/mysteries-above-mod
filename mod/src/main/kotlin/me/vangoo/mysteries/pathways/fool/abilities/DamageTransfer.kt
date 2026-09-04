package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RecentDamageHeal
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.fool.RecentDamageLedger
import net.minecraft.core.particles.ParticleTypes
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
        context.level.sendParticles(ParticleTypes.HEART, at.x, at.y + 2.0, at.z, 8, 0.3, 0.2, 0.3, 0.0)
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 12, 0.3, 0.4, 0.3, 0.0)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f)

        return AbilityOutcome.done("✦ Ви загоїли %.1f HP".format(heal))
    }

    private fun healTarget(context: CastContext): AbilityOutcome {
        val target: LivingEntity = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі в межах $MAX_RANGE бл.")

        val heal = RecentDamageHeal.healAmount(RecentDamageLedger.take(target.uuid), context.sequence)
        if (heal <= 0) return AbilityOutcome.failed("Ціль не отримувала шкоди останнім часом")

        streak(context, context.origin, target.position())
        target.heal(heal.toFloat())
        val at = target.position()
        context.level.sendParticles(ParticleTypes.HEART, at.x, at.y + 1.5, at.z, 8, 0.3, 0.3, 0.3, 0.0)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 1.2f)

        return AbilityOutcome.done("✦ Ви зцілили ${target.name.string} на %.1f HP".format(heal))
    }

    private fun streak(context: CastContext, from: Vec3, to: Vec3) {
        val steps = 12
        for (i in 1..steps) {
            val point = from.add(0.0, 1.0, 0.0).lerp(to.add(0.0, 1.0, 0.0), i.toDouble() / steps)
            context.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private companion object {
        const val BASE_COST = 100
        const val BASE_COOLDOWN_SECONDS = 45
        const val MAX_RANGE = 8.0
    }
}
