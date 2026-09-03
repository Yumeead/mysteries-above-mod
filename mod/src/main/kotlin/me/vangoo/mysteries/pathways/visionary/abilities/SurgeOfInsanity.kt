package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player

/**
 * Visionary, Seq. 7 (Psychiatrist): **Surge of Insanity**.
 *
 * A burst of raw dread: Blindness and Weakness for every player nearby, sanity torn from any
 * Beyonder among them, plain damage for everything else. One cast, no state to keep — the
 * particles are a puff at the moment of casting, not a lasting shape, so they stay direct
 * vanilla calls rather than going through `context.effects`.
 */
class SurgeOfInsanity : ActiveAbility(AbilityIdentity.of("visionary_surge_of_insanity")) {

    override val displayName: Component = Component.literal("Всплеск божевілля")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "У радіусі ${range(sequence).toInt()} бл. накладає Сліпоту та Слабкість на " +
            "${duration(sequence) / TICKS_PER_SECOND} с.\n" +
            "▪ Потойбічні втрачають ${sanityLoss(sequence)} втрати глузду\n" +
            "▪ Інші істоти отримують $MOB_DAMAGE шкоди",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val radius = range(context.sequence)
        val durationTicks = duration(context.sequence)
        val sanity = sanityLoss(context.sequence)
        val origin = context.origin

        context.level.sendParticles(
            ParticleTypes.SMOKE, origin.x, origin.y + 1.0, origin.z, 40, radius, 1.0, radius, 0.02,
        )
        context.level.playSound(
            null, origin.x, origin.y, origin.z,
            SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 1.0f, 0.6f,
        )
        context.level.playSound(
            null, origin.x, origin.y, origin.z,
            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 0.5f,
        )

        var players = 0
        var mobs = 0
        context.targeting.livingNear(radius).forEach { target ->
            context.level.sendParticles(
                DustParticleOptions(0x9932CC, 1.2f),
                target.x, target.y + 1.0, target.z, 12, 0.2, 0.2, 0.2, 0.0,
            )
            if (target is Player) {
                target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 0))
                target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0))
                context.beyonders.of(target.uuid)?.increaseSanityLoss(sanity)
                context.messaging.actionBar(target.uuid, Component.literal("🌀 Розум затьмарюється  +$sanity глузду"))
                players++
            } else {
                target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), MOB_DAMAGE)
                mobs++
            }
        }

        return AbilityOutcome.done(
            if (players + mobs == 0) "✦ Поблизу немає цілей" else "🌀 Божевілля вивільнено · гравців: $players, істот: $mobs",
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * TICKS_PER_SECOND

    private fun sanityLoss(sequence: Sequence): Int =
        scale(BASE_SANITY_LOSS, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val BASE_RANGE = 5
        const val COOLDOWN_SECONDS = 30
        const val BASE_DURATION_SECONDS = 10
        const val BASE_SANITY_LOSS = 5
        const val MOB_DAMAGE = 8.0f
    }
}
