package me.vangoo.mysteries.gametest

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.item.Ingredients
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.pathways.ModPathways
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

/**
 * Chest loot as the game rolls it: what `Loot` adds, for whom, and what it finishes. Each roll is a
 * chest rolled many times over, so the claims are about shares and the margins are several standard
 * deviations wide.
 */
class LootGameTests {

    private val catalogue = Ingredients.catalogue.associateBy { it.item }

    @GameTest(maxTicks = 1)
    fun aDungeonChestSometimesHoldsAnIngredient(helper: GameTestHelper) {
        // The pool's chance is 3 %: two thousand chests miss it with odds of about 10⁻²⁶.
        val found = roll(helper, BuiltInLootTables.SIMPLE_DUNGEON, 2000, opener = null).filter { it.item in catalogue }
        if (found.isEmpty()) helper.fail("жодного інгредієнта в двох тисячах скринь данжу")
        helper.succeed()
    }

    @GameTest(maxTicks = 1)
    fun aPotionFromLootIsAFinishedBottle(helper: GameTestHelper) {
        val potions = roll(helper, chest("ancient_city"), 6000, opener = null).filter { it.`is`(ModItems.BEYONDER_POTION) }
        if (potions.isEmpty()) helper.fail("жодного зілля в шести тисячах скринь давнього міста")
        val unfinished = potions.filter { !it.has(DataComponents.POTION_CONTENTS) || it.get(ModComponents.POTION_BREW) == null }
        if (unfinished.isNotEmpty()) helper.fail("зілля без кольору чи складу: ${unfinished.first().components}")
        helper.succeed()
    }

    @GameTest(maxTicks = 1)
    fun somebodyWhoIsNotABeyonderFindsSequenceNineAlone(helper: GameTestHelper) {
        val found = ingredients(roll(helper, chest("ancient_city"), 3000, helper.makeMockServerPlayerInLevel()))
        if (found.isEmpty()) helper.fail("жодного інгредієнта")
        val deeper = found.filter { it.sequence != 9 }
        if (deeper.isNotEmpty()) helper.fail("не-Потойбічний знайшов Посл. ${deeper.first().sequence}: ${deeper.first().id}")
        helper.succeed()
    }

    @GameTest(maxTicks = 1)
    fun aBeyondersOwnPathwayGathersAroundThem(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        AbilityServices.beyonders.register(Beyonder(player.uuid, Sequence(9), ModPathways.byName("Tyrant")!!))
        // A place that leans nowhere: without Convergence a pathway's share is about 1/9.
        val found = ingredients(roll(helper, chest("trial_chambers/corridor"), 30000, player))
        val share = found.count { it.pathway == "Tyrant" }.toDouble() / found.size
        if (found.size < 300 || share < 0.2) helper.fail("частка Тирана ${"%.2f".format(share)} із ${found.size} — Конвергенція не тягне")
        helper.succeed()
    }

    @GameTest(maxTicks = 1)
    fun aShipwreckLeansToTheSea(helper: GameTestHelper) {
        val found = ingredients(roll(helper, chest("shipwreck_supply"), 20000, opener = null))
        val share = found.count { it.pathway == "Tyrant" }.toDouble() / found.size
        if (found.size < 300 || share < 0.25) helper.fail("частка Тирана в корабельній скрині ${"%.2f".format(share)} із ${found.size}")
        helper.succeed()
    }

    private fun ingredients(stacks: List<ItemStack>): List<Ingredients.Entry> = stacks.mapNotNull { catalogue[it.item] }

    private fun chest(path: String): ResourceKey<LootTable> =
        ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/$path"))

    private fun roll(helper: GameTestHelper, key: ResourceKey<LootTable>, times: Int, opener: ServerPlayer?): List<ItemStack> {
        val table = helper.level.server.reloadableRegistries().getLootTable(key)
        val params = LootParams.Builder(helper.level)
            .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
            .withOptionalParameter(LootContextParams.THIS_ENTITY, opener)
            .create(LootContextParamSets.CHEST)
        return (1..times).flatMap { table.getRandomItems(params) }
    }
}
