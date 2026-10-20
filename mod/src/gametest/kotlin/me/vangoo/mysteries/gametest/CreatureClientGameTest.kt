package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CreatureMovePayload
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext

/**
 * What the creatures look like in a real client: the human renderer (skins by id, both arm widths,
 * held weapons, armour), name plates, speech bubbles and a boss bar. A unit test cannot see any of
 * it, and `runClient` to the menu does not reach a world.
 *
 * Three scenes, each on a clean yard: a street (people only — a boss among them sends them
 * running, which is right, but leaves nothing to look at), a boss, and the wild. The screenshots
 * land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class CreatureClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-2 ~8 ~-1 ~14 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 10")

            scene(context, server, "creatures-street", listOf("citizen", "guard", "citizen", "wild_beyonder", "citizen", "guard"))
            scene(context, server, "creatures-boss", listOf("drowned_despot"))
            scene(context, server, "creatures-wild", listOf("tyrant_siren_5", "fool_thousand_faced_hunter_6", "death_knight"))
            models(context, server)
        }
    }

    /**
     * The plugin's four BetterModel creatures on the mod's own renderer: from the front, turned
     * three-quarters (the side and top faces), and mid-cast (an animation named by the server).
     * Minds off, or the condor circles out of frame.
     */
    private fun models(context: ClientGameTestContext, server: TestServerContext) {
        val ids = listOf("tyrant_sea_condor_6", "tyrant_siren_5", "fool_ancient_wraith_5", "fool_thousand_faced_hunter_6")
        scene(context, server, "creatures-models", ids)
        server.runCommand("execute as @e[tag=mysteries_above.creature] run data merge entity @s {NoAI:1b}")
        // Phase 14's items in the same frames: an ingredient in hand, the characteristic's own core in the other.
        server.runCommand("item replace entity @a weapon.mainhand with mysteries_above:sphinx_brain")
        server.runCommand("item replace entity @a weapon.offhand with mysteries_above:characteristic")
        server.runCommand("execute as @e[tag=mysteries_above.creature] at @s run tp @s ~ ~ ~ 135 0")
        context.waitTicks(20)
        context.takeScreenshot("creatures-models-turned")
        server.runOnServer<Throwable> { srv ->
            val player = srv.playerList.players.first()
            srv.overworld().getEntities(player, player.boundingBox.inflate(48.0)) { "mysteries_above.creature" in it.entityTags() }
                .forEach {
                    val move = when (it.type) {
                        ModEntities.SIREN -> "attack"
                        ModEntities.THOUSAND_FACED_HUNTER -> "cast_bolt"
                        else -> "cast_marionette" // the condor has no gesture and stays as it is
                    }
                    ModChannels.CREATURE_MOVE.sendTo(player, CreatureMovePayload(it.id, move))
                }
        }
        context.waitTicks(12)
        context.takeScreenshot("creatures-models-cast")
    }

    /** A row of [ids] a few steps ahead, turned to the camera, given three seconds to live. */
    private fun scene(context: ClientGameTestContext, server: TestServerContext, name: String, ids: List<String>) {
        server.runCommand("kill @e[tag=mysteries_above.creature]")
        ids.forEachIndexed { i, id ->
            val x = (i - (ids.size - 1) / 2.0) * 2.2
            server.runCommand("execute positioned ~$x ~ ~6 run mysteries creature spawn mysteries_above:$id")
        }
        server.runCommand("execute as @e[tag=mysteries_above.creature] at @s run tp @s ~ ~ ~ 180 0")
        context.waitTicks(60)
        server.runOnServer<Throwable> { srv ->
            val player = srv.playerList.players.first()
            srv.overworld().getEntities(player, player.boundingBox.inflate(48.0)) { "mysteries_above.creature" in it.entityTags() }
                .forEach { println("[$name] ${it.name.string}: ${"%.1f %.1f %.1f".format(it.x - player.x, it.y - player.y, it.z - player.z)} alive=${it.isAlive}") }
        }
        context.takeScreenshot(name)
    }
}
