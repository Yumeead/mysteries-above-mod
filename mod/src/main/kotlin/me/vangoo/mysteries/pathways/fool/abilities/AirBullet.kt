package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 7 (Magician): **Air Bullet**.
 *
 * Compressed air, fired as an invisible round — Klein Moretti's "finger gun". Sneaking spends a
 * little more on a heavier shot; the ticket already covers the cheap form, the extra is paid in
 * the cast itself the same way `Verdict` and `PowerProhibition` pay for their pricier branches, so
 * a caster who cannot afford the powered shot still gets the ordinary one instead of nothing.
 */
class AirBullet : ActiveAbility(AbilityIdentity.of("fool_air_bullet")) {

    override val displayName: Component = Component.literal("Повітряна куля")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стискає повітря у невидимий снаряд у ціль під прицілом.\n" +
            "▪ Шкода ${damageFor(sequence)}, дальність ${MAX_RANGE.toInt()} бл.\n" +
            "▪ Присід — посилений постріл: x2 шкода й вартість",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        var damage = damageFor(context.sequence)
        val powered = context.caster.isShiftKeyDown && context.beyonder.payUpkeep(BASE_COST)
        if (powered) damage *= 2

        val eye = context.eye
        val look = context.look
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, if (powered) 0.5f else 1.0f,
        )
        if (powered) {
            context.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.5f)
        }

        val target = context.targeting.lookingAt(MAX_RANGE)
        val impact = target?.eyePosition ?: eye.add(look.scale(MAX_RANGE))
        trail(context, eye, impact, powered)

        if (target != null) {
            target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
            val knockback = look.scale(if (powered) KNOCKBACK * 1.5 else KNOCKBACK)
            target.setDeltaMovement(knockback.x, 0.3, knockback.z)
            context.level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, if (powered) 3 else 1, 0.1, 0.1, 0.1, 0.0)
            context.level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y, impact.z, 10, 0.2, 0.2, 0.2, 0.0)
            context.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 1.0f)
        } else {
            context.level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y, impact.z, 8, 0.1, 0.1, 0.1, 0.0)
            context.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5f, 0.8f)
        }

        return AbilityOutcome.done(if (target != null) "Влучання: $damage шкоди" else "Повз")
    }

    private fun trail(context: CastContext, from: Vec3, to: Vec3, powered: Boolean) {
        val steps = 8
        for (i in 1..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(
                if (powered) ParticleTypes.CLOUD else ParticleTypes.POOF,
                point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun damageFor(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_COST = 35
        const val BASE_COOLDOWN_SECONDS = 2
        const val BASE_DAMAGE = 5
        const val MAX_RANGE = 25.0
        const val KNOCKBACK = 0.8
    }
}
