package me.vangoo.mysteries.creature

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3

/**
 * Where a body may stand: a sturdy floor and two clear, dry blocks above it.
 *
 * The plugin learnt this the hard way — a structure spawn that put a creature into the wall it was
 * standing next to "one-shot from the chest" — and split its finder in two: one that may fail, for
 * spawning, and one that never does, for nudging an entity already in the world. Here there is
 * only the one that may fail; every caller decides what failing means.
 */
object SafeSpots {

    /** [pos] itself or a block above or below it, if a body fits there; the feet's centre. */
    fun standable(level: ServerLevel, pos: BlockPos): Vec3? =
        sequenceOf(pos, pos.above(), pos.below())
            .firstOrNull { fits(level, it) }
            ?.let { Vec3.atBottomCenterOf(it) }

    /** A random standable spot within [radius] of [center], or `null` after a handful of tries. */
    fun near(level: ServerLevel, center: Vec3, radius: Double, random: RandomSource, tries: Int = 16): Vec3? {
        repeat(tries) {
            val angle = random.nextDouble() * Math.PI * 2
            val reach = radius * (0.4 + 0.6 * random.nextDouble())
            val pos = BlockPos.containing(center.x + Math.cos(angle) * reach, center.y, center.z + Math.sin(angle) * reach)
            standable(level, pos)?.let { return it }
        }
        return null
    }

    /** Water two blocks deep — for the bodies that are helpless on land. */
    fun swimmable(level: ServerLevel, pos: BlockPos): Vec3? =
        if (level.getFluidState(pos).`is`(FluidTags.WATER) && level.getFluidState(pos.above()).`is`(FluidTags.WATER)) {
            Vec3.atBottomCenterOf(pos)
        } else {
            null
        }

    private fun fits(level: ServerLevel, feet: BlockPos): Boolean {
        val floor = feet.below()
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false
        if (level.getFluidState(floor).`is`(FluidTags.LAVA)) return false
        return clear(level, feet) && clear(level, feet.above())
    }

    private fun clear(level: ServerLevel, pos: BlockPos): Boolean =
        level.getBlockState(pos).getCollisionShape(level, pos).isEmpty && level.getFluidState(pos).isEmpty
}
