package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SelectiveVisibility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Visionary, Seq. 6 (Hypnotist): **Battle Hypnotism**.
 *
 * One enemy loses the Hypnotist from their senses for ten seconds — everyone else keeps
 * fighting them as normal. [SelectiveVisibility] exists for exactly this: vanilla invisibility
 * is global, and making the caster invisible to everyone here would only duplicate
 * `PsychologicalInvisibility`, the other Seq. 6 power.
 */
class BattleHypnotism : ActiveAbility(AbilityIdentity.of("visionary_battle_hypnotism")) {

    override val displayName: Component = Component.literal("Бойовий гіпноз")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE))
            .toInt().coerceAtLeast(MIN_COOLDOWN_SECONDS)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Змушує ворога, на якого дивитесь, втратити вас з поля зору на ${EFFECT_DURATION_SECONDS}с.\n" +
            "▪ Дальність ${range(sequence).toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(range(context.sequence)) as? ServerPlayer
            ?: return AbilityOutcome.failed("Ви не дивитесь на живу ціль")

        context.level.sendParticles(
            ParticleTypes.SOUL, target.eyePosition.x, target.eyePosition.y, target.eyePosition.z,
            30, 0.4, 0.6, 0.4, 0.0,
        )
        context.level.sendParticles(
            ParticleTypes.WITCH, target.x, target.y + 1.0, target.z, 20, 0.3, 0.5, 0.3, 0.0,
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.6f,
        )
        context.level.playSound(
            null, target.x, target.y, target.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1f, 0.5f,
        )

        context.messaging.actionBar(context.caster, Component.literal("🧠 Ви занурили ${target.name.string} у гіпноз"))
        context.messaging.actionBar(target, Component.literal("🌫 Ваша свідомість розчиняється…"))

        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, EFFECT_DURATION_TICKS, 1))
        target.addEffect(MobEffectInstance(MobEffects.NAUSEA, EFFECT_DURATION_TICKS, 0))

        SelectiveVisibility.hide(context.caster, target)
        val caster = context.caster
        val messaging = context.messaging
        context.scheduling.after(EFFECT_DURATION_TICKS) {
            SelectiveVisibility.show(caster, target)
            messaging.actionBar(target, Component.literal("👁 Реальність повертається"))
            target.level().playSound(
                null, target.x, target.y, target.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1.2f,
            )
        }

        return AbilityOutcome.done("🧠 Ви занурили ${target.name.string} у гіпноз")
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.STRONG).toDouble()

    private companion object {
        const val BASE_RANGE = 15
        const val BASE_COST = 100
        const val BASE_COOLDOWN_SECONDS = 25
        const val MIN_COOLDOWN_SECONDS = 5
        const val EFFECT_DURATION_SECONDS = 10
        const val EFFECT_DURATION_TICKS = EFFECT_DURATION_SECONDS * TICKS_PER_SECOND
    }
}
