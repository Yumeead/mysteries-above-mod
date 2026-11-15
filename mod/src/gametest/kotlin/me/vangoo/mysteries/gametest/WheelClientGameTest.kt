package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * The ability wheel under real input: held, it lets the player walk; held for four seconds, it pins
 * and outlives the key; a second press and release of the key closes it, and nothing is left held.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class WheelClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("fill ~-8 ~-1 ~-8 ~8 ~-1 ~24 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 5 @a")
            context.waitTicks(20)

            // A short hold: the wheel opens and closes on release, as it always did.
            context.input.holdKey(GLFW.GLFW_KEY_R)
            context.waitTicks(10)
            check(wheelOpen(context)) { "Колесо не відкрилось на утриманні R" }
            context.input.releaseKey(GLFW.GLFW_KEY_R)
            context.waitTicks(2)
            check(!wheelOpen(context)) { "Колесо не закрилось, коли R відпустили до закріплення" }

            // Hold again and walk with the wheel open.
            context.input.holdKey(GLFW.GLFW_KEY_R)
            context.waitTicks(10)
            val start = z(context)
            context.input.holdKey { it.keyUp }
            context.waitTicks(30)
            check(wheelOpen(context)) { "Колесо закрилось від ходьби" }
            check(z(context) - start > WALKED) { "З відкритим колесом гравець не йде: ${z(context) - start} бл." }
            context.takeScreenshot("wheel-walking")
            context.input.releaseKey { it.keyUp }

            // Past four seconds of holding: pinned. Letting go no longer closes it.
            context.waitTicks(PIN_WAIT)
            context.takeScreenshot("wheel-pinned")
            context.input.releaseKey(GLFW.GLFW_KEY_R)
            context.waitTicks(5)
            check(wheelOpen(context)) { "Закріплене колесо закрилось, коли R відпустили" }

            // Rested on a card, the wheel reads it out at the left of the screen.
            val card = context.computeOnClient<DoubleArray, Throwable> { client ->
                val scale = client.window.guiScale
                val (dx, dy) = WheelLayout.offset(DESCRIBED_CARD, WheelLayout.PAGE_SIZE)
                doubleArrayOf(client.window.screenWidth / 2.0 + dx * scale, client.window.screenHeight / 2.0 + dy * scale)
            }
            // The first move after a screen opens is swallowed by the mouse handler: move twice.
            context.input.setCursorPos(card[0] + 2, card[1] + 2)
            context.waitTicks(1)
            context.input.setCursorPos(card[0], card[1])
            context.waitTicks(3)
            context.takeScreenshot("wheel-described")

            // A card with modes fans them out the moment it is hovered; a click out on a row's name
            // picks the ability in that mode, and the server says so back.
            val page = context.computeOnClient<Int, Throwable> {
                val at = ModKeys.visible().indexOfFirst { it.identity == ILLUSION }
                check(at >= 0) { "Створення ілюзій немає на колесі" }
                at / WheelLayout.PAGE_SIZE
            }
            repeat(page) {
                context.input.scroll(-1.0)
                context.waitTicks(1)
            }
            val illusion = context.computeOnClient<Array<DoubleArray>, Throwable> { client ->
                val scale = client.window.guiScale
                val shown = ModKeys.visible().chunked(WheelLayout.PAGE_SIZE)[page]
                val index = shown.indexOfFirst { it.identity == ILLUSION }
                val (dx, dy) = WheelLayout.offset(index, shown.size)
                val half = client.window.guiScaledHeight / 2 - 2
                val row = WheelLayout.arc(dx, dy, shown.size, shown[index].modes.size, half, null)[ARC_ROW]
                val cx = client.window.screenWidth / 2.0
                val cy = client.window.screenHeight / 2.0
                arrayOf(
                    doubleArrayOf(cx + dx * scale, cy + dy * scale),
                    doubleArrayOf(cx + (row.x + if (row.right) 12 else -12) * scale, cy + (row.y + 5) * scale),
                )
            }
            context.input.setCursorPos(illusion[0][0], illusion[0][1])
            context.waitTicks(1)
            context.takeScreenshot("wheel-arc-opening")
            context.waitTicks(4)
            context.takeScreenshot("wheel-arc")
            context.input.setCursorPos(illusion[1][0], illusion[1][1])
            context.waitTicks(2)
            context.takeScreenshot("wheel-arc-row")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(10)
            context.runOnClient<Throwable> {
                val modes = ClientAbilityState.entries.first { it.identity == ILLUSION }.modes
                val picked = ClientAbilityState.modeOf(ILLUSION)
                check(picked == modes[ARC_ROW].id) { "Клік по рядку дуги не обрав режим: $picked" }
            }

            // R again closes it — and the key is up when the world gets it back: no stray cast, no walking on.
            context.input.pressKey(GLFW.GLFW_KEY_R)
            context.waitTicks(2)
            check(!wheelOpen(context)) { "Закріплене колесо не закрилось на R" }
            val stopped = z(context)
            context.waitTicks(10)
            check(z(context) - stopped < STILL) { "Після закриття гравець іде далі сам" }
            context.runOnClient<Throwable> { check(!it.options.keyUp.isDown) { "Клавіша руху лишилась натиснутою" } }
        }
    }

    private fun wheelOpen(context: ClientGameTestContext): Boolean =
        context.computeOnClient<Boolean, Throwable> { it.gui.screen() is AbilityWheelScreen }

    private fun z(context: ClientGameTestContext): Double =
        context.computeOnClient<Double, Throwable> { client: Minecraft -> client.player!!.z }

    private companion object {
        /** Thirty ticks of walking cover several blocks; anything over one means the keys reached the world. */
        const val WALKED = 1.0
        const val STILL = 0.2

        /** From 40 ticks held to past the 80-tick pin. */
        const val PIN_WAIT = 50

        /** The card at nine o'clock: the one nearest the side panel. */
        const val DESCRIBED_CARD = 9

        /** The Fool's ability with modes, and the row of its arc the cursor goes out to. */
        const val ILLUSION = "fool_illusion_creation"
        const val ARC_ROW = 1
    }
}
