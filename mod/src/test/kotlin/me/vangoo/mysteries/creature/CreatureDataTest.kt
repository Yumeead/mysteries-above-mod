package me.vangoo.mysteries.creature

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.pathways.ModPathways
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The mod's own creature data, read with the very codecs the datapack loader uses.
 *
 * The game would refuse a broken file at world load; this refuses it at build time, and also does
 * what the loader cannot — follows every reference between the three registries, which are plain
 * keys resolved only when a creature uses them. The plugin pinned the same thing with
 * `MythicPackKitReferenceTest`.
 */
internal class CreatureDataTest {

    companion object {
        private lateinit var skills: Map<String, CreatureSkill>
        private lateinit var kits: Map<String, CreatureKit>
        private lateinit var creatures: Map<String, CreatureSpec>

        @JvmStatic
        @BeforeAll
        fun load() {
            val ops = RegistryOps.create(JsonOps.INSTANCE, GameRegistries.access())
            fun <T> read(kind: String, codec: Codec<T>): Map<String, T> {
                val root = Paths.get(CreatureDataTest::class.java.getResource("/data/mysteries_above/mysteries_above/$kind")!!.toURI())
                return Files.walk(root).use { paths ->
                    paths.filter { it.toString().endsWith(".json") }.toList().associate { file ->
                        val id = "mysteries_above:" + root.relativize(file).toString().replace('\\', '/').removeSuffix(".json")
                        val json = Files.newBufferedReader(file).use(JsonParser::parseReader)
                        standInForModBodies(id, json)
                        id to codec.parse(ops, json).getOrThrow { error -> AssertionError("$id: $error") }
                    }
                }
            }
            skills = read("creature_skill", CreatureSkill.CODEC)
            kits = read("creature_kit", CreatureKit.CODEC)
            creatures = read("creature", CreatureSpec.CODEC)
        }

        /**
         * The mod's own entity types are not in a unit test's registries — `Bootstrap` freezes them
         * before the mod could add any. A body the mod owns is checked by name here and read as a
         * vanilla stand-in, so the rest of the file still meets the real codec.
         */
        private fun standInForModBodies(id: String, json: com.google.gson.JsonElement) {
            val body = json.asJsonObject.getAsJsonObject("body") ?: return
            val entity = body.get("entity").asString
            if (!entity.startsWith("mysteries_above:")) return
            assertTrue(entity in MOD_BODIES, "$id: невідоме тіло мода $entity")
            body.addProperty("entity", "minecraft:villager")
        }

        private val MOD_BODIES = setOf("mysteries_above:human")

        private fun ResourceKey<*>.id(): String = identifier().toString()

        private fun mechanics(list: List<Mechanic>): List<Mechanic> = list.flatMap { mechanic ->
            listOf(mechanic) + when (mechanic) {
                is Projectile -> mechanics(mechanic.hit)
                is Chance -> mechanics(mechanic.then)
                else -> emptyList()
            }
        }
    }

    @Test
    fun theWholePortArrived() {
        // 63 creatures of the plugin's pack plus its puppet, and the town, the wild Beyonders and a boss of the mod's own.
        assertTrue(creatures.size >= 64 + 6, "істот: ${creatures.size}")
        assertTrue(skills.size >= 63, "прийомів: ${skills.size}")
    }

    @Test
    fun everyKitNamesSkillsThatExist() {
        kits.forEach { (id, kit) ->
            (kit.skills.map { it.skill } + kit.reactions.map { it.skill }).forEach { skill ->
                assertTrue(skill.id() in skills, "$id → невідомий прийом ${skill.id()}")
            }
        }
    }

    @Test
    fun everyCreatureNamesAKitAndPhasesThatExist() {
        creatures.forEach { (id, spec) ->
            spec.kit?.let { assertTrue(it.id() in kits, "$id → невідомий кіт ${it.id()}") }
            spec.boss?.phases?.forEach { phase ->
                phase.kit?.let { assertTrue(it.id() in kits, "$id → невідомий кіт акту ${it.id()}") }
                phase.enter.forEach { assertTrue(it.id() in skills, "$id → невідомий прийом акту ${it.id()}") }
            }
        }
    }

    @Test
    fun everySummonCallsACreatureThatExists() {
        skills.forEach { (id, skill) ->
            mechanics(skill.self + skill.target).filterIsInstance<Summon>().forEach {
                assertTrue(it.creature.id() in creatures, "$id кличе невідому ${it.creature.id()}")
            }
        }
    }

    /**
     * A wild Beyonder's kit is derived from their pathway and Sequence, so every pathway a persona
     * may roll needs a kit for every Sequence in its range — or that Beyonder fights bare-handed.
     */
    @Test
    fun everyWildBeyonderHasAKitForWhatTheyMayRoll() {
        creatures.values.mapNotNull { it.persona }.forEach { persona ->
            persona.pathways.forEach { pathway ->
                assertTrue(ModPathways.byName(pathway) != null, "невідомий шлях $pathway")
                for (sequence in persona.sequenceMax..persona.sequenceMin) {
                    assertTrue("mysteries_above:${pathway.lowercase()}_$sequence" in kits, "немає кіта $pathway $sequence")
                }
            }
        }
    }

    @Test
    fun creaturePathwaysAreRealPathways() {
        creatures.forEach { (id, spec) ->
            spec.pathway?.let { assertTrue(ModPathways.byName(it) != null, "$id: невідомий шлях $it") }
        }
    }

    @Test
    fun theSpiritWorldTagNamesItsEightBeings() {
        val tag = CreatureDataTest::class.java.getResourceAsStream("/data/mysteries_above/tags/mysteries_above/creature/spirit_world.json")!!
            .reader().use(JsonParser::parseReader).asJsonObject.getAsJsonArray("values").map { it.asString }
        assertEquals(8, tag.size)
        tag.forEach { assertTrue(it in creatures, "тег Світу Духів: невідома $it") }
    }
}
