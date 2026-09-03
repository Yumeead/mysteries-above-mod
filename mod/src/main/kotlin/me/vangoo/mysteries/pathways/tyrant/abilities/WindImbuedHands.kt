package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Wind-Imbued Hands**.
 *
 * The palms wrap in compressed air: for a few seconds every melee blow punches straight through
 * any protection, and a gust bursts from the fist.
 *
 * ## Health, not the damage pipeline
 *
 * "Through armour" has no expression as an [IncomingDamage] bonus: the event fires **before**
 * armour, so anything added to [IncomingDamage.amount] would still be mitigated a moment later,
 * same as it was in the plugin's own note. The only way to truly skip armour is what the plugin
 * did — take the health directly — and that limitation is not something this port can lift.
 */
class WindImbuedHands : ActiveAbility(AbilityIdentity.of("tyrant_wind_imbued_hands")) {

    override val displayName: Component = Component.literal("Руки Вітру")

    override val spiritualityCost: Int = 70

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Долоні огортає стиснене повітря: кожен ваш удар у ближньому бою додає шкоди повз броню.\n" +
            "▪ Додаткова шкода: ${bonus(sequence)}\n" +
            "▪ Тривалість ${DURATION_TICKS / 20} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val owner = context.caster.uuid
        val bonus = bonus(context.sequence)

        context.events.on<IncomingDamage>(durationTicks = DURATION_TICKS) { damage ->
            val attacker = damage.attacker ?: return@on
            if (attacker.uuid != owner) return@on
            if (damage.directCause !== attacker) return@on // melee only, not a projectile

            damage.victim.health = (damage.victim.health - bonus).coerceAtLeast(0f)
            val hit = damage.victim.position().add(0.0, damage.victim.bbHeight * 0.5, 0.0)
            context.level.sendParticles(ParticleTypes.GUST, hit.x, hit.y, hit.z, 1, 0.0, 0.0, 0.0, 0.0)
            context.level.playSound(
                null, hit.x, hit.y, hit.z,
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8f, 1.6f,
            )
        }

        context.effects.bodyAura(context.caster, DURATION_TICKS, counterFlow = ParticleTypes.SMALL_GUST)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0f, 1.3f,
        )
        return AbilityOutcome.done("Долоні огортає стиснене повітря")
    }

    private fun bonus(sequence: Sequence): Int =
        scale(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_BONUS_DAMAGE = 4
        const val DURATION_TICKS = 120
        const val COOLDOWN_SECONDS = 25
    }
}
