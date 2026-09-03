package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
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
import net.minecraft.world.entity.LivingEntity

/**
 * Tyrant, Seq. 7 (Seafarer): **Restorative Water**.
 *
 * The Seafarer wraps themselves or the ally they are looking at in living water — a weak
 * regeneration. Deliberately weaker than a potion, but instant and free of materials as support
 * in a fight.
 */
class RestorativeWater : ActiveAbility(AbilityIdentity.of("tyrant_restorative_water")) {

    override val displayName: Component = Component.literal("Цілюща вода")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Живлюща вода огортає вас або союзника, на якого ви дивитесь: слабка регенерація " +
            "на ${durationTicks(sequence) / TICKS_PER_SECOND} с. Поступається зіллям, зате миттєва.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        // The target is an ally on the line of sight, otherwise the caster themselves: a miss must
        // not cost a cast.
        val target: LivingEntity = context.targeting.playerLookingAt(MAX_RANGE) ?: caster
        val ticks = durationTicks(context.sequence)

        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true))

        // The aura holds exactly as long as the water works and travels with the target: the
        // service itself has the pathway colour and the scheduler. The spray is the counter-flow
        // to the strands.
        context.effects.bodyAura(target, ticks, counterFlow = ParticleTypes.SPLASH)
        splash(context, target)

        if (target !== caster) {
            context.messaging.actionBar(
                target.uuid,
                Component.literal("Цілюща вода огортає вас — ${caster.name.string}"),
            )
            return AbilityOutcome.done("Ви огорнули ${target.name.string} цілющою водою")
        }
        return AbilityOutcome.done("Цілюща вода огортає вас")
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    /**
     * A splash at the moment of the cast: without it the aura "fades in" instead of starting.
     *
     * It stays here rather than in `context.effects`, and that is the same boundary: two vanilla
     * calls with no duration and no repetition. What gets into the vocabulary of shapes is what
     * lasts or what a second ability asked for, not everything that is visible.
     */
    private fun splash(context: CastContext, target: LivingEntity) {
        val eye = target.eyePosition
        context.level.sendParticles(ParticleTypes.SPLASH, eye.x, eye.y, eye.z, 20, 0.4, 0.4, 0.4, 0.05)
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
    }

    private companion object {
        /** ~4 s of Regeneration I at Seq. 7; it grows with the Sequence. */
        const val BASE_DURATION_TICKS = 80
        const val MAX_RANGE = 20.0
        const val COOLDOWN_SECONDS = 20
    }
}
