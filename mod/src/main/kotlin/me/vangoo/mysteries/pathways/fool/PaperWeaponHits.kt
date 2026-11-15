package me.vangoo.mysteries.pathways.fool

import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.PaperWeapon

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * A blow struck with a paper weapon (Fool, Seq. 7 `PaperWeaponry`) hits harder than the plain
 * item would on its own — and the item itself wears down with every such blow.
 *
 * The effect depends on **what is in the attacker's hand**, not on who the attacker is, so it
 * cannot live on a per-ability subscription: a Beyonder who put the weapon down and handed it to
 * someone else must still see the bonus land, and a per-player scope is wiped on logout and on
 * every server stop besides. [onDamage] is called straight from `AbilityServices`'s own publish
 * sink instead — the same place [me.vangoo.mysteries.pathways.fool.RecentDamageLedger] hooks in.
 */
object PaperWeaponHits {

    fun onDamage(damage: IncomingDamage) {
        val attacker = damage.attacker as? ServerPlayer ?: return
        val hand = attacker.mainHandItem
        val weapon = hand.get(ModComponents.PAPER_WEAPON) ?: return
        val type = weapon.asType() ?: return

        damage.amount += type.bonusDamage.toFloat()

        val victim = damage.victim
        if (type.knockback > 0) {
            val push = victim.position().subtract(attacker.position()).let { Vec3(it.x, 0.0, it.z) }
            if (push.lengthSqr() > 0.01) {
                val scaled = push.normalize().scale(type.knockback)
                victim.setDeltaMovement(scaled.x, 0.35, scaled.z)
            }
        }
        if (type.slowTicks > 0) {
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, type.slowTicks, 2))
        }

        val at = victim.position()
        // A blow with paper sheds paper: a few scraps off the weapon, where it struck.
        scraps(attacker.level(), at.add(0.0, 1.0, 0.0), HIT_SCRAPS, 0.2, 0.06)
        attacker.level().playSound(null, attacker.x, attacker.y, attacker.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.3f)

        degrade(attacker, hand, weapon.degraded())
    }

    private fun degrade(attacker: ServerPlayer, hand: ItemStack, degraded: PaperWeapon) {
        if (degraded.usesLeft <= 0) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            // Worn through: the weapon comes apart in the hand.
            scraps(attacker.level(), attacker.position().add(0.0, 1.0, 0.0), BREAK_SCRAPS, 0.3, 0.08)
            attacker.level().playSound(null, attacker.x, attacker.y, attacker.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.6f)
        } else {
            hand.set(ModComponents.PAPER_WEAPON, degraded)
        }
    }

    private const val HIT_SCRAPS = 3
    private const val BREAK_SCRAPS = 16
}
