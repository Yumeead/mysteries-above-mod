package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Tyrant, Seq. 7 (Seafarer): **Water Film**.
 *
 * The Seafarer wraps the target's head in a film of water: it chokes, blinds and weakens them on
 * dry land. The film is meant to be hard to shed, so the control effects are applied for the
 * whole duration up front rather than refreshed tick by tick — nothing removes them early.
 */
class WaterFilm : ActiveAbility(AbilityIdentity.of("tyrant_water_film")) {

    override val displayName: Component = Component.literal("Плівка Задухи")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Водяна плівка огортає голову цілі: осліплення, слабкість і задуха на суходолі.\n" +
            "▪ Шкода: ${dot(sequence)} щосекунди, ${DURATION_TICKS / 20} с (у воді задуха не діє)\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл.",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(MAX_RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі на лінії зору")

        target.addEffect(MobEffectInstance(MobEffects.DARKNESS, DURATION_TICKS, 0))
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, DURATION_TICKS, 1))
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, DURATION_TICKS, 0))
        context.effects.bodyAura(target, DURATION_TICKS)

        val eye = target.eyePosition
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 1.0f, 0.8f,
        )

        choke(context, target, dot(context.sequence))
        return AbilityOutcome.done("Плівка задухи огортає ${target.name.string}")
    }

    /** A drowning tick once a second, skipped while the target stands in water — the film needs dry air. */
    private fun choke(context: CastContext, target: LivingEntity, dot: Int) {
        var elapsed = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(DOT_PERIOD_TICKS) {
            elapsed += DOT_PERIOD_TICKS
            if (target.isRemoved || elapsed > DURATION_TICKS) return@every handle.cancel()
            if (target.isInWater) return@every

            target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), dot.toFloat())
            val eye = target.eyePosition
            context.level.playSound(
                null, eye.x, eye.y, eye.z,
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.6f,
            )
        }
    }

    private fun dot(sequence: Sequence): Int =
        scale(BASE_DOT, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DOT = 1
        const val MAX_RANGE = 25.0
        const val DURATION_TICKS = 120
        const val DOT_PERIOD_TICKS = 20
        const val COOLDOWN_SECONDS = 30
    }
}
