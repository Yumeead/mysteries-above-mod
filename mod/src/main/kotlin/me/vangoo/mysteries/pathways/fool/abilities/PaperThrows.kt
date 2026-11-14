package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.PaperThrowDamage
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.pathways.fool.throwSheet
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The throw itself, shared by [PaperCutter] (Seq. 8) and its Seq. 7 weaving — the ability instance is
 * shared across the whole pathway, so the cooldown map is the caller's.
 *
 * ## A sheet flies, and it strikes when it gets there
 *
 * The plugin launched a real snowball and waited for `ProjectileHitEvent`; the first port made it an
 * instant hitscan with a streak of cloud puffs standing in for the flight. Now the sheet is **seen**
 * flying — one packet, spinning flat through the air (`pathways/fool/Paper.kt`) — and the blow lands
 * the tick it arrives, not the tick it was thrown. The target is still chosen along the gaze at the
 * throw: a card thrown at someone is thrown at them. If they are gone or dead by the time it gets
 * there, it tears on nothing.
 */
internal object PaperThrows {

    private const val THROW_RANGE = 12.0

    fun throwOnce(context: AbilityContext, lastThrowTick: MutableMap<UUID, Long>): Boolean {
        val caster = context.caster
        val now = context.scheduling.tick
        val last = lastThrowTick[caster.uuid]
        if (last != null && now - last < PaperThrowDamage.THROW_COOLDOWN_TICKS) return false

        val mainHand = caster.mainHandItem
        if (mainHand.item != Items.PAPER || mainHand.count < 1) return false
        lastThrowTick[caster.uuid] = now
        mainHand.shrink(1)

        val damage = PaperThrowDamage.damageFor(context.sequence)
        val target = context.targeting.lookingAt(THROW_RANGE)
        val impact = target?.let { it.position().add(0.0, it.bbHeight * BODY_SHARE, 0.0) }
            ?: context.eye.add(context.look.scale(THROW_RANGE))
        // Off the hand, not out of the eye: a little ahead, a little down.
        val hand = context.eye.add(context.look.scale(HAND_REACH)).add(0.0, HAND_DROP, 0.0)

        val level = context.level
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 2.0f)
        val arrival = throwSheet(level, hand, impact, hit = target != null)
        if (target == null) return true

        val source = level.damageSources().indirectMagic(caster, caster)
        context.scheduling.after(arrival) {
            if (!target.isAlive || target.level() !== level) return@after
            target.hurtServer(level, source, damage.toFloat())
            val at: Vec3 = target.position()
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.5f)
        }
        return true
    }

    /** How far up a body the sheet strikes: about the chest. */
    private const val BODY_SHARE = 0.6
    private const val HAND_REACH = 0.5
    private const val HAND_DROP = -0.2
}
