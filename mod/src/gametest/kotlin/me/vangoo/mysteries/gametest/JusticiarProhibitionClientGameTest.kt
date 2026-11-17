package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.render.WorldEffects
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * The Judge's Prohibition, three of its laws cast for real on a mode picked as the wheel picks it, each with someone
 * to break it: bats under a ban on flight, an invisible villager under a ban on invisibility, husks under a ban on the
 * undead. After each cast the Judge steps up onto a stand to the side, to see the whole round from above. Effects are
 * held while each screenshot is taken. Run with `-Ponly=prohibition`.
 */
@Suppress("UnstableApiUsage")
class JusticiarProhibitionClientGameTest : FabricClientGameTest {

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
            server.runCommand("fill -24 -61 -24 24 -61 24 minecraft:stone_bricks")
            // The stand the Judge watches from: high on one side of the round.
            server.runCommand("fill -1 -54 -26 1 -54 -24 minecraft:stone_bricks")
            server.runCommand("mysteries become Justiciar 5 @a")
            context.waitTicks(10)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            scene(context, server, "flight", setup = {
                for ((x, z) in listOf(4 to 3, -5 to 6, 7 to -2, -3 to -6)) server.runCommand("summon bat $x -57 $z")
            })
            scene(context, server, "invisibility", setup = {
                server.runCommand("summon villager 4 -60 6 {NoAI:1b}")
                server.runCommand("effect give @e[type=villager] invisibility 120 0 true")
            })
            scene(context, server, "undead", setup = {
                for ((x, z) in listOf(3 to 4, -4 to 5, 5 to -3, -2 to -5, 8 to 8)) server.runCommand("summon husk $x -60 $z")
            })
        }
    }

    /**
     * Stand in the middle, set the breakers out, cast the law [mode], step onto the stand and look down at the round:
     * shots through the spreading, while it stands with its breakers, and once more after.
     */
    private fun scene(context: ClientGameTestContext, server: TestServerContext, mode: String, setup: () -> Unit) {
        server.runCommand("kill @e[type=!player]")
        server.runCommand("tp @a 0.5 -60 0.5 0 0")
        context.waitTicks(10)
        setup()
        context.waitTicks(5)
        cast(server, mode)
        context.waitTicks(2)
        server.runCommand("tp @a 0.5 -53 -24.5 0 28")
        var age = 2
        for (at in listOf(4, 10, 18, 26, 34, 44, 60, 80)) {
            context.waitTicks(at - age)
            age = at
            context.runOnClient<Throwable> { WorldEffects.held = true }
            context.takeScreenshot("prohibition-$mode-${at.toString().padStart(3, '0')}")
            context.runOnClient<Throwable> { WorldEffects.held = false }
        }
        context.waitTicks(20)
    }

    /** Picks the Prohibition's [mode] as the wheel would, and casts it with a full tank and no cooldown. */
    private fun cast(server: TestServerContext, mode: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        val prohibition = beyonder.abilityBy(AbilityIdentity.of("justiciar_power_prohibition")) as? Ability ?: error("Заборони немає")
        check(AbilityServices.modes.select(player.uuid, prohibition, beyonder.sequence, mode)) { "Закону $mode немає" }
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of("justiciar_power_prohibition"))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
