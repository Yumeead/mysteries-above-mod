package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Seq. 7 (Solar High Priest): **Resistance to Horror**.
 *
 * Holy light resists fear: every second it cuts two seconds off the duration of mental debuffs,
 * hurrying their expiry. It removes nothing instantly — slowness is not protected against either,
 * as it counts here as a physical effect rather than a mental one.
 *
 * A tick fits here, unlike in `IllusoryScales`: a debuff expires **on its own**, and there is no
 * event at all one could wait for. That is exactly why `Ticking` is now written out explicitly —
 * the passive declares that it needs a clock.
 */
class ResistanceToHorror :
    PermanentPassiveAbility(AbilityIdentity.of("sun_resistance_to_horror")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Стійкість до жаху")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Святе світло не дає страху вкоренитись.\n" +
            "▪ Нудота, Сліпота, Слабкість і Темрява минають швидше: щосекунди " +
            "вони коротшають ще на ${REDUCTION_TICKS / TICKS_PER_SECOND} с",
    )

    override fun tick(context: AbilityContext) {
        FEAR_EFFECTS.forEach { effect -> shorten(context, effect) }
    }

    /**
     * Shortens one debuff by [REDUCTION_TICKS].
     *
     * `addEffect` will not do here: the vanilla `update` takes the stronger of the two, so it
     * simply ignores a shorter duration. `forceAddEffect` puts the instance in place of the
     * previous one and notifies the client itself — otherwise the icon would show the old duration.
     */
    private fun shorten(context: AbilityContext, effect: Holder<MobEffect>) {
        val player = context.caster
        val active = player.getEffect(effect) ?: return
        if (active.isInfiniteDuration) return

        val remaining = active.duration - REDUCTION_TICKS
        if (remaining <= 0) {
            player.removeEffect(effect)
            burnOff(context, player)
            return
        }

        player.forceAddEffect(
            MobEffectInstance(
                effect,
                remaining,
                active.amplifier,
                active.isAmbient,
                active.isVisible,
                active.showIcon(),
            ),
            null,
        )
    }

    /** A debuff burned out — the light is visible. While it is only being shortened, the passive stays silent. */
    private fun burnOff(context: AbilityContext, player: ServerPlayer) {
        context.level.sendParticles(
            ParticleTypes.GLOW,
            player.x, player.y + player.bbHeight * 0.75, player.z,
            8, 0.3, 0.3, 0.3, 0.01,
        )
    }

    private companion object {
        /** −2 s of a debuff's duration per trigger. */
        const val REDUCTION_TICKS = 2 * TICKS_PER_SECOND

        val FEAR_EFFECTS: List<Holder<MobEffect>> = listOf(
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.WEAKNESS,
            MobEffects.DARKNESS,
        )
    }
}
