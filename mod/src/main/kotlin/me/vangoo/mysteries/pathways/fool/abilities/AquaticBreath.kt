package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.BreathDepthLimit
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks

/**
 * Fool, Seq. 7 (Magician): **Aquatic Breath**.
 *
 * The Magician breathes underwater without limit as long as they stay within reach of the
 * surface; dive past that reach and air drains as usual — a trick of misdirection, not true gills.
 */
class AquaticBreath : PermanentPassiveAbility(AbilityIdentity.of("fool_aquatic_breath")), Ticking {

    override val tickPeriodTicks: Int = 5

    override val displayName: Component = Component.literal("Підводне дихання")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Дихаєте під водою нескінченно, поки не занурились глибше ${BreathDepthLimit.maxDepth(sequence)} бл. від поверхні.",
    )

    override fun tick(context: AbilityContext) {
        val player = context.caster
        if (!player.isInWater) return
        if (depthBelowSurface(context, player.eyePosition.y, player.blockX, player.blockZ) > BreathDepthLimit.maxDepth(context.sequence)) return
        player.setAirSupply(player.maxAirSupply)
    }

    /** How many water blocks lie above eye level before the first non-water block (the surface). */
    private fun depthBelowSurface(context: AbilityContext, eyeY: Double, x: Int, z: Int): Int {
        val level = context.level
        var pos = BlockPos(x, eyeY.toInt(), z)
        var depth = 0
        while (level.getBlockState(pos).block == Blocks.WATER && depth < MAX_SCAN) {
            depth++
            pos = pos.above()
        }
        return depth
    }

    private companion object {
        const val MAX_SCAN = 64
    }
}
