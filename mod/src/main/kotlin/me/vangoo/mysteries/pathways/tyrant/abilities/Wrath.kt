package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Tyrant, Seq. 8 (Furious One): **Wrath**.
 *
 * The Furious One briefly gives in to the storm inside: the body swells with strength and the
 * blows come faster than usual. A short burst of Strength + Speed, both growing with the
 * Sequence — and the state [RagingBlow] feeds on to build faster.
 */
class Wrath : ActiveAbility(AbilityIdentity.of("tyrant_wrath")) {

    override val displayName: Component = Component.literal("Лють")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви впадаєте в шал: очі палають, а тіло наливається силою.\n" +
            "▪ Сила ${strengthAmplifier(sequence) + 1}, Швидкість ${speedAmplifier(sequence) + 1} " +
            "на ${durationTicks(sequence) / TICKS_PER_SECOND} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val ticks = durationTicks(context.sequence)

        caster.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, strengthAmplifier(context.sequence)))
        caster.addEffect(MobEffectInstance(MobEffects.SPEED, ticks, speedAmplifier(context.sequence)))

        context.effects.bodyAura(caster, ticks, color = RAGE_COLOR, counterFlow = ParticleTypes.CRIT)
        val eye = caster.eyePosition
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.9f,
        )

        return AbilityOutcome.done("Лють охоплює вас")
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    /** Seq. 8 — Strength I, Seq. 5 — Strength II, Seq. 2 — Strength III. */
    private fun strengthAmplifier(sequence: Sequence): Int =
        SequenceScaler.getSequencePower(sequence.level) / 3

    /** Seq. 8 — Speed II, Seq. 5 — Speed III, Seq. 1 — Speed IV. */
    private fun speedAmplifier(sequence: Sequence): Int =
        1 + SequenceScaler.getSequencePower(sequence.level) / 4

    private companion object {
        /** MODERATE scale: Seq. 8 ~= 7s, Seq. 5 ~= 11s. */
        const val BASE_DURATION_TICKS = 140
        const val COOLDOWN_SECONDS = 20
        /** "Eyes burning with rage" — a fire accent on the aura, not the pathway colour. */
        val RAGE_COLOR = RgbColor.of(255, 70, 40)
    }
}
