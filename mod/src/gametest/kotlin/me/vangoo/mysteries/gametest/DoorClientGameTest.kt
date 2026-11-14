package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks

/**
 * The Door's doors as a player sees them, cast for real through the same request a key press sends:
 * a wall cracking round its arch and swinging its own face open, the corridor behind it, the far
 * side, the scar; a door into the spirit world standing in the air; and a blink — a door opening
 * ahead, the Traveller rushing along a passage of arches, a door shutting behind them.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class DoorClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("weather clear")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-4 ~8 ~-1 ~24 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Door 5 @a")
            server.runCommand("time set noon")
            wall(server)
            context.waitTicks(20)

            // Straight ahead, at arm's length: the wall in front of the eyes.
            cast(server, "door_opening")
            context.waitTicks(3)
            context.takeScreenshot("door-opening-crack")
            context.waitTicks(3)
            context.takeScreenshot("door-opening-swing")
            // Through by now, the wall behind: a few steps on, turn round to the door on its far face.
            context.waitTicks(10)
            turn(server, 180f, back = 2.5)
            context.waitTicks(5)
            context.takeScreenshot("door-opening-far-side")
            // It shuts after five seconds and leaves a scar going out.
            context.waitTicks(100)
            context.takeScreenshot("door-opening-scar")

            context.waitTicks(60)
            server.runCommand("time set midnight")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.on { srv, player -> player.teleportTo(player.x - 5.0, player.y, player.z) }
            context.waitTicks(20)
            cast(server, "door_travellers_door")
            context.waitTicks(10)
            context.takeScreenshot("door-travellers-door-cut")
            context.waitTicks(20)
            context.takeScreenshot("door-travellers-door-open")
            // Recast: the Traveller changes their mind; the door that stands goes on to shut by itself.
            cast(server, "door_travellers_door")
            context.waitTicks(240)

            // From behind: the door ahead opens and the passage builds itself out, the Traveller rushes
            // along it; then from the side, the passage going out behind them.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            context.waitTicks(10)
            cast(server, "door_blink")
            context.waitTicks(2)
            context.takeScreenshot("door-blink-open")
            context.waitTicks(3)
            context.takeScreenshot("door-blink-flight")
            context.waitTicks(4)
            turn(server, -90f)
            context.waitTicks(2)
            context.takeScreenshot("door-blink-passage")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
        }
    }

    /**
     * A wall three blocks thick and three high a block and a half ahead, in two kinds of brick so the
     * leaves show they are cut from the very blocks they stand in; the player stands square to it.
     */
    private fun wall(server: TestServerContext) = server.on { srv, player ->
        val level = srv.overworld()
        val feet = player.blockPosition()
        player.teleportTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
        for (x in -3..3) for (y in 0..2) for (z in 2..4) {
            val brick = if ((x + y) % 2 == 0) Blocks.STONE_BRICKS else Blocks.MOSSY_STONE_BRICKS
            level.setBlockAndUpdate(feet.offset(x, y, z), brick.defaultBlockState())
        }
    }

    /**
     * Turns the player to [yaw], [back] blocks further along +z first: `tp ~ ~ ~` from the server would
     * take them back to its own spot.
     */
    private fun turn(server: TestServerContext, yaw: Float, back: Double = 0.0) = server.on { _, player ->
        player.teleportTo(player.level(), player.x, player.y, player.z + back, emptySet(), yaw, 0f, false)
    }

    /** The request a key press sends, with a full tank and no cooldown in the way. */
    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
