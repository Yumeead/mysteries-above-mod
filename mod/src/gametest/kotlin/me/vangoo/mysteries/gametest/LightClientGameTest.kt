package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.effect.beam
import me.vangoo.mysteries.effect.flare
import me.vangoo.mysteries.effect.flash
import me.vangoo.mysteries.effect.ward
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.HintPayload
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes

/**
 * What a power's light looks like in a real client: the additive glow, the stepped halo, a real
 * light block at night, the flash and the fizzle at the hand. None of it is reachable from a unit
 * test — a pipeline, a particle atlas and a HUD layer all live in the client alone.
 *
 * The beams are shot twice, at midnight and at noon: additive light is easy to love in the dark
 * and the first thing to wash out in the sun. The screenshots land in
 * `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class LightClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("weather clear")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-2 ~8 ~-1 ~14 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            // The fizzle takes its colour from the roster: a plain player has no pathway to draw with.
            server.runCommand("mysteries become Sun 9 @a")

            server.runCommand("time set midnight")
            beams(context, server, "light-beams-night")

            server.runCommand("summon villager ~ ~ ~4 {NoAI:1b}")
            context.waitTicks(10)
            server.on { srv, player ->
                srv.overworld().getEntities(EntityTypes.VILLAGER, player.boundingBox.inflate(8.0)) { true }
                    .forEach { ward(it, RgbColor(0x7FB2FF)) }
            }
            context.waitTicks(3)
            context.takeScreenshot("light-ward")
            server.runCommand("kill @e[type=villager]")
            // The body takes a second to fall and vanish; it has no place in the next frame.
            context.waitTicks(25)

            server.on { srv, player ->
                flare(AbilityServices.scheduling, srv.overworld(), BlockPos.containing(player.position().add(1.0, 0.0, 4.0)), 200, 15)
            }
            context.waitTicks(10)
            context.takeScreenshot("light-flare-night")

            server.on { srv, player -> flash(srv.overworld(), player.getEyePosition(1f).add(0.0, 0.0, 6.0), 24.0, 1f, SUN) }
            context.waitTicks(2)
            context.takeScreenshot("light-flash")

            context.runOnClient<Throwable> { ClientOptions.hints = true }
            server.on { _, player ->
                ModChannels.HINT.sendTo(player, HintPayload(Fizzle.EMPTY, Component.literal("Недостатньо духовності: потрібно 30, є 4")))
            }
            context.waitTicks(3)
            context.takeScreenshot("light-fizzle")
            context.runOnClient<Throwable> { ClientOptions.hints = false }

            server.runCommand("time set noon")
            context.waitTicks(40)
            beams(context, server, "light-beams-day")

            // Fabulous draws the translucent world into targets of its own and merges them by depth.
            context.runOnClient<Throwable> { it.options.improvedTransparency().set(true) }
            context.waitTicks(20)
            beams(context, server, "light-beams-day-fabulous")
            context.runOnClient<Throwable> { it.options.improvedTransparency().set(false) }
        }
    }

    /** A pillar a block across falling from high above, a thin thread across the yard, and a ray along the view. */
    private fun beams(context: ClientGameTestContext, server: TestServerContext, name: String) {
        server.on { srv, player ->
            val base = player.position()
            val scheduling = AbilityServices.scheduling
            beam(scheduling, srv.overworld(), base.add(0.0, 24.0, 9.0), base.add(0.0, 0.0, 9.0), SUN, 40, 1f)
            beam(scheduling, srv.overworld(), base.add(-5.0, 1.2, 5.0), base.add(5.0, 1.8, 11.0), SUN, 40, 0f)
            // From beside the eye straight ahead: the view the caster has of their own ray.
            val eye = player.getEyePosition(1f)
            beam(scheduling, srv.overworld(), eye.add(-0.36, -0.35, 0.5), eye.add(-1.5, -1.0, 13.0), SUN, 40, 0.25f)
        }
        context.waitTicks(12)
        context.takeScreenshot(name)
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }

    private companion object {
        val SUN = RgbColor(0xFFD27A)
    }
}
