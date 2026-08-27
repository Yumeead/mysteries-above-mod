package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.bodyAura
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.entity.LivingEntity

/**
 * Фігури, налаштовані під одного кастера: звідси вони знають його колір шляху.
 *
 * Самі фігури живуть у `mysteries/effect` і про контекст нічого не знають — цей клас
 * лише підставляє їм планувальник і типовий колір. Тому фігуру можна намалювати й
 * поза здібністю (ритуал, предмет, істота), не заводячи заради цього контексту.
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
