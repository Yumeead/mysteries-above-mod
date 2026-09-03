package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Visionary, Seq. 7 (Psychiatrist): **Appeasement**.
 *
 * A cleansing calm: negative effects gone, Regeneration up, sanity restored. Sneaking while
 * casting keeps it to the caster; casting plainly spreads it to everyone nearby — the same
 * sneak-picks-the-shape idiom `TravellersDoor` already uses for leap-versus-open, so there is no
 * mode to remember between casts and nothing to store per player.
 *
 * The plugin's area cast also tried to pull anyone present out of a madness rampage. That
 * subsystem does not exist yet (`AbilityExecutor`'s `EXTREME` penalty is a placeholder for it),
 * so there is nothing here to rescue from — the hook returns the day Rampage lands, not before.
 */
class Appeasement : ActiveAbility(AbilityIdentity.of("visionary_appeasement")) {

    override val displayName: Component = Component.literal("Умиротворення")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Очищає розум, знімає шкідливі ефекти, дає Регенерацію на ${regen(sequence) / TICKS_PER_SECOND} с " +
            "і знижує втрату глузду на ${sanity(sequence)}.\n" +
            "▪ Пряме застосування: на всіх у радіусі ${range(sequence).toInt()} бл.\n" +
            "▪ Присідання під час каста: лише на собі",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val regenTicks = regen(context.sequence)
        val sanity = sanity(context.sequence)

        if (context.caster.isShiftKeyDown) {
            appease(context, context.caster, regenTicks, sanity)
            return AbilityOutcome.done("✦ Внутрішній спокій відновлено")
        }

        val radius = range(context.sequence)
        appease(context, context.caster, regenTicks, sanity)
        val others = context.targeting.livingNear(radius)
        others.forEach { appease(context, it, regenTicks, sanity) }

        return AbilityOutcome.done("✦ Умиротворення · цілей: ${others.size + 1}")
    }

    private fun appease(context: CastContext, target: LivingEntity, regenTicks: Int, sanity: Int) {
        val at = target.position()
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 25, 0.4, 0.8, 0.4, 0.0)
        context.level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 15, 0.3, 0.5, 0.3, 0.0)
        context.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 1.0, at.z, 15, 0.4, 0.6, 0.4, 0.0)
        context.level.playSound(
            null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.3f,
        )

        NEGATIVE_EFFECTS.forEach { target.removeEffect(it) }
        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0))

        if (target is ServerPlayer) {
            context.beyonders.of(target.uuid)?.decreaseSanityLoss(sanity)
            context.messaging.actionBar(target, Component.literal("✦ Розум очищено · +$sanity глузду"))
        }
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun regen(sequence: Sequence): Int =
        scale(BASE_REGEN_SECONDS, sequence, SequenceScaler.ScalingStrategy.WEAK) * TICKS_PER_SECOND

    private fun sanity(sequence: Sequence): Int =
        scale(BASE_SANITY_REDUCTION, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val BASE_RANGE = 4
        const val COOLDOWN_SECONDS = 40
        const val BASE_SANITY_REDUCTION = 15
        const val BASE_REGEN_SECONDS = 7

        val NEGATIVE_EFFECTS = listOf(
            MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE,
            MobEffects.WEAKNESS, MobEffects.HUNGER, MobEffects.NAUSEA, MobEffects.BLINDNESS,
            MobEffects.DARKNESS, MobEffects.LEVITATION, MobEffects.UNLUCK,
        )
    }
}
