package me.vangoo.mysteries.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * What the creatures look like in a real client: the human renderer (skins by id, both arm widths,
 * held weapons, armour), name plates and a speech bubble. A unit test cannot see any of it, and
 * `runClient` to the menu does not reach a world.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
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
            server.runCommand("fill ~-6 ~-1 ~-2 ~6 ~-1 ~12 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 10")
            // A row of people a few steps ahead, facing the camera: the face is the entity's id.
            for ((i, id) in listOf("citizen", "citizen", "guard", "wild_beyonder", "citizen", "drowned_despot").withIndex()) {
                server.runCommand("execute positioned ~${i * 2 - 5} ~ ~7 run mysteries creature spawn mysteries_above:$id")
            }
            server.runCommand("execute as @e[tag=mysteries_above.creature] run tp @s ~ ~ ~ 180 0")
            context.waitTicks(60)
            context.takeScreenshot("creatures-town")

            server.runCommand("kill @e[tag=mysteries_above.creature]")
            server.runCommand("execute positioned ~-3 ~ ~6 run mysteries creature spawn mysteries_above:tyrant_siren_5")
            server.runCommand("execute positioned ~0 ~ ~6 run mysteries creature spawn mysteries_above:fool_thousand_faced_hunter_6")
            server.runCommand("execute positioned ~3 ~ ~6 run mysteries creature spawn mysteries_above:death_knight")
            context.waitTicks(60)
            context.takeScreenshot("creatures-wild")
        }
    }
}
