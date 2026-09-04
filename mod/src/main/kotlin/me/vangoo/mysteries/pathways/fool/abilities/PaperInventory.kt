package me.vangoo.mysteries.pathways.fool.abilities

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

/** Counting and spending plain paper across the whole inventory — shared by every Fool recipe. */
internal object PaperInventory {

    fun count(player: ServerPlayer): Int =
        player.inventory.nonEquipmentItems.filter { it.item == Items.PAPER }.sumOf { it.count }

    fun consume(player: ServerPlayer, amount: Int) {
        var remaining = amount
        for (stack in player.inventory.nonEquipmentItems) {
            if (remaining <= 0) break
            if (stack.item != Items.PAPER) continue
            val take = minOf(stack.count, remaining)
            stack.shrink(take)
            remaining -= take
        }
    }
}
