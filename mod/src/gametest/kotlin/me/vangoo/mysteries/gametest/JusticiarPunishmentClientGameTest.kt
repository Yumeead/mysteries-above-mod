package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.render.WorldEffects
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes

/**
 * The Paladin's Punishment: a real cast on a husk (the dawn behind the Paladin, the shackles on the husk, the ring of
 * choosing), the Paladin growing in the marked one's eyes (a client sees that only when it is the one marked, so the
 * mark is laid on this player by a villager, sent straight to the client), and a breaker of a Prohibition marked by
 * itself. Effects are held while each screenshot is taken. Run with `-Ponly=punishment`.
 */
@Suppress("UnstableApiUsage")
class JusticiarPunishmentClientGameTest : FabricClientGameTest {

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
            server.runCommand("fill -16 -61 -16 16 -61 16 minecraft:stone_bricks")
            server.runCommand("mysteries become Justiciar 5 @a")
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            context.waitTicks(10)

            // The cast: from behind and to the side, so both the dawn behind the Paladin and the husk are in frame.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            server.runCommand("summon husk 0.5 -60 5.5 {NoAI:1b}")
            context.waitTicks(5)
            server.on { srv, player ->
                srv.overworld().getEntities(EntityTypes.HUSK, player.boundingBox.inflate(12.0)) { true }.firstOrNull()
                    ?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, it.eyePosition) }
            }
            cast(server, "justiciar_punishment")
            context.waitTick()
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~40 ~")
            series(context, "punishment-cast", listOf(2, 5, 9, 14, 20, 40))
            server.runCommand("kill @e[type=husk]")

            // The Paladin grows in the marked one's eyes: a villager marks this player, as a server would tell it.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            server.runCommand("summon villager 0.5 -60 4.5 {NoAI:1b,Rotation:[180f,0f]}")
            context.waitTicks(10)
            context.runOnClient<Throwable> { client ->
                val me = client.player!!
                val villager = client.level!!.entitiesForRendering().first { it.type == EntityTypes.VILLAGER }
                WorldEffects.accept(WorldEffectPayload.Lash(villager.id, me.id, WorldEffectPayload.Lash.Shape.MARK, 0xEBC38E))
            }
            series(context, "punishment-loom", listOf(0, 6, 12, 24))
            server.runCommand("kill @e[type=villager]")
            context.waitTicks(10)

            // A Paladin's Prohibition marks its breaker by itself, and says so.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            server.runCommand("summon bat 3 -57 5")
            server.runCommand("summon bat -2 -57 6")
            context.waitTicks(5)
            pick(server, "justiciar_power_prohibition", "flight")
            cast(server, "justiciar_power_prohibition")
            series(context, "punishment-breach", listOf(32, 36, 42, 60))
        }
    }

    private fun series(context: ClientGameTestContext, name: String, ages: List<Int>) {
        var age = 0
        for (at in ages) {
            context.waitTicks(at - age)
            age = at
            context.runOnClient<Throwable> { WorldEffects.held = true }
            context.takeScreenshot("$name-${at.toString().padStart(3, '0')}")
            context.runOnClient<Throwable> { WorldEffects.held = false }
        }
    }

    private fun pick(server: TestServerContext, identity: String, mode: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        val ability = beyonder.abilityBy(AbilityIdentity.of(identity)) as? Ability ?: error("Немає $identity")
        check(AbilityServices.modes.select(player.uuid, ability, beyonder.sequence, mode)) { "Режиму $mode немає" }
    }

    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
