package me.vangoo.mysteries.pathways.door.abilities

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

/**
 * Door, Seq. 8 (Trickster): **Flash**.
 *
 * A flash, after which the Trickster is simply not there. Not a teleport — blindness plus a
 * disappearance, which is exactly what an illusionist's trick is.
 *
 * ## Invisibility instead of "hide a player from a player"
 *
 * In the plugin these were `hidePlayerFromTarget` / `showPlayerToTarget` per target, plus a
 * deferred job to show everyone again — and every disconnect between those two points left
 * somebody invisible forever. The vanilla [MobEffects.INVISIBILITY] does the same in one line,
 * expires by itself and survives anything, including a server crash.
 *
 * There is one difference in behaviour: armour and the item in hand give an invisible player
 * away. That is not a flaw — it is what makes the power a choice rather than a "vanish" button.
 */
class Flash : ActiveAbility(AbilityIdentity.of("door_flash")) {

    override val displayName: Component = Component.literal("Спалах")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Сліпучий спалах, під яким ви зникаєте.\n" +
            "▪ Осліплює всіх у ${radius(sequence).toInt()} бл. на ${DURATION_TICKS / TICKS_PER_SECOND} с\n" +
            "▪ Ви стаєте невидимим на той самий час\n" +
            "▪ Відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val blinded = context.targeting.livingNear(radius(context.sequence))
        blinded.forEach { it.addEffect(MobEffectInstance(MobEffects.BLINDNESS, DURATION_TICKS, 1)) }

        context.caster.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, DURATION_TICKS, 0, true, false, true))

        val origin = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 1.0, origin.z, 150, 0.4, 0.6, 0.4, 0.35)
        context.level.playSound(
            null, origin.x, origin.y, origin.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 2.3f,
        )
        context.level.playSound(
            null, origin.x, origin.y, origin.z,
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 2.0f,
        )

        return AbilityOutcome.done(
            if (blinded.isEmpty()) "Спалах — і вас немає" else "Спалах осліпив ${blinded.size}; вас немає",
        )
    }

    private fun radius(sequence: Sequence): Double =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private companion object {
        const val BASE_RADIUS = 10
        const val COOLDOWN_SECONDS = 15
        const val DURATION_TICKS = 3 * TICKS_PER_SECOND
    }
}
