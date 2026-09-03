package me.vangoo.mysteries.pathways.common

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.AbstractCandleBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * An altar built in the world: candles burning around a spot, and offerings lying on the ground.
 *
 * `RitualMagic` invented this reading — a rite chooses itself from what the place actually holds,
 * rather than from a chest menu — and Error's `TheftOfHeavenlySecrets` asks the same two
 * questions of the same kind of place. Two askers, so the questions live here rather than twice.
 *
 * The rule the shape carries: **the parameter exists in the world**. An altar can be seen from a
 * distance, its offerings can be stolen, and it makes a spot into a place.
 */
object Altar {

    const val RADIUS: Int = 3
    const val SPAN: Double = 7.0
    const val HEIGHT: Double = 4.0

    /** How many candles burn around. A vanilla block holds up to four — count them. */
    fun candles(level: ServerLevel, centre: BlockPos): Int =
        BlockPos.betweenClosed(
            centre.offset(-RADIUS, -1, -RADIUS),
            centre.offset(RADIUS, 2, RADIUS),
        ).sumOf { pos ->
            val state = level.getBlockState(pos)
            when {
                state.block !is AbstractCandleBlock -> 0
                !AbstractCandleBlock.isLit(state) -> 0
                state.hasProperty(CandleBlock.CANDLES) -> state.getValue(CandleBlock.CANDLES)
                else -> 1
            }
        }

    /** The offering is what **lies on the ground** in the circle, not what is hidden in a pocket. */
    fun offerings(level: ServerLevel, centre: BlockPos): List<ItemEntity> =
        level.getEntitiesOfClass(
            ItemEntity::class.java,
            AABB.ofSize(Vec3.atCenterOf(centre), SPAN, HEIGHT, SPAN),
        )
}
