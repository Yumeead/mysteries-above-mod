package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.bodyAura
// Aliased: with the same name the member would resolve to itself — `flash` here takes a
// `RgbColor?` and is a perfectly good match for its own call — and recurse until the stack runs out.
import me.vangoo.mysteries.effect.beam as beamShape
import me.vangoo.mysteries.effect.flare as flareShape
import me.vangoo.mysteries.effect.flash as flashShape
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

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

    override fun beam(level: ServerLevel, from: Vec3, to: Vec3, durationTicks: Int, width: Float, color: RgbColor?, sourceWidth: Float) =
        beamShape(scheduling, level, from, to, color ?: pathwayColor, durationTicks, width, sourceWidth)

    override fun flare(level: ServerLevel, at: BlockPos, durationTicks: Int, brightness: Int) =
        flareShape(scheduling, level, at, durationTicks, brightness)

    override fun flash(level: ServerLevel, at: Vec3, radius: Double, strength: Float, color: RgbColor?) =
        flashShape(level, at, radius, strength, color ?: pathwayColor)

    companion object {
        fun of(scheduling: Scheduling, pathwayName: String): LiveEffects =
            LiveEffects(scheduling, PathwayBranding.liquidOf(pathwayName))
    }
}
