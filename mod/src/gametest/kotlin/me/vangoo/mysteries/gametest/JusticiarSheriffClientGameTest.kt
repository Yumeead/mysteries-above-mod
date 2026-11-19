package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.render.WorldEffects
import me.vangoo.mysteries.client.screen.AbilityBookScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType

/**
 * The Sheriff's two senses in a real client:
 * - **Danger Intuition**: a zombie behind takes the Sheriff as its target, and the arrow by the crosshair turns to it;
 * - **Recognition**: corners closing round a villager as the Sheriff holds it in the eye, snapping shut; its footprints
 *   as it is walked away, also behind a wall; a zombie remembered and killed; the book open on «Справи».
 * Effects are held while each screenshot is taken. Run with `-Ponly=sheriff`.
 */
@Suppress("UnstableApiUsage")
class JusticiarSheriffClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("weather clear")
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("gamerule doMobSpawning false")
            server.runCommand("time set noon")
            server.runCommand("difficulty normal")
            server.runCommand("mysteries become Justiciar 8 @a")
            server.runCommand("effect give @a resistance 600 4 true")
            context.waitTicks(10)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            // Danger Intuition: a zombie behind the Sheriff takes them as its target (it takes a moment to choose).
            server.runCommand("gamemode survival @a")
            server.runCommand("tp @a 0.5 -60 0.5 180 0")
            server.runCommand("summon zombie 0.5 -60 5.5")
            context.waitTicks(40)
            screenshot(context, "danger")
            server.runCommand("kill @e[type=zombie]")
            server.runCommand("gamemode creative @a")
            context.waitTicks(10)

            // Recognition: on, and a villager held in the eye.
            toggle(server, "justiciar_recognition")
            server.runCommand("summon villager 0.5 -60 -5.5 {NoAI:1b}")
            server.runCommand("tp @a 0.5 -60 0.5 180 12")
            context.waitTicks(4)
            screenshot(context, "gaze-early")
            context.waitTicks(16)
            screenshot(context, "gaze-late")
            context.waitTicks(14)
            screenshot(context, "known")

            // It walks away east, a stride every few ticks; the Sheriff turns aside and watches its trail.
            server.runCommand("tp @a 0.5 -60 0.5 -120 25")
            for (step in 0 until 18) {
                server.runCommand("execute as @e[type=villager] at @s run tp @s ~0.5 ~ ~")
                context.waitTicks(3)
            }
            context.waitTicks(5)
            screenshot(context, "tracks")
            server.runCommand("fill 3 -60 -3 3 -57 3 minecraft:stone_bricks")
            context.waitTicks(5)
            screenshot(context, "tracks-wall")

            // A zombie remembered, then killed: its file says so.
            server.runCommand("fill 3 -60 -3 3 -57 3 minecraft:air")
            server.runCommand("summon zombie -5.5 -60 0.5 {NoAI:1b}")
            server.runCommand("tp @a 0.5 -60 0.5 90 12")
            context.waitTicks(45)
            server.runCommand("kill @e[type=zombie]")
            context.waitTicks(30)

            context.runOnClient<Throwable> { it.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.CASES)) }
            context.waitTicks(10)
            context.takeScreenshot("sheriff-book")
        }
    }

    private fun screenshot(context: ClientGameTestContext, name: String) {
        context.runOnClient<Throwable> { WorldEffects.held = true }
        context.takeScreenshot("sheriff-$name")
        context.runOnClient<Throwable> { WorldEffects.held = false }
    }

    private fun toggle(server: TestServerContext, id: String) = server.runOnServer<Throwable> { srv ->
        val player = srv.playerList.players.first()
        AbilityServices.cooldowns.forget(player.uuid)
        AbilityRequests.cast(player, AbilityIdentity.of(id))
    }
}
