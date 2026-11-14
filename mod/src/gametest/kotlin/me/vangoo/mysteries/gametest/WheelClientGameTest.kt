package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * The ability wheel under real input: held, it lets the player walk; held for six seconds, it pins
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

            // Past six seconds of holding: pinned. Letting go no longer closes it.
            context.waitTicks(PIN_WAIT)
            context.takeScreenshot("wheel-pinned")
            context.input.releaseKey(GLFW.GLFW_KEY_R)
            context.waitTicks(5)
            check(wheelOpen(context)) { "Закріплене колесо закрилось, коли R відпустили" }

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

        /** From 40 ticks held to past the 120-tick pin. */
        const val PIN_WAIT = 90
    }
}
