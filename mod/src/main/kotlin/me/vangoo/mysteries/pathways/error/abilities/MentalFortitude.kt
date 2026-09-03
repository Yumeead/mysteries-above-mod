package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import kotlin.math.roundToInt

/**
 * Error, Seq. 6 (Prometheus): **Mental Fortitude**.
 *
 * A mind that has carried other people's powers stops being easy to shake. Any loss of sanity is
 * halved whatever its source, the three effects that work on the head fall off the moment they
 * land, and the mind slowly puts itself back together.
 *
 * ## Not the Sun's Resistance to Horror
 *
 * They touch the same three vanilla effects and do opposite things with them. The Sun **shortens**
 * a debuff — two seconds off its remaining time each second, so the fear burns out faster. This
 * one **refuses** it: the effect is gone on the next beat, and there is nothing to burn out. And
 * only this one touches sanity, in both directions — half the incoming loss, plus a point back
 * every half minute. A Prometheus is not braver than a priest of the Sun; his head is simply no
 * longer entirely his own.
 *
 * ## Why a tick and not an event
 *
 * The mod has no "an effect was applied" moment to wait for, and there is no vanilla hook for one
 * either — the same reason `ResistanceToHorror` still ticks. Sanity recovery is a clock by nature
 * anyway.
 */
class MentalFortitude :
    PermanentPassiveAbility(AbilityIdentity.of("error_mental_fortitude")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Стійкість розуму")

    /** Halves every incoming loss of sanity — `Beyonder.increaseSanityLoss` applies it to all sources. */
    override val sanityLossMultiplier: Double = SANITY_MULTIPLIER

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваш розум звик до чужих думок.\n" +
            "▪ Будь-яка втрата глузду менша на ${((1.0 - SANITY_MULTIPLIER) * 100).roundToInt()}%\n" +
            "▪ Нудота, Сліпота й Темрява спадають із вас одразу\n" +
            "▪ Кожні $RECOVERY_SECONDS с ви повертаєте 1 глузду",
    )

    override fun tick(context: AbilityContext) {
        MENTAL_EFFECTS.forEach { effect ->
            if (context.caster.hasEffect(effect)) {
                context.caster.removeEffect(effect)
                shrugOff(context)
            }
        }

        // The mod's clock is the tick, and here it may be: recovery is not a debt to real time,
        // it is the mind resting — and a paused game rests nobody.
        if (context.scheduling.tick % (RECOVERY_SECONDS * TICKS_PER_SECOND) == 0L) {
            context.beyonder.decreaseSanityLoss(1)
        }
    }

    /** A refused thought is visible: it comes off the head rather than fading in place. */
    private fun shrugOff(context: AbilityContext) {
        val player = context.caster
        context.level.sendParticles(
            ParticleTypes.ENCHANT,
            player.x, player.eyeY + 0.3, player.z,
            10, 0.3, 0.2, 0.3, 0.02,
        )
    }

    private companion object {
        /** Incoming sanity loss is halved. */
        const val SANITY_MULTIPLIER = 0.5
        const val RECOVERY_SECONDS = 30

        val MENTAL_EFFECTS: List<Holder<MobEffect>> = listOf(
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.DARKNESS,
        )
    }
}
