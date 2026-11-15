package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.client.hud.DangerArrows
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The Seer's Danger Intuition as the arrows by the crosshair. An arrow fired at the Seer is felt the
 * tick it leaves — before it lands, which is the whole point; then one from behind, a hissing creeper
 * and a lit fuse. Arrows are summoned already moving: the spawn hook sees a command's arrow the same
 * way it sees a skeleton's.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class DangerClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("difficulty normal")
            server.runCommand("fill ~-12 ~-1 ~-24 ~12 ~-1 ~24 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 9 @a")
            server.runCommand("gamemode creative @a")
            context.waitTicks(20)
            check(threats(context) == 0) { "Загроза без причини" }

            // An arrow from 20 blocks ahead, 2.5 blocks a tick: eight ticks of warning.
            server.runCommand("summon arrow ~ ~1.5 ~20 {Motion:[0.0,0.05,-2.5]}")
            context.waitTicks(2)
            check(threats(context) > 0) { "Стрілу не відчуто до влучання" }
            context.takeScreenshot("danger-arrow-ahead")
            context.waitTicks(20)

            // One from behind: the chevron is at the bottom of the ring.
            server.runCommand("summon arrow ~ ~1.5 ~-20 {Motion:[0.0,0.05,2.5]}")
            context.waitTicks(2)
            context.takeScreenshot("danger-arrow-behind")
            context.waitTicks(20)

            // A shot that passes wide is not a threat.
            server.runCommand("kill @e[type=arrow]")
            context.waitTicks(15)
            val before = threats(context)
            server.runCommand("summon arrow ~8 ~1.5 ~20 {Motion:[0.0,0.05,-2.5]}")
            context.waitTicks(2)
            check(threats(context) <= before) { "Стрілу мимо прийнято за загрозу" }

            // A hissing creeper on the right and a lit fuse on the left: both count down.
            server.runCommand("summon creeper ~-4 ~ ~2 {ignited:1b,NoAI:1b}")
            server.runCommand("summon tnt ~4 ~ ~2 {fuse:60}")
            context.waitTicks(12)
            check(threats(context) >= 2) { "Крипер і TNT не відчуто" }
            context.takeScreenshot("danger-fuses")
        }
    }

    private fun threats(context: ClientGameTestContext): Int =
        context.computeOnClient<Int, Throwable> { DangerArrows.count }
}
