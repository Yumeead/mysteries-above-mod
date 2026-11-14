package me.vangoo.mysteries.gametest

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.entity.HumanEntity
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * The Fool's paper and threads as a player sees them, cast for real through the same request a key
 * press sends: a sheet thrown, a batch of dolls folded, a blow taken by a paper body; a pig's own
 * threads, the grip running ink down them, the puppet on black strings and the pull of stepping into
 * it; and the Faceless's trade — the paper mask settling on and peeling off, a face taken off one head
 * and onto another, a borrowed face torn on the way down.
 *
 * Two shapes are sent straight from the server rather than earned: the spool (it winds only out of a
 * puppet made from a Beyonder, and this world has one player) and the face taken (Shapeshifting takes
 * it off a player). What they draw is the same packet the powers send.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class FoolClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("weather clear")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-4 ~8 ~-1 ~16 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 5 @a")
            server.runCommand("mysteries mastery 100 @a")
            server.runCommand("give @a minecraft:paper 64")
            context.waitTicks(20)

            // A sheet off the hand at a zombie ahead: it takes three ticks to get there.
            standing(server, "zombie", 6)
            context.input.pressKey { it.keyUse }
            context.waitTicks(2)
            context.takeScreenshot("fool-paper-throw")
            server.runCommand("kill @e[type=zombie]")

            context.waitTicks(20)
            cast(server, "fool_paper_substitution")
            context.waitTicks(5)
            context.takeScreenshot("fool-paper-dolls")

            // The guard up, then a blow: the body that takes it is paper, and the Magician is elsewhere.
            sneaking(server, true)
            cast(server, "fool_paper_substitution")
            sneaking(server, false)
            server.runCommand("gamemode survival @a")
            context.waitTicks(10)
            var struck = Vec3.ZERO
            server.on { _, player -> struck = player.position() }
            server.runCommand("damage @a 8 minecraft:generic")
            // The Magician is somewhere near by now, at random: step back from the body that took the
            // blow and look at it, from where the whole figure fits in the frame.
            server.on { _, player ->
                val back = struck.add(0.0, 0.0, -VIEW_BACK)
                player.teleportTo(player.level(), back.x, back.y, back.z, emptySet(), 0f, 0f, false)
                player.lookAt(EntityAnchorArgument.Anchor.EYES, struck.add(0.0, 1.0, 0.0))
            }
            context.waitTicks(1)
            context.takeScreenshot("fool-paper-substitution")
            server.runCommand("gamemode creative @a")

            // A pig's own threads, then the grip on them, then the puppet.
            context.waitTicks(20)
            server.runCommand("tp @a ~ ~ ~ 0 0")
            standing(server, "pig", 5)
            context.waitTicks(30)
            context.takeScreenshot("fool-thread-sight")
            cast(server, "fool_marionettist_control")
            context.waitTicks(60)
            context.takeScreenshot("fool-marionette-grip")
            var waited = 0
            while (!puppeted(server) && waited < PUPPET_TIMEOUT) {
                context.waitTicks(20)
                waited += 20
            }
            check(puppeted(server)) { "Свиня так і не стала маріонеткою" }
            context.waitTicks(10)
            context.takeScreenshot("fool-marionette-puppet")

            // Into the puppet and back: a thread from the body that stays, jerked taut. Both face across
            // the line between them, so the camera behind sees the thread from the side.
            server.on { _, player ->
                player.teleportTo(player.level(), player.x, player.y, player.z, emptySet(), ACROSS, 0f, false)
                Marionettes.of(player.uuid).forEach { it.yRot = ACROSS; it.yBodyRot = ACROSS; it.yHeadRot = ACROSS }
            }
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            context.waitTicks(10)
            cast(server, "fool_marionette_possession")
            context.waitTicks(3)
            context.takeScreenshot("fool-possession-in")
            context.waitTicks(40)
            cast(server, "fool_marionette_possession")
            context.waitTicks(3)
            context.takeScreenshot("fool-possession-out")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
            server.runCommand("kill @e[type=pig]")

            // A Characteristic gathering itself: the ball halfway wound.
            context.waitTicks(20)
            server.on { _, player ->
                player.teleportTo(player.level(), player.x, player.y, player.z, emptySet(), 0f, 0f, false)
                val at = player.position().add(0.0, 0.6, 3.0)
                ModChannels.WORLD_EFFECT.sendTo(player, WorldEffectPayload.Spool(at, PathwayBranding.liquidOf("Fool").packed, SPOOL_TICKS))
            }
            context.waitTicks(SPOOL_TICKS / 2)
            context.takeScreenshot("fool-spool")

            // The face, from the front: the paper mask on and off, a face taken, the borrowed one dropped.
            context.waitTicks(SPOOL_TICKS)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            context.waitTicks(10)
            cast(server, "fool_expression_control")
            context.waitTicks(4)
            context.takeScreenshot("fool-mask")
            context.waitTicks(20)
            cast(server, "fool_expression_control")
            context.waitTicks(7)
            context.takeScreenshot("fool-unmask")

            context.waitTicks(20)
            server.runCommand("execute positioned ~2.5 ~ ~1.5 run mysteries creature spawn mysteries_above:citizen")
            context.waitTicks(10)
            server.on { srv, player ->
                val citizen = srv.overworld().getEntitiesOfClass(HumanEntity::class.java, player.boundingBox.inflate(8.0)).first()
                citizen.isNoAi = true
                ModChannels.WORLD_EFFECT.sendTo(player, WorldEffectPayload.Face(WorldEffectPayload.Face.Kind.TAKE, citizen.id, player.id))
            }
            context.waitTicks(4)
            context.takeScreenshot("fool-face-take")
            context.waitTicks(20)
            server.on { _, player ->
                ModChannels.WORLD_EFFECT.sendTo(player, WorldEffectPayload.Face(WorldEffectPayload.Face.Kind.DROP, player.id, player.id))
            }
            context.waitTicks(11)
            context.takeScreenshot("fool-face-drop")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
        }
    }

    /** A [type] standing still [ahead] blocks in front, looked at in the eyes. */
    private fun standing(server: TestServerContext, type: String, ahead: Int) {
        server.runCommand("summon $type ~ ~ ~$ahead {NoAI:1b}")
        server.on { srv, player ->
            srv.overworld().getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(ahead + 2.0)) { it !== player }
                .firstOrNull()?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, it.eyePosition) }
        }
    }

    private fun puppeted(server: TestServerContext): Boolean {
        var held = false
        server.on { _, player -> held = Marionettes.of(player.uuid).isNotEmpty() }
        return held
    }

    private fun sneaking(server: TestServerContext, down: Boolean) = server.on { _, player -> player.isShiftKeyDown = down }

    /** The request a key press sends, with a full tank and no cooldown in the way. */
    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }

    private companion object {
        const val PUPPET_TIMEOUT = 800
        const val SPOOL_TICKS = 120

        /** Facing east: the pig ahead to the south is then off to the side. */
        const val ACROSS = -90f

        /** How far back the paper body is shot from. */
        const val VIEW_BACK = 4.0
    }
}
