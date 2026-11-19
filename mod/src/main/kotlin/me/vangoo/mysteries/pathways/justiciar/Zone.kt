package me.vangoo.mysteries.pathways.justiciar

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * A square of ground on the block grid, [half] blocks out from the block [centre] each way (a side of `2 * half + 1`),
 * open to the sky and the deep: a Sheriff's territory is ground, not a box of air.
 *
 * Square, not round, by the user's call (19.11.2026): a jurisdiction is drawn the way a town's bounds are, and it
 * must not be mistaken for the round of a Judge's Prohibition.
 */
class Zone(val centre: BlockPos, val half: Int) {

    fun contains(pos: Vec3): Boolean =
        abs(pos.x - (centre.x + 0.5)) <= half + 0.5 && abs(pos.z - (centre.z + 0.5)) <= half + 0.5
}
