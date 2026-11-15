package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 7 (Magician): **Air Bullet**.
 *
 * Compressed air, fired as an invisible round — Klein Moretti's "finger gun". The round itself is
 * never seen, as in the novel: only the air it was squeezed out of, a ring at the fingertips, and the
 * ring it bursts into where it strikes. Sneaking spends a
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
        ring(context, eye.add(look.scale(MUZZLE)).add(0.0, MUZZLE_DROP, 0.0), look, MUZZLE_RING)
        ring(context, impact.subtract(look.scale(0.2)), look, if (powered) IMPACT_RING * 1.5 else IMPACT_RING)

        if (target != null) {
            target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
            val knockback = look.scale(if (powered) KNOCKBACK * 1.5 else KNOCKBACK)
            target.setDeltaMovement(knockback.x, 0.3, knockback.z)
            target.hurtMarked = true
            context.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 1.0f)
        } else {
            context.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5f, 0.8f)
        }

        return AbilityOutcome.Done()
    }

    /**
     * A ring of squeezed air across the line of fire, for a blink: still points that stand in the world
     * (`count = 1`, no deltas, so no glyph latches onto a body), gone before anyone could call it a shape.
     */
    private fun ring(context: CastContext, at: Vec3, along: Vec3, radius: Double) {
        val side = along.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up = side.cross(along).normalize()
        val air = ModParticles.glyph(AIR)
        for (i in 0 until RING_POINTS) {
            val angle = i * 2 * Math.PI / RING_POINTS
            val point = at.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius))
            context.level.sendParticles(air, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
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
        const val MUZZLE = 0.7
        const val MUZZLE_DROP = -0.15
        const val MUZZLE_RING = 0.1
        const val IMPACT_RING = 0.35
        const val RING_POINTS = 12
        val AIR: RgbColor = RgbColor.of(214, 222, 230)
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}
