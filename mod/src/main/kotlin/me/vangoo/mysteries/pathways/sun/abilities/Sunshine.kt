package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.burst
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

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

        ray(context, target)

        smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
        if (target.isProfane(context.beyonders)) {
            target.igniteForSeconds((FIRE_TICKS / TICKS_PER_SECOND).toFloat())
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0))
        }
        return AbilityOutcome.Done()
    }

    /**
     * A beam from the hand to the target. Where it lands — a flash for whoever looks, a burst, and
     * for half a second real light.
     */
    private fun ray(context: CastContext, target: LivingEntity) {
        val level = context.level
        val caster = context.caster
        // From the casting hand, not the eye: a beam out of the camera itself is seen end-on — a
        // dot — and from the side of the view the caster watches it fly.
        val yaw = Math.toRadians(caster.yRot.toDouble())
        val side = if (caster.mainArm == HumanoidArm.RIGHT) HAND_ASIDE else -HAND_ASIDE
        // Facing +z, the right hand is towards −x.
        val right = Vec3(-cos(yaw), 0.0, -sin(yaw)).scale(side)
        val from = context.eye.add(caster.lookAngle.scale(HAND_REACH)).add(right).add(0.0, -HAND_DROP, 0.0)
        val to = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        context.effects.beam(level, from, to, BEAM_TICKS, BAND_WIDTH)
        context.effects.flash(level, to, FLASH_RADIUS, FLASH_STRENGTH)
        context.effects.flare(level, BlockPos.containing(to), FLARE_TICKS, FLARE_BRIGHTNESS)
        burst(level, to, IMPACT_RADIUS, context.effects.pathwayColor, IMPACT_POINTS, IMPACT_SPEED)
        level.playSound(
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

        const val BEAM_TICKS = 4
        const val BAND_WIDTH = 0.25f
        const val HAND_REACH = 0.5
        const val HAND_DROP = 0.35
        const val FLASH_RADIUS = 8.0
        const val FLASH_STRENGTH = 0.35f
        const val FLARE_TICKS = 10
        const val FLARE_BRIGHTNESS = 12
        const val IMPACT_RADIUS = 0.2
        const val IMPACT_POINTS = 16
        const val IMPACT_SPEED = 0.12
    }
}
