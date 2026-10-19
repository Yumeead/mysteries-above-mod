package me.vangoo.mysteries.gametest

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
        }
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
