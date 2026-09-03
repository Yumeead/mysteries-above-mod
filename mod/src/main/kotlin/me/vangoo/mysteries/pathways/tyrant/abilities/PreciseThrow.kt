package me.vangoo.mysteries.pathways.tyrant.abilities

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
 * Tyrant, Seq. 7 (Seafarer): **Precise Throw**.
 *
 * The Seafarer's sense of bearing makes the throw unmissable: a bolt of water strikes whatever
 * they are looking at. Modest in force, but a guaranteed-hit outlet for a navigator's skill.
 */
class PreciseThrow : ActiveAbility(AbilityIdentity.of("tyrant_precise_throw")) {

    override val displayName: Component = Component.literal("Точний Кидок")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Чуття орієнтації робить кидок безпомилковим: водяний болт летить у ціль, " +
            "у яку ви дивитесь, і не промахується.\n" +
            "▪ Шкода: ${damage(sequence)}\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі на лінії зору")

        val start = context.caster.eyePosition
        val end = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        trail(context, start, end)

        val damage = damage(context.sequence)
        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
        val push = end.subtract(start).normalize().scale(KNOCKBACK)
        target.deltaMovement = target.deltaMovement.add(push.x, 0.0, push.z)
        target.hurtMarked = true

        splash(context, end)
        context.level.playSound(
            null, start.x, start.y, start.z,
            SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.1f,
        )
        return AbilityOutcome.done("Водяний болт влучив у ${target.name.string}")
    }

    /** A bolt of bubbles along the line, drawn once — a puff at the moment of the cast, not a shape that lasts. */
    private fun trail(context: CastContext, start: Vec3, end: Vec3) {
        val step = end.subtract(start).scale(1.0 / STEPS)
        var point = start
        repeat(STEPS) {
            point = point.add(step)
            context.level.sendParticles(ParticleTypes.BUBBLE, point.x, point.y, point.z, 2, 0.06, 0.06, 0.06, 0.0)
        }
    }

    private fun splash(context: CastContext, at: Vec3) {
        context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.06)
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 8
        const val MAX_RANGE = 30.0
        const val KNOCKBACK = 0.5
        const val STEPS = 10
        const val COOLDOWN_SECONDS = 6
    }
}
