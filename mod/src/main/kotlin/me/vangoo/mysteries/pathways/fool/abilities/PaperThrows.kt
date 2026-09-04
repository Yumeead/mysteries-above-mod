package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.PaperThrowDamage
import me.vangoo.mysteries.ability.AbilityContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * The throw itself, shared by [PaperCutter] (Seq. 8) and [PaperWeaponry] (Seq. 7) — each holds
 * its own cooldown map, since the ability instance is shared across the whole pathway, not per
 * player.
 *
 * The plugin launched a real [net.minecraft.world.entity.projectile.Snowball] and waited for
 * `ProjectileHitEvent` — Fabric has no ready hook for that (checked: neither `fabric-api` nor a
 * vanilla callback fires on a generic projectile's impact), and standing up a custom projectile
 * entity just to catch its own hit is a lot of engine for a Seq. 8 utility throw. `AirBullet`,
 * the sibling ability one Sequence down in this very pathway, already settled the same question
 * the same way: an instant hitscan along the caster's gaze, with a particle streak standing in
 * for the flight.
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
        val impact = target?.eyePosition ?: context.eye.add(context.look.scale(THROW_RANGE))

        streak(context, impact)
        context.level.playSound(
            null, caster.x, caster.y, caster.z,
            SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 2.0f,
        )

        if (target != null) {
            target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
            context.level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 15, 0.3, 0.3, 0.3, 0.0)
            context.level.sendParticles(ParticleTypes.SWEEP_ATTACK, impact.x, impact.y, impact.z, 3, 0.2, 0.2, 0.2, 0.0)
            context.level.playSound(
                null, impact.x, impact.y, impact.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.5f,
            )
        } else {
            context.level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 8, 0.1, 0.1, 0.1, 0.0)
            context.level.playSound(
                null, impact.x, impact.y, impact.z,
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.5f, 2.0f,
            )
        }
        return true
    }

    private fun streak(context: AbilityContext, to: net.minecraft.world.phys.Vec3) {
        val from = context.eye
        val steps = 6
        for (i in 1..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
