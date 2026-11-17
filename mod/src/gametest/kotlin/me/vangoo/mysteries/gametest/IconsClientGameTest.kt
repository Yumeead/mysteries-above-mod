package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.hud.HudSettings
import me.vangoo.domain.hud.InterfaceStyle
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.AbilityGroup
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.BookAbilityIcons
import me.vangoo.mysteries.client.render.WheelSkin
import me.vangoo.mysteries.client.screen.AbilityBookScreen
import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

/** Painted powers at the real wheel and book sizes, including permanent passives and pathway variants. */
@Suppress("UnstableApiUsage")
class IconsClientGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            for (pathway in listOf("WhiteTower", "Tyrant", "Error", "Death", "Justiciar", "Sun", "Fool", "Door")) {
                server.runCommand("mysteries become $pathway 5 @a")
                context.waitTicks(10)
                val samples = context.computeOnClient<List<String>, Throwable> {
                    ClientAbilityState.entries.map { it.identity }.filter { BookAbilityIcons.texture(it, pathway) != null }
                }
                check(samples.isNotEmpty()) { "No painted samples in $pathway roster" }
                if (pathway == "WhiteTower") check(samples.size == 10) { "WhiteTower artwork is incomplete: ${samples.size}/10" }
                if (pathway == "Tyrant") check(samples.size == 31) { "Tyrant artwork is incomplete: ${samples.size}/31" }
                if (pathway == "Error") check(samples.size == 19) { "Error artwork is incomplete: ${samples.size}/19" }
                if (pathway == "Death") check(samples.size == 20) { "Death artwork is incomplete: ${samples.size}/20" }
                if (pathway == "Justiciar") check(samples.size == 16) { "Justiciar artwork is incomplete: ${samples.size}/16" }
                if (pathway == "Sun") check(samples.size == 18) { "Sun artwork is incomplete: ${samples.size}/18" }
                if (pathway == "Fool") check(samples.size == 18) { "Fool artwork is incomplete: ${samples.size}/18" }
                if (pathway == "Door") check(samples.size == 14) { "Door artwork is incomplete: ${samples.size}/14" }
                for (identity in samples) {
                    server.runOnServer<Throwable> { srv ->
                        check(AbilityRequests.select(srv.playerList.players.first(), AbilityIdentity.of(identity)))
                    }
                    context.waitTicks(3)
                    if (context.computeOnClient<Boolean, Throwable> { ModKeys.visible().any { it.identity == identity } }) {
                        context.input.holdKey(GLFW.GLFW_KEY_R)
                        context.waitTicks(10)
                        check(context.computeOnClient<Boolean, Throwable> { it.gui.screen() is AbilityWheelScreen })
                        context.takeScreenshot("icons-wheel-$identity")
                        context.input.releaseKey(GLFW.GLFW_KEY_R)
                        context.waitTicks(2)
                    }
                    context.runOnClient<Throwable> {
                        it.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.LIBRARY))
                    }
                    context.waitTicks(5)
                    context.takeScreenshot("icons-book-$identity")
                    context.runOnClient<Throwable> { it.gui.setScreen(null) }
                    context.waitTicks(2)
                    if (pathway == "WhiteTower" && identity == samples.first()) reviewLayouts(context, identity)
                }
            }
        }
    }

    private fun reviewLayouts(context: ClientGameTestContext, identity: String) {
        val settings = context.computeOnClient<HudSettings, Throwable> { ClientOptions.hudSettings }
        val hud = context.computeOnClient<HudMode, Throwable> { ClientOptions.hud }
        val skin = context.computeOnClient<WheelSkin, Throwable> { ClientOptions.wheelSkin }
        val group = context.computeOnClient<Int, Throwable> { ClientOptions.activeGroup }
        val preview = AbilityGroup("Icon preview", mutableListOf(identity))
        try {
            for (style in InterfaceStyle.entries) {
                context.runOnClient<Throwable> {
                    ClientOptions.hud = HudMode.ALWAYS
                    ClientOptions.hudSettings = settings.copy(interfaceStyle = style)
                }
                context.waitTicks(8)
                context.takeScreenshot("icons-ui-hud-${style.name.lowercase()}")
            }
            for (look in WheelSkin.entries) {
                context.runOnClient<Throwable> { ClientOptions.wheelSkin = look }
                context.input.holdKey(GLFW.GLFW_KEY_R)
                context.waitTicks(10)
                context.takeScreenshot("icons-ui-wheel-${look.name.lowercase()}")
                context.input.releaseKey(GLFW.GLFW_KEY_R)
                context.waitTicks(2)
            }
            context.runOnClient<Throwable> { it.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.KEYS)) }
            context.waitTicks(5)
            context.takeScreenshot("icons-ui-book-keys")
            context.runOnClient<Throwable> { client ->
                ClientOptions.editGroups {
                    ClientOptions.groups.add(preview)
                    ClientOptions.activeGroup = ClientOptions.groups.lastIndex
                }
                client.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.GROUPS))
            }
            context.waitTicks(5)
            context.takeScreenshot("icons-ui-book-groups")
        } finally {
            context.runOnClient<Throwable> { client ->
                client.gui.setScreen(null)
                ClientOptions.hudSettings = settings
                ClientOptions.hud = hud
                ClientOptions.wheelSkin = skin
                ClientOptions.editGroups {
                    ClientOptions.groups.remove(preview)
                    ClientOptions.activeGroup = group
                }
            }
        }
    }
}
