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
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind's Whisper**.
 *
 * The wind carries the sound of others' breathing: everyone alive nearby glows through walls
 * for a short while.
 *
 * The vanilla [MobEffects.GLOWING] does what the plugin's own outline bookkeeping did by hand —
 * it expires by itself, so there is no ledger of who is currently lit to clean up. The one thing
 * this drops is "only the caster sees it": vanilla's outline renders for everyone, where the
 * plugin's team-coloured glow was caster-only. Making it caster-only again would need the same
 * per-viewer render channel [me.vangoo.mysteries.ability.SelectiveVisibility] built for
 * `BattleHypnotism` — a real subsystem, not owed to a Seq. 6 utility power.
 */
class Eavesdrop : ActiveAbility(AbilityIdentity.of("tyrant_eavesdrop")) {

    override val displayName: Component = Component.literal("Шепіт Вітру")

    override val spiritualityCost: Int = 30

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вітер приносить звуки чужого дихання: усе живе поблизу проступає крізь стіни.\n" +
            "▪ Радіус ${range(sequence)} бл., тривалість ${duration(sequence) / 20} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val range = range(context.sequence).toDouble()
        val ticks = duration(context.sequence)

        val box = caster.boundingBox.inflate(range)
        var revealed = 0
        for (target in context.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, box) { it !== caster }) {
            target.addEffect(MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, true))
            revealed++
        }

        val center = caster.position()
        context.level.sendParticles(ParticleTypes.SMALL_GUST, center.x, center.y + 1.5, center.z, 20, range * 0.3, 0.5, range * 0.3, 0.05)
        context.level.playSound(
            null, center.x, center.y, center.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.7f, 0.6f,
        )

        context.messaging.actionBar(caster.uuid, Component.literal("Вітер шепоче: поруч $revealed присутностей"))
        return AbilityOutcome.done("Вітер приносить звуки чужого дихання")
    }

    private fun range(sequence: Sequence): Int =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * 20

    private companion object {
        const val BASE_RANGE = 15
        const val BASE_DURATION_SECONDS = 8
        const val COOLDOWN_SECONDS = 20
    }
}
