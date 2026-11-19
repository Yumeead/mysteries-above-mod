package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.render.WorldEffects
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Jurisdiction
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * The Sheriff's Area of Jurisdiction in a real client:
 * - what someone walking in sees (the client has one player, so the wave is handed to it as the server would send it);
 * - the Sheriff's own cast, the wave from their feet seen from above, posts rising at the edge and sinking;
 * - the posts that rise as the Sheriff walks up to the edge, by day and by night, and the light running along it as
 *   they cross.
 * Effects are held while each screenshot is taken. Run with `-Ponly=jurisdiction`.
 */
@Suppress("UnstableApiUsage")
class JusticiarJurisdictionClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("weather clear")
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("gamerule doMobSpawning false")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            server.runCommand("mysteries become Justiciar 8 @a")
            // Uneven ground in the square, so the wave and the posts can be seen to follow it.
            server.runCommand("fill 6 -60 -6 12 -59 4 minecraft:stone_bricks")
            server.runCommand("fill 74 -60 -4 82 -60 4 minecraft:grass_block")
            context.waitTicks(10)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            // Someone else's ground, stepped into from its south edge: a square of 20 round (0, 0).
            server.runCommand("tp @a 0.5 -60 17.5 180 20")
            context.waitTicks(10)
            context.runOnClient<Throwable> {
                WorldEffects.accept(Jurisdiction(1, Jurisdiction.Kind.REVEAL, BlockPos(0, -60, 0), 20, Vec3(0.5, -60.0, 17.5), 0xEBC38E))
            }
            shots(context, "reveal", listOf(3, 8, 14, 22, 32, 50, 75))

            // The Sheriff's own: cast at the middle, watched from above and to the side.
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            context.waitTicks(10)
            cast(server)
            context.waitTicks(1)
            server.runCommand("gamemode spectator @a")
            server.runCommand("tp @a 0.5 -28 -40.5 0 30")
            shots(context, "own", listOf(5, 15, 30, 50, 75, 100, 130))
            server.runCommand("gamemode creative @a")

            // Walking up to the east edge (half 80 at Seq. 8), and across it.
            server.runCommand("tp @a 70.5 -60 0.5 -90 10")
            context.waitTicks(20)
            for (x in listOf(73, 75, 77)) {
                server.runCommand("tp @a $x.5 -60 0.5 -90 10")
                context.waitTicks(16)
                screenshot(context, "near-$x")
            }
            server.runCommand("tp @a 81.5 -60 0.5 120 10")
            shots(context, "cross", listOf(2, 6, 12))

            server.runCommand("time set midnight")
            server.runCommand("tp @a 76.5 -60 3.5 -110 10")
            context.waitTicks(25)
            screenshot(context, "night")
        }
    }

    private fun shots(context: ClientGameTestContext, name: String, ages: List<Int>) {
        var age = 0
        for (at in ages) {
            context.waitTicks(at - age)
            age = at
            screenshot(context, "$name-${at.toString().padStart(3, '0')}")
        }
    }

    private fun screenshot(context: ClientGameTestContext, name: String) {
        context.runOnClient<Throwable> { WorldEffects.held = true }
        context.takeScreenshot("jurisdiction-$name")
        context.runOnClient<Throwable> { WorldEffects.held = false }
    }

    private fun cast(server: TestServerContext) = server.runOnServer<Throwable> { srv ->
        val player = srv.playerList.players.first()
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of("justiciar_area_of_jurisdiction"))
    }
}
