package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload.AirShot
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * The Fool's air bullet as bent air. Most shots here are sent straight as the packet, slowed down so
 * a frame can catch the lens in flight: what they draw is the same packet the power sends. A husk
 * stands behind the lens's path — the frame's colour is copied after the solid entities, so it must
 * bend too. The last shot is cast for real, from the first person.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class AirBulletClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("weather clear")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-4 ~8 ~-1 ~24 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 -5")
            server.runCommand("summon husk ~ ~ ~7 {NoAI:1b,Rotation:[180f,0f]}")
            server.runCommand("mysteries become Fool 7 @a")
            server.runCommand("mysteries mastery 100 @a")
            context.waitTicks(20)

            // Across the view in front of the husk, slowed to a crawl: the lens and its wake.
            shoot(server, Vec3(-4.0, 1.4, 4.0), Vec3(4.0, 1.4, 4.0), SLOW, powered = false, AirShot.End.AIR)
            context.waitTicks(SLOW / 2)
            context.takeScreenshot("air-lens-day")
            context.waitTicks(SLOW / 2 + 2)
            context.takeScreenshot("air-spent-day")

            // The heavier shot, straight at the husk, and the burst where it lands.
            shoot(server, Vec3(-3.0, 1.5, 1.0), Vec3(0.0, 1.5, 6.6), 8, powered = true, AirShot.End.BODY)
            context.waitTicks(4)
            context.takeScreenshot("air-powered-flight")
            context.waitTicks(6)
            context.takeScreenshot("air-burst-body")

            // A wall stops it: a smaller burst on this side of it.
            server.runCommand("fill ~3 ~ ~5 ~3 ~2 ~9 minecraft:stone")
            context.waitTicks(10)
            shoot(server, Vec3(-3.0, 1.5, 7.0), Vec3(2.95, 1.5, 7.0), 6, powered = false, AirShot.End.WALL)
            context.waitTicks(8)
            context.takeScreenshot("air-burst-wall")
            server.runCommand("fill ~3 ~ ~5 ~3 ~2 ~9 minecraft:air")

            // At night the rim is what catches the eye.
            server.runCommand("time set midnight")
            context.waitTicks(10)
            shoot(server, Vec3(-4.0, 1.4, 4.0), Vec3(4.0, 1.4, 4.0), SLOW, powered = true, AirShot.End.AIR)
            context.waitTicks(SLOW / 2)
            context.takeScreenshot("air-lens-night")
            server.runCommand("time set noon")
            context.waitTicks(SLOW / 2 + 15)

            // Cast for real at the husk: the track from the first person, a tick after the shot.
            cast(server, "fool_air_bullet")
            context.waitTicks(1)
            context.takeScreenshot("air-first-person")
            context.waitTicks(3)
            context.takeScreenshot("air-first-person-burst")
        }
    }

    /** An air shot from [from] to [to], relative to the player's feet, flying [ticks]. */
    private fun shoot(server: TestServerContext, from: Vec3, to: Vec3, ticks: Int, powered: Boolean, end: AirShot.End) =
        server.on { _, player ->
            val feet = player.position()
            ModChannels.WORLD_EFFECT.sendTo(player, AirShot(feet.add(from), feet.add(to), ticks, powered, end))
        }

    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }

    private companion object {
        /** A shot slowed down so a frame catches it mid-flight. */
        const val SLOW = 40
    }
}
