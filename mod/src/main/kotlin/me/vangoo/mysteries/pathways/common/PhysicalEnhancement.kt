package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * The shared "strong body" passive: more health plus, where needed, a few vanilla effects. One
 * for every pathway — parameterised by name, description and the number of hearts.
 *
 * ## Health is an attribute, not a `HEALTH_BOOST`
 *
 * In the plugin this was `AttributeInstance.setBaseValue(20 + bonus)` plus restoring the base
 * by hand in `onDeactivate` — that is, **editing the player's base value**, which is baked into
 * the file and outlives everything, including removing the mod. One server crash between
 * `onActivate` and `onDeactivate` is enough for the base to stay raised forever.
 *
 * A transient modifier has no such property: it is not in the player's file at all, so the
 * "cleanup" after a crash happens by itself.
 *
 * Effects stayed effects — there is no attribute for `WATER_BREATHING` or `NIGHT_VISION`. But
 * they are infinite and hidden, so they take no room in the potion list.
 */
open class PhysicalEnhancement(
    identity: AbilityIdentity,
    override val displayName: Component,
    private val flavour: String,
    /** How many hearts it adds at Seq. 9; beyond that it grows with the Sequence. */
    private val hearts: Int,
    private vararg val effects: Holder<MobEffect>,
) : PermanentPassiveAbility(identity) {

    private val modifier = modifierId(identity.id, "max_health")

    override fun description(sequence: Sequence): Component = Component.literal(
        "$flavour\n▪ Здоров'я: +${bonusHealth(sequence) / 2.0} ❤" +
            if (effects.isEmpty()) "" else "\n▪ Постійних ефектів: ${effects.size}",
    )

    override fun onActivate(context: AbilityContext) {
        val player = context.caster
        player.grantAttribute(Attributes.MAX_HEALTH, modifier, bonusHealth(context.sequence).toDouble())
        effects.forEach { effect ->
            player.forceAddEffect(MobEffectInstance(effect, INFINITE, 0, true, false, false), null)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        val player = context.caster
        player.revokeAttribute(Attributes.MAX_HEALTH, modifier)
        effects.forEach { player.removeEffect(it) }
        // The maximum just dropped — vanilla will not pull health above it down by itself.
        if (player.health > player.maxHealth) player.health = player.maxHealth
    }

    private fun bonusHealth(sequence: Sequence): Int =
        scale(hearts * 2, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        /** The vanilla marker for infinite duration. */
        const val INFINITE = -1
    }
}
