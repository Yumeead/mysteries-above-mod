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
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes

/**
 * The Judge's Verdict, each of its five sentences cast for real on a mode picked as the wheel picks it, and shot
 * tick by tick with the effects held while each screenshot is taken (see `WorldEffects.held`). From behind and turned
 * a little, so the caster and the target are both in the frame. Run with `-Ponly=verdict`.
 */
@Suppress("UnstableApiUsage")
class JusticiarVerdictClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("weather clear")
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-12 ~-1 ~-12 ~12 ~-1 ~16 minecraft:stone_bricks")
            server.runCommand("tp @a 0.5 ~ 0.5 0 0")
            server.runCommand("mysteries become Justiciar 5 @a")
            context.waitTicks(10)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }

            // Exile: husks round the caster, thrown out by the wave.
            for ((x, z) in listOf(3 to 0, -3 to 1, 0 to 3, 2 to -3)) server.runCommand("summon husk ~$x ~ ~$z")
            cast(server, "exile")
            series(context, "verdict-exile", listOf(1, 4, 6, 7, 8, 10, 13, 18))
            server.runCommand("kill @e[type=husk]")
            context.waitTicks(40)

            // Imprison: the walls closing in layers, the liquid running, the release.
            target(server, "villager", 4.0)
            cast(server, "imprison")
            series(context, "verdict-imprison", listOf(6, 8, 10, 12, 16, 40, 80, 160, 164, 168), server)
            context.waitTicks(20)
            server.runCommand("tp @a 0.5 ~ 0.5 0 0")

            // Confinement: the cell rising, and rippling where the villager is thrown against its wall.
            target(server, "villager", 4.0)
            cast(server, "confine")
            series(context, "verdict-confine", listOf(6, 10, 14, 18, 24, 34), server)
            server.runCommand("execute as @e[type=villager] at @s run tp @s ~7 ~ ~")
            series(context, "verdict-confine-struck", listOf(1, 3, 6, 10, 14))
            context.waitTicks(260)
            server.runCommand("tp @a 0.5 ~ 0.5 0 0")

            // Death: the dash with its afterimages, the blow.
            target(server, "husk", 8.0)
            cast(server, "death")
            series(context, "verdict-death", listOf(6, 7, 8, 9, 10, 11, 13, 16, 20), server)
            server.runCommand("kill @e[type=husk]")
            context.waitTicks(20)
            server.runCommand("tp @a 0.5 ~ 0.5 0 0")
            context.waitTicks(5)

            // Flog: three strokes of the unseen whip, a cut at each.
            target(server, "villager", 3.0)
            cast(server, "flog")
            series(context, "verdict-flog", listOf(7, 8, 10, 13, 15, 19, 22, 30), server)
        }
    }

    /**
     * Screenshots at the given ages (ticks since the cast), the effects held while each is taken. The caster is turned
     * after the first shot so the line to the target is seen from the side.
     */
    private fun series(context: ClientGameTestContext, name: String, ages: List<Int>, server: TestServerContext? = null) {
        var age = 0
        // Turned once the target is taken: from behind, a target straight ahead is hidden by the caster's own back.
        if (server != null) {
            context.waitTick()
            age = 1
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~55 ~")
        }
        for (at in ages) {
            context.waitTicks(at - age)
            age = at
            context.runOnClient<Throwable> { WorldEffects.held = true }
            context.takeScreenshot("$name-${at.toString().padStart(3, '0')}")
            context.runOnClient<Throwable> { WorldEffects.held = false }
        }
    }

    /** One standing [kind] of creature [ahead] blocks in front, the caster looking at it, then turned to see it side-on. */
    private fun target(server: TestServerContext, kind: String, ahead: Double) {
        server.runCommand("kill @e[type=villager]")
        server.runCommand("kill @e[type=husk]")
        server.runCommand("summon $kind 0.5 ~ ${0.5 + ahead} {NoAI:1b}")
        server.on { srv, player ->
            srv.overworld().getEntities(if (kind == "husk") EntityTypes.HUSK else EntityTypes.VILLAGER, player.boundingBox.inflate(16.0)) { true }
                .firstOrNull()?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, it.eyePosition) }
        }
    }

    /** Picks the Verdict's [sentence] as the wheel would, and casts it with a full tank and no cooldown. */
    private fun cast(server: TestServerContext, sentence: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        val verdict = beyonder.abilityBy(AbilityIdentity.of("justiciar_verdict")) as? Ability ?: error("Вердикту немає")
        check(AbilityServices.modes.select(player.uuid, verdict, beyonder.sequence, sentence)) { "Режиму $sentence немає" }
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of("justiciar_verdict"))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
