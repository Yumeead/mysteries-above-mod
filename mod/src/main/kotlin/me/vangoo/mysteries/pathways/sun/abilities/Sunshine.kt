package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Sun, Seq. 8 (Light Seeker): **Sunshine**.
 *
 * The pathway's workhorse: a cheap beam of holy light on a short cooldown. Not the strongest
 * thing the Sun can do — the most frequent.
 *
 * The unclean (a dark pathway or undead) take full damage, ignition and blinding; everyone else
 * only reduced damage. This is not a softening for balance's sake but what light **is**: it
 * judges by the target's nature, and it hurts a zombie more than a person.
 */
class Sunshine : ActiveAbility(AbilityIdentity.of("sun_sunshine")) {

    override val displayName: Component = Component.literal("Сонячне сяйво")

    override val spiritualityCost: Int = 40

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Промінь святого світла в ціль під прицілом.\n" +
            "▪ Шкода: ${damage(sequence)} по нечисті, знижена по решті\n" +
            "▪ Нечисті також: підпал ${FIRE_TICKS / TICKS_PER_SECOND} с і засліплення " +
            "${BLIND_TICKS / TICKS_PER_SECOND} с\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл., відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі")

        val mote = ModParticles.mote(context.effects.pathwayColor)
        ray(context, target, mote)

        val dealt = smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
        val profane = target.isProfane(context.beyonders)
        if (profane) {
            target.igniteForSeconds((FIRE_TICKS / TICKS_PER_SECOND).toFloat())
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0))
        }

        context.messaging.actionBar(target.uuid, Component.literal("Вас торкнулось святе світло"))
        return AbilityOutcome.done(
            "Сяйво вразило ${target.name.string} на ${dealt.toInt()}" +
                if (profane) " — нечисть горить" else "",
        )
    }

    /** A beam from the eye to the target plus a flash at the point of impact. */
    private fun ray(context: CastContext, target: LivingEntity, mote: ParticleOptions) {
        val from = context.eye
        val to = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        val steps = (from.distanceTo(to) / STEP).toInt().coerceIn(1, MAX_STEPS)
        for (i in 0..steps) {
            val point: Vec3 = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(mote, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0)
        }
        context.level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 12, 0.2, 0.2, 0.2, 0.03)
        context.level.playSound(
            null, to.x, to.y, to.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.8f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val MAX_RANGE = 15.0
        const val BASE_DAMAGE = 8
        const val COOLDOWN_SECONDS = 5
        const val FIRE_TICKS = 3 * TICKS_PER_SECOND
        const val BLIND_TICKS = 3 * TICKS_PER_SECOND
        const val STEP = 0.3
        const val MAX_STEPS = 90
    }
}
