package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.bodyAura
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.entity.LivingEntity

/**
 * Shapes tuned for one caster: this is where they learn their pathway colour.
 *
 * The shapes themselves live in `mysteries/effect` and know nothing about the context — this
 * class only supplies them with a scheduler and a default colour. That is why a shape can be
 * drawn outside an ability too (a ritual, an item, a creature) without setting up a context for it.
 */
internal class LiveEffects(
    private val scheduling: Scheduling,
    override val pathwayColor: RgbColor,
) : Effects {

    override fun bodyAura(
        target: LivingEntity,
        durationTicks: Int,
        color: RgbColor?,
        counterFlow: ParticleOptions?,
    ) = bodyAura(scheduling, target, durationTicks, color ?: pathwayColor, counterFlow)

    companion object {
        fun of(scheduling: Scheduling, pathwayName: String): LiveEffects =
            LiveEffects(scheduling, PathwayBranding.liquidOf(pathwayName))
    }
}
