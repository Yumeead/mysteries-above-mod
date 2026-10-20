package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.item.Loot
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.ModItems
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams

/** Loot tables as the game rolls them: what `Loot` adds to other tables, and what it finishes. */
class LootGameTests {

    @GameTest(maxTicks = 1)
    fun aDungeonChestSometimesHoldsAnIngredient(helper: GameTestHelper) {
        // The pool's chance is 3 %: two thousand chests miss it with odds of about 10⁻²⁶.
        val found = roll(helper, BuiltInLootTables.SIMPLE_DUNGEON, 2000)
            .filter { BuiltInRegistries.ITEM.getKey(it.item).namespace == MOD_ID }
        if (found.isEmpty()) helper.fail("жодного інгредієнта в двох тисячах скринь данжу")
        helper.succeed()
    }

    @GameTest(maxTicks = 1)
    fun aPotionFromLootIsAFinishedBottle(helper: GameTestHelper) {
        // Potions weigh 19 of the rare table's ~3000 per pathway: a few hundred rolls bring several.
        val potions = roll(helper, Loot.RARE, 3000).filter { it.`is`(ModItems.BEYONDER_POTION) }
        if (potions.isEmpty()) helper.fail("жодного зілля в трьох тисячах кидків рідкісної таблиці")
        val unfinished = potions.filter { !it.has(DataComponents.POTION_CONTENTS) || it.get(ModComponents.POTION_BREW) == null }
        if (unfinished.isNotEmpty()) helper.fail("зілля без кольору чи складу: ${unfinished.first().components}")
        helper.succeed()
    }

    private fun roll(helper: GameTestHelper, key: ResourceKey<LootTable>, times: Int): List<ItemStack> {
        val table = helper.level.server.reloadableRegistries().getLootTable(key)
        val params = LootParams.Builder(helper.level)
            .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
            .create(LootContextParamSets.CHEST)
        return (1..times).flatMap { table.getRandomItems(params) }
    }
}
