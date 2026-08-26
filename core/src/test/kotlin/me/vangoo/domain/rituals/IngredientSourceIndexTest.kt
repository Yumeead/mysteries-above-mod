package me.vangoo.domain.rituals

import me.vangoo.domain.creatures.CreatureDefinition
import me.vangoo.domain.creatures.CreatureTier
import me.vangoo.domain.creatures.SpawnRule
import me.vangoo.domain.forage.ForageEntry
import me.vangoo.domain.valueobjects.LootItem
import me.vangoo.domain.valueobjects.LootTableData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class IngredientSourceIndexTest {

    private fun creature(id: String, ingredient: String, vararg biomes: String) = CreatureDefinition(
        id, "ZOMBIE", CreatureTier.COMMON,
        LootTableData(listOf(LootItem(ingredient, 50, 1, 1)), 0, 1),
        SpawnRule(biomes.toList(), emptyList(), 0.005, 0.0),
        "Error", 9,
    )

    @Test
    fun creatureLootMapsToItsSpawnBiomes() {
        val index = IngredientSourceIndex(
            listOf(creature("c1", "rat_heart", "SWAMP", "DARK_FOREST")),
            emptyMap(),
        )

        val source = index.sourceOf("rat_heart")!!
        assertEquals(IngredientSourceIndex.Kind.CREATURE, source.kind)
        assertEquals(listOf("SWAMP", "DARK_FOREST"), source.biomes)
    }

    @Test
    fun biomesOfSeveralCreaturesDroppingTheSameIngredientMerge() {
        val index = IngredientSourceIndex(
            listOf(creature("c1", "rat_heart", "SWAMP"), creature("c2", "rat_heart", "SWAMP", "JUNGLE")),
            emptyMap(),
        )

        assertEquals(listOf("SWAMP", "JUNGLE"), index.sourceOf("rat_heart")!!.biomes)
    }

    @Test
    fun forageIngredientIsIndexedByTheBiomesThatOfferIt() {
        val index = IngredientSourceIndex(
            emptyList(),
            mapOf("PLAINS" to listOf(ForageEntry("elf_flower_petals", 50))),
        )

        val source = index.sourceOf("elf_flower_petals")!!
        assertEquals(IngredientSourceIndex.Kind.FORAGE, source.kind)
        assertEquals(listOf("PLAINS"), source.biomes)
    }

    @Test
    fun creatureWinsOverForageForTheSameIngredient() {
        val index = IngredientSourceIndex(
            listOf(creature("c1", "shared", "SWAMP")),
            mapOf("PLAINS" to listOf(ForageEntry("shared", 50))),
        )

        val source = index.sourceOf("shared")!!
        assertEquals(IngredientSourceIndex.Kind.CREATURE, source.kind)
        assertEquals(listOf("SWAMP"), source.biomes)
    }

    @Test
    fun forageIngredientMergesBiomesAcrossTables() {
        val index = IngredientSourceIndex(
            emptyList(),
            linkedMapOf(
                "PLAINS" to listOf(ForageEntry("petals", 50)),
                "FOREST" to listOf(ForageEntry("petals", 30)),
            ),
        )

        assertEquals(listOf("PLAINS", "FOREST"), index.sourceOf("petals")!!.biomes)
    }

    @Test
    fun chestOnlyIngredientHasNoSource() {
        assertNull(IngredientSourceIndex(emptyList(), emptyMap()).sourceOf("master_recipe_book"))
    }
}
