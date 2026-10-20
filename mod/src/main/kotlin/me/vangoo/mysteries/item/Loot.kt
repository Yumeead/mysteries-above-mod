package me.vangoo.mysteries.item

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import me.vangoo.domain.creatures.LootReach
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition

/**
 * Where ingredients are found outside creatures — the plugin's `global_loot.yml`, with its weights,
 * plus three things the plugin's single table could not do.
 *
 * | layer | what decides | how |
 * |---|---|---|
 * | **place** | what is found here at all | each chest leans to the pathways that belong there: a shipwreck to the Tyrant, a desert temple to the Sun |
 * | **Convergence** | what comes to *you* | the opener's own pathway weighs three times as much ([Convergence]) |
 * | **reach** | how deep | only Sequences one above the opener to two below ([WithinReach], `core`'s [LootReach]) |
 * | **luck** | how rare | Sequence 6–5 entries carry vanilla `quality`: the Luck attribute raises their weight |
 *
 * The pool is built from [Ingredients.catalogue] — the plugin's weights were a function of the
 * Sequence and nothing else, so the catalogue already holds the whole table. Chests get it as a pool
 * in the table itself, so `/loot` and other mods see it; archaeology cannot (`BrushableBlockEntity`
 * keeps the first stack), so there the find is *replaced*.
 */
object Loot {

    /**
     * What a place holds: the chance to hold anything, whether it reaches Sequence 6–5, and how much
     * more a pathway weighs here than elsewhere.
     */
    private class Place(val chance: Float, val deep: Boolean, vararg leaning: Pair<String, Int>) {
        val leaning = leaning.toMap()
    }

    private val SEA = Place(0.03f, false, "Tyrant" to 4, "Fool" to 2)
    private val DESERT = Place(0.03f, false, "Sun" to 4, "Justiciar" to 2)
    private val UNDERGROUND = Place(0.03f, false, "Death" to 2, "Error" to 2)
    private val RUINS = Place(0.20f, false, "WhiteTower" to 3, "Visionary" to 2)

    /**
     * Chest tables by a part of their path. Chances are the plugin's; the leanings are the mod's.
     * The plugin matched `ocean_ruin_*` among chests, but vanilla's ocean ruin chests are
     * `underwater_ruin_*` — that rule never fired.
     */
    private val CHESTS: List<Pair<String, Place>> = listOf(
        "shipwreck" to SEA,
        "underwater_ruin" to SEA,
        "buried_treasure" to Place(0.05f, false, "Tyrant" to 4, "Fool" to 2),
        "desert_pyramid" to DESERT,
        "jungle_temple" to Place(0.03f, false, "Error" to 3, "Visionary" to 2),
        "abandoned_mineshaft" to UNDERGROUND,
        "simple_dungeon" to UNDERGROUND,
        "ruined_portal" to Place(0.06f, false, "Door" to 4),
        "trial_chambers/supply" to Place(0.03f, false),
        "trial_chambers/corridor" to Place(0.04f, true),
        "woodland_mansion" to Place(0.07f, true, "Error" to 3, "Justiciar" to 2, "Visionary" to 2),
        "pillager_outpost" to Place(0.06f, true, "Justiciar" to 3, "Error" to 2),
        "ancient_city" to Place(0.09f, true, "Death" to 4, "Visionary" to 2),
        "bastion" to Place(0.07f, true, "Error" to 2, "Sun" to 2),
        "nether_bridge" to Place(0.02f, true, "Sun" to 3, "Death" to 2),
        "end_city" to Place(0.04f, true, "Door" to 4, "Fool" to 2),
        "stronghold" to Place(0.04f, true, "Door" to 3, "WhiteTower" to 3),
    )

    /** The mod's own structures stay the most generous chests, as in the plugin, and lean nowhere. */
    private val OWN_STRUCTURE = Place(0.10f, true)

    /** Archaeology replaces the brushed find with the chance given; the plugin's chances. */
    private val ARCHAEOLOGY: Map<String, Place> = mapOf(
        "archaeology/desert_pyramid" to Place(0.15f, false, "Sun" to 4, "Justiciar" to 2),
        "archaeology/desert_well" to Place(0.20f, false, "Sun" to 4, "Justiciar" to 2),
        "archaeology/ocean_ruin_cold" to Place(0.15f, false, "Tyrant" to 4, "Fool" to 2),
        "archaeology/ocean_ruin_warm" to Place(0.15f, false, "Tyrant" to 4, "Fool" to 2),
        "archaeology/trail_ruins_common" to RUINS,
        "archaeology/trail_ruins_rare" to Place(0.25f, false, "WhiteTower" to 3, "Visionary" to 2),
    )

    /** The plugin's weights by Sequence; 6–5 are the rare tier, found only in deep places. */
    private val INGREDIENT_WEIGHT = mapOf(9 to 60, 8 to 40, 7 to 25, 6 to 12, 5 to 5)
    private val POTION_WEIGHT = mapOf(9 to 10, 8 to 6, 7 to 3)
    private const val RARE_FROM = 6

    /** Weight added per point of Luck: a third more for Sequence 6, three-fifths more for 5. */
    private val QUALITY = mapOf(6 to 4, 5 to 3)

    /** The opener's own pathway weighs this many times its share on top. */
    private const val CONVERGENCE = 2

    fun install() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, modId("convergence"), Convergence.CODEC)
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, modId("within_reach"), WithinReach.CODEC)

        LootTableEvents.MODIFY.register { key, builder, _, _ ->
            val place = chestPlace(key.identifier()) ?: return@register
            builder.withPool(pool(place).`when`(LootItemRandomChanceCondition.randomChance(place.chance)))
        }
        LootTableEvents.MODIFY_DROPS.register { table, context, drops ->
            val path = table.unwrapKey().map { it.identifier().path }.orElse("")
            val place = ARCHAEOLOGY[path]
            if (place != null && context.random.nextFloat() < place.chance) {
                drops.clear()
                archaeology.getValue(path).getRandomItems(context) { drops += it }
            }
            finishPotions(drops)
        }
    }

    private val archaeology: Map<String, LootTable> by lazy {
        ARCHAEOLOGY.mapValues { LootTable.lootTable().withPool(pool(it.value)).build() }
    }

    private fun chestPlace(id: net.minecraft.resources.Identifier): Place? {
        // The mod's structure chests sit at the root of its loot tables; creature tables do not.
        if (id.namespace == MOD_ID) return if ('/' in id.path) null else OWN_STRUCTURE
        if (!id.path.startsWith("chests/")) return null
        return CHESTS.firstOrNull { it.first in id.path }?.second
    }

    private fun pool(place: Place): LootPool.Builder {
        val pool = LootPool.lootPool()
        for (entry in Ingredients.catalogue) {
            if (entry.sequence < RARE_FROM && !place.deep) continue
            offer(pool, place, entry.pathway, entry.sequence, INGREDIENT_WEIGHT.getValue(entry.sequence)) {
                LootItem.lootTableItem(entry.item)
            }
        }
        for (pathway in Ingredients.catalogue.map { it.pathway }.distinct()) {
            for ((sequence, weight) in POTION_WEIGHT) {
                if (sequence < 8 && !place.deep) continue
                offer(pool, place, pathway, sequence, weight) {
                    LootItem.lootTableItem(ModItems.BEYONDER_POTION)
                        .apply(SetComponentsFunction.setComponent(ModComponents.POTION_BREW, PotionBrew(pathway, sequence)))
                }
            }
        }
        return pool
    }

    /** One thing twice: its share of the place, and the Convergence share that only its own pathway sees. */
    private fun offer(
        pool: LootPool.Builder, place: Place, pathway: String, sequence: Int, weight: Int,
        item: () -> LootPoolSingletonContainer.Builder<*>,
    ) {
        val share = place.leaning[pathway] ?: 1
        val quality = QUALITY[sequence] ?: 0
        pool.add(item().setWeight(weight * share).setQuality(quality * share).`when` { WithinReach(sequence) })
        pool.add(
            item().setWeight(weight * share * CONVERGENCE).setQuality(quality * share * CONVERGENCE)
                .`when` { WithinReach(sequence) }.`when` { Convergence(pathway) },
        )
    }

    /**
     * A loot table names a potion by its brew alone — `set_components` with `potion_brew` — and the
     * bottle is finished here: colour, name and lore come from the same [ModItems.potionOf] the
     * command uses. Any datapack's table gets this for free.
     */
    private fun finishPotions(drops: MutableList<ItemStack>) {
        drops.replaceAll { stack ->
            val brew = stack.get(ModComponents.POTION_BREW)
            if (brew == null || stack.has(DataComponents.POTION_CONTENTS)) return@replaceAll stack
            val pathway = brew.resolvePathway() ?: return@replaceAll stack
            ModItems.potionOf(pathway, brew.asSequence()).also { it.count = stack.count }
        }
    }

    /** The Beyonder Sequence of whoever the loot is rolled for; `null` — nobody (a hopper, `/loot`). */
    private fun openerSequence(context: LootContext): Int? {
        val player = context.getOptionalParameter(LootContextParams.THIS_ENTITY) as? Player ?: return null
        return AbilityServices.beyonders.of(player.uuid)?.sequence?.level ?: LootReach.NOT_A_BEYONDER
    }

    /**
     * The Law of Convergence: what belongs to a pathway gathers around its Beyonders. Passes when the
     * one the loot is rolled for walks [pathway]. Usable from any datapack as `mysteries_above:convergence`.
     */
    class Convergence(val pathway: String) : LootItemCondition {
        override fun codec(): MapCodec<Convergence> = CODEC

        override fun test(context: LootContext): Boolean {
            val player = context.getOptionalParameter(LootContextParams.THIS_ENTITY) as? Player ?: return false
            return AbilityServices.beyonders.of(player.uuid)?.pathway?.name == pathway
        }

        companion object {
            val CODEC: MapCodec<Convergence> = Codec.STRING.fieldOf("pathway").xmap(::Convergence, Convergence::pathway)
        }
    }

    /**
     * Whether a [sequence] is within the opener's reach ([LootReach]). Nobody to reach — a hopper,
     * `/loot` — sees everything. Usable from any datapack as `mysteries_above:within_reach`.
     */
    class WithinReach(val sequence: Int) : LootItemCondition {
        override fun codec(): MapCodec<WithinReach> = CODEC

        override fun test(context: LootContext): Boolean {
            val opener = openerSequence(context) ?: return true
            return LootReach.allows(opener, sequence)
        }

        companion object {
            val CODEC: MapCodec<WithinReach> = Codec.intRange(0, 9).fieldOf("sequence").xmap(::WithinReach, WithinReach::sequence)
        }
    }
}
