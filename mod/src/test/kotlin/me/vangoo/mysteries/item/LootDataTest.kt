package me.vangoo.mysteries.item

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The plugin's `GlobalLootCoverageTest`, against the mod's loot tables: an ingredient exists in the
 * game exactly when it can be found. A table naming an item that is not there is skipped silently by
 * the game; an ingredient no table names is a potion nobody can brew — found weeks into play.
 *
 * The mod's items are not in a unit test's registries, so an item counts as existing when it has an
 * item model definition (`assets/…/items/<id>.json`) — every registered item needs one to be drawn.
 */
internal class LootDataTest {

    private fun dir(path: String): Path = Paths.get(LootDataTest::class.java.getResource(path)!!.toURI())

    private fun names(dir: Path): Set<String> =
        Files.list(dir).use { files -> files.map { it.fileName.toString().removeSuffix(".json") }.toList().toSet() }

    private val items = names(dir("/assets/mysteries_above/items"))
    private val creatures = names(dir("/data/mysteries_above/mysteries_above/creature"))
    private val tables = dir("/data/mysteries_above/loot_table")

    /** Every `"name"` of every item entry in a table — the only place an item id sits in these files. */
    private fun itemsIn(file: Path): List<String> {
        val found = mutableListOf<String>()
        fun walk(json: JsonElement) {
            when {
                json.isJsonArray -> json.asJsonArray.forEach(::walk)
                json.isJsonObject -> json.asJsonObject.let { obj ->
                    if (obj.get("type")?.asString == "minecraft:item") found += obj.get("name").asString
                    obj.entrySet().forEach { walk(it.value) }
                }
            }
        }
        walk(Files.newBufferedReader(file).use(JsonParser::parseReader))
        return found
    }

    private fun named(sub: String): Map<Path, List<String>> =
        Files.list(tables.resolve(sub)).use { files -> files.toList().associateWith(::itemsIn) }

    @Test
    fun everyLootItemIsARegisteredItem() {
        val unknown = (named("global") + named("creature")).flatMap { (file, ids) ->
            ids.filter { it.startsWith("mysteries_above:") && it.removePrefix("mysteries_above:") !in items }
                .map { "${file.fileName} → $it" }
        }
        assertTrue(unknown.isEmpty(), "Таблиця луту називає предмет, якого мод не реєструє: $unknown")
    }

    @Test
    fun everyCreatureTableBelongsToACreature() {
        val orphans = names(tables.resolve("creature")) - creatures
        assertTrue(orphans.isEmpty(), "Таблиця луту без істоти — її ніхто не скине: $orphans")
    }

    @Test
    fun everyIngredientCanBeFound() {
        val looted = (named("global") + named("creature")).values.flatten().map { it.removePrefix("mysteries_above:") }.toSet()
        val missing = items - setOf("beyonder_potion", "characteristic") - looted
        assertTrue(missing.isEmpty(), "Інгредієнт не трапляється ні в скринях, ні в істотах — зілля не звариш: ${missing.sorted()}")
    }
}
