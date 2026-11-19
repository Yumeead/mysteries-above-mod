package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.ability.Restraints
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.phys.Vec3

/**
 * The client half of full restraint: a bound player presses forward and jump and goes nowhere — no step, no hop, no
 * rubber band; let go, the same keys walk them off. Run with `-Ponly=restraint`.
 */
@Suppress("UnstableApiUsage")
class RestraintClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamemode survival @a")
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            context.waitTicks(20)

            server.runOnServer<Throwable> { Restraints.hold(it.playerList.players.first(), 80) }
            context.waitTicks(5)
            val bound = position(context)
            val up = context.computeOnClient<net.minecraft.client.KeyMapping, Throwable> { it.options.keyUp }
            val jump = context.computeOnClient<net.minecraft.client.KeyMapping, Throwable> { it.options.keyJump }
            context.input.holdKey(up)
            context.input.holdKey(jump)
            context.waitTicks(30)
            context.takeScreenshot("restraint-held")
            val held = position(context)
            val peak = held.y - bound.y
            context.input.releaseKey(up)
            context.input.releaseKey(jump)
            check(held.subtract(bound).horizontalDistance() < 0.05) { "Скутий гравець пройшов ${held.subtract(bound).horizontalDistance()} бл." }
            check(peak < 0.05) { "Скутий гравець підстрибнув на $peak бл." }

            // Let go: the same key walks.
            context.waitTicks(60)
            val free = position(context)
            context.input.holdKeyFor(up, 20)
            context.waitTicks(2)
            val walked = position(context).subtract(free).horizontalDistance()
            check(walked > 1.0) { "Відпущений гравець не йде: $walked бл." }
        }
    }

    private fun position(context: ClientGameTestContext): Vec3 =
        context.computeOnClient<Vec3, Throwable> { it.player!!.position() }
}
