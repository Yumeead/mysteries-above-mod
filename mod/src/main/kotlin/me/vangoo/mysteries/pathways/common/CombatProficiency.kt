package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.roundToInt

/**
 * The shared "Combat Proficiency" passive: a weapon in your hands obeys you better.
 *
 * ## The damage bonus is an attribute, and it is **visible**
 *
 * The plugin kept this as a number inside an `EntityDamageByEntityEvent` handler: the player
 * only saw that the enemy went down faster. An `ATTACK_DAMAGE` modifier, by contrast, is
 * printed by vanilla right in the sword's tooltip — feedback the plugin did not have at all,
 * at the price of zero lines.
 *
 * ## A parry became a parry
 *
 * In the plugin this was a flat −20 % incoming damage while a weapon was in hand — that is, a
 * passive shield with an odd condition. Here the condition is the one that makes it
 * **proficiency**: you can only parry what you see. A blow in the back lands in full, and
 * turning to face the attacker is now the player's decision rather than a state of the inventory.
 *
 * A side catch: the `directCause` of an arrow is the arrow itself, so a Justiciar turned towards
 * the archer deflects the shot. The plugin could not do that: it looked at `getDamager()`, i.e.
 * at an archer standing forty blocks somewhere behind.
 */
class CombatProficiency : PermanentPassiveAbility(AbilityIdentity.of("common_combat_proficiency")) {

    override val displayName: Component = Component.literal("Бойова майстерність")

    private val modifier = modifierId(identity.id, "attack_damage")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Зброя у ваших руках слухається краще за чужу.\n" +
            "▪ Шкода зброї: +${weaponBonus(sequence)}\n" +
            "▪ Парирування: −${(PARRY_REDUCTION * 100).roundToInt()}% від удару, який ви бачите " +
            "(зброя в головній руці, нападник перед вами)",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid
        // The service goes into the handler, not the context: `Messaging` holds nobody's identity.
        val messaging = context.messaging
        context.caster.grantAttribute(Attributes.ATTACK_DAMAGE, modifier, weaponBonus(context.sequence))

        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim
            if (victim.uuid != owner) return@on
            val blow = damage.directCause ?: return@on
            if (!holdsWeapon(victim) || !faces(victim, blow)) return@on

            damage.scale(1.0 - PARRY_REDUCTION)
            clang(messaging, victim, blow, damage.attacker)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.ATTACK_DAMAGE, modifier)
    }

    /**
     * The `melee_weapon_enchantable` tag is vanilla's answer to "is this a weapon?": swords,
     * axes and the trident. The mod keeps no material list of its own, so a weapon from another
     * mod that honestly sets the tag parries too.
     */
    private fun holdsWeapon(victim: LivingEntity): Boolean =
        victim.getItemInHand(InteractionHand.MAIN_HAND).typeHolder().`is`(ItemTags.MELEE_WEAPON_ENCHANTABLE)

    /** A blow in the back is not parried: proficiency is what you can see. */
    private fun faces(victim: LivingEntity, blow: Entity): Boolean {
        val toBlow = blow.position().subtract(victim.position())
        if (toBlow.lengthSqr() < 1.0e-4) return false
        return victim.getViewVector(1.0f).dot(toBlow.normalize()) >= FACING_DOT
    }

    /** A ring and a spark at the meeting point — plus a line to the attacker: they must understand why. */
    private fun clang(messaging: Messaging, victim: LivingEntity, blow: Entity, attacker: LivingEntity?) {
        val level = victim.level() as? ServerLevel ?: return
        val contact = victim.eyePosition.add(blow.position().subtract(victim.position()).normalize().scale(0.6))
        level.sendParticles(ParticleTypes.CRIT, contact.x, contact.y, contact.z, 10, 0.1, 0.1, 0.1, 0.25)
        level.playSound(
            null, contact.x, contact.y, contact.z,
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f,
        )
        // The line goes to whoever is responsible for the blow, not to what arrived: the archer
        // must understand why the arrow did not work, and the arrow itself cannot read.
        // Addressing by UUID also filters out mobs — they are simply not in the player list.
        attacker?.let { messaging.actionBar(it.uuid, Component.literal("Ваш удар парировано")) }
    }

    private fun weaponBonus(sequence: Sequence): Double =
        scale(BASE_BONUS_TENTHS, sequence, SequenceScaler.ScalingStrategy.MODERATE) / 10.0

    private companion object {
        /** +2.0 damage at Seq. 9 — in tenths, because [scale] is integral. */
        const val BASE_BONUS_TENTHS = 20
        const val PARRY_REDUCTION = 0.35
        /** ~66° from the look direction on either side: a cone, not a needle. */
        const val FACING_DOT = 0.4
    }
}
