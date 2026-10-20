package me.vangoo.mysteries.item

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.NestedLootTable
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition

/**
 * Where ingredients are found outside creatures: the plugin's `global_loot.yml`, now two vanilla loot
 * tables (`global/common`, `global/rare` — rare is common plus the Sequence 6–5 entries) that this
 * object slips into other tables.
 *
 * | where | how | tables |
 * |---|---|---|
 * | ordinary chests | a pool of one roll under a `random_chance` | common |
 * | dungeons, special structures, the mod's own | the same | rare |
 * | suspicious sand and gravel | the brushed item is *replaced* — a brush yields one thing | common |
 *
 * Chests get a pool rather than a listener: the addition is part of the table, so `/loot` and any
 * other mod that reads the table see it. Archaeology cannot: `BrushableBlockEntity` keeps the first
 * stack and warns about the rest.
 *
 * Not carried over: the plugin rerolled potions and recipes weaker than the opener's Sequence (twice).
 * Recipes do not exist yet; for potions the reroll would need a custom loot condition — add it with
 * brewing, when a Sequence 5 opening a Sequence 9 potion starts to matter.
 */
object Loot {

    val COMMON: ResourceKey<LootTable> = table("global/common")
    val RARE: ResourceKey<LootTable> = table("global/rare")

    /**
     * Chest tables by a part of their path, with the plugin's chances. The plugin matched
     * `ocean_ruin_*` here too, but vanilla's ocean ruin chests are `underwater_ruin_*` — that rule
     * never fired; it is aimed where it meant to aim.
     */
    private val CHESTS: List<Triple<String, Float, ResourceKey<LootTable>>> = listOf(
        Triple("shipwreck", 0.03f, COMMON),
        Triple("abandoned_mineshaft", 0.03f, COMMON),
        Triple("desert_pyramid", 0.03f, COMMON),
        Triple("jungle_temple", 0.03f, COMMON),
        Triple("buried_treasure", 0.05f, COMMON),
        Triple("underwater_ruin", 0.03f, COMMON),
        Triple("ruined_portal", 0.06f, COMMON),
        Triple("simple_dungeon", 0.03f, COMMON),
        Triple("trial_chambers/supply", 0.03f, COMMON),
        Triple("woodland_mansion", 0.07f, RARE),
        Triple("ancient_city", 0.09f, RARE),
        Triple("bastion", 0.07f, RARE),
        Triple("nether_bridge", 0.02f, RARE),
        Triple("end_city", 0.04f, RARE),
        Triple("stronghold", 0.04f, RARE),
        Triple("pillager_outpost", 0.06f, RARE),
        Triple("trial_chambers/corridor", 0.04f, RARE),
    )

    /** The mod's own structures stay the most generous chests, as in the plugin. */
    private const val OWN_STRUCTURE_CHANCE = 0.10f

    private val ARCHAEOLOGY: Map<String, Float> = mapOf(
        "archaeology/desert_pyramid" to 0.15f,
        "archaeology/desert_well" to 0.20f,
        "archaeology/ocean_ruin_cold" to 0.15f,
        "archaeology/ocean_ruin_warm" to 0.15f,
        "archaeology/trail_ruins_common" to 0.20f,
        "archaeology/trail_ruins_rare" to 0.25f,
    )

    fun install() {
        LootTableEvents.MODIFY.register { key, builder, _, _ ->
            val (chance, pool) = chestRule(key) ?: return@register
            builder.withPool(
                LootPool.lootPool()
                    .add(NestedLootTable.lootTableReference(pool))
                    .`when`(LootItemRandomChanceCondition.randomChance(chance)),
            )
        }
        LootTableEvents.MODIFY_DROPS.register { table, context, drops ->
            val path = table.unwrapKey().map { it.identifier().path }.orElse("")
            val chance = ARCHAEOLOGY[path]
            if (chance != null && context.random.nextFloat() < chance) {
                drops.clear()
                context.level.server.reloadableRegistries().getLootTable(COMMON).getRandomItems(context) { drops += it }
            }
            finishPotions(drops)
        }
    }

    private fun chestRule(key: ResourceKey<LootTable>): Pair<Float, ResourceKey<LootTable>>? {
        val id = key.identifier()
        // The mod's structure chests sit at the root of its loot tables; creature and global tables do not.
        if (id.namespace == MOD_ID) return if ('/' in id.path) null else OWN_STRUCTURE_CHANCE to RARE
        if (!id.path.startsWith("chests/")) return null
        return CHESTS.firstOrNull { it.first in id.path }?.let { it.second to it.third }
    }

    /**
     * A loot table names a potion by its brew alone — `set_components` with `potion_brew` — and the
     * bottle is finished here: colour, name and lore come from the same [ModItems.potionOf] the
     * command uses. Any datapack's table gets this for free.
     */
    private fun finishPotions(drops: MutableList<net.minecraft.world.item.ItemStack>) {
        drops.replaceAll { stack ->
            val brew = stack.get(ModComponents.POTION_BREW)
            if (brew == null || stack.has(DataComponents.POTION_CONTENTS)) return@replaceAll stack
            val pathway = brew.resolvePathway() ?: return@replaceAll stack
            ModItems.potionOf(pathway, brew.asSequence()).also { it.count = stack.count }
        }
    }

    private fun table(path: String): ResourceKey<LootTable> = ResourceKey.create(Registries.LOOT_TABLE, modId(path))
}
