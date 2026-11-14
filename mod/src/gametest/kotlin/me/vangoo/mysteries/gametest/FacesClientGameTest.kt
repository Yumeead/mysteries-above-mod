package me.vangoo.mysteries.gametest

import com.mojang.authlib.GameProfile
import io.netty.channel.embedded.EmbeddedChannel
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.screen.FaceGalleryScreen
import me.vangoo.mysteries.pathways.fool.Faces
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.client.CameraType
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * The Faceless and the faces they have seen, in one client: a second player is made on the server
 * (no client of its own, so it is only ever seen), the first sighting sketches its face into the
 * Faceless's hand, a masked stranger is looked at to see whether its skin changes on this client
 * (the old open question: offline profiles have no textures, only the default skin by id), the
 * gallery opens on a cast at nobody, the picked face is worn — seen on oneself in F5 — and the
 * stranger's death is announced under the face it wore.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class FacesClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        val said = mutableListOf<String>()
        ServerMessageEvents.GAME_MESSAGE.register { _, message, _ -> said += message.string }

        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("fill ~-8 ~-1 ~-8 ~8 ~-1 ~12 minecraft:stone_bricks")
            server.runCommand("fill ~-8 ~ ~-8 ~8 ~4 ~12 minecraft:air")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 6 @a")
            server.runCommand("mysteries mastery 100 @a")
            context.waitTicks(10)

            // A stranger four blocks ahead, facing the Faceless — with a default skin unlike the Faceless's,
            // so a face changing hands is visible even where no profile carries a skin.
            val ownId = server.computeOnServer<UUID, Throwable> { it.playerList.players.first().uuid }
            val strangerId = context.computeOnClient<UUID, Throwable> {
                generateSequence { UUID.randomUUID() }.first { skinOf(it) != skinOf(ownId) }
            }
            server.on { srv, player -> stranger(srv, player, strangerId) }
            var waited = 0
            while (!seen(server, strangerId) && waited < 60) {
                context.waitTicks(1)
                waited++
            }
            check(seen(server, strangerId)) { "Незнайомця не побачено" }
            context.waitTicks(5)
            context.takeScreenshot("faces-sketch")
            context.waitTicks(20)
            context.takeScreenshot("faces-stranger")

            // The stranger in the Faceless's own face: does its skin change on this client?
            server.on { srv, player -> Disguise.wear(srv.playerList.getPlayer(strangerId)!!, Disguise.faceOf(player)) }
            context.waitTicks(10)
            context.takeScreenshot("faces-stranger-masked")
            val masked = context.computeOnClient<String, Throwable> { client ->
                (client.level!!.players().first { it.uuid == strangerId } as AbstractClientPlayer).skin.body().texturePath().toString()
            }
            check(masked == skinOf(ownId).toString()) { "Скін незнайомця в масці не змінився: $masked, а мав би ${skinOf(ownId)}" }
            server.on { srv, _ -> Disguise.remove(srv.playerList.getPlayer(strangerId)!!) }
            context.waitTicks(5)

            // A cast at nobody: the gallery of faces seen.
            server.runCommand("tp @a ~ ~ ~ 180 -60")
            context.waitTicks(5)
            cast(server, "fool_shapeshifting")
            context.waitTicks(5)
            check(context.computeOnClient<Boolean, Throwable> { it.gui.screen() is FaceGalleryScreen }) { "Галерея не відкрилась" }
            val head = context.computeOnClient<DoubleArray, Throwable> { client ->
                val scale = client.window.guiScale
                val screen = client.gui.screen()!!
                // The first cell: the grid's left edge, under the search line.
                val columns = ((screen.width - 70 - 16 - 24) / 56).coerceIn(1, 8)
                val left = (screen.width - columns * 56 - 70 - 16) / 2
                doubleArrayOf((left + 28.0) * scale, (30 + 18 + 10 + 12.0) * scale)
            }
            context.input.setCursorPos(head[0] + 2, head[1] + 2)
            context.waitTicks(1)
            context.input.setCursorPos(head[0], head[1])
            context.waitTicks(3)
            context.takeScreenshot("faces-gallery")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(10)
            check(context.computeOnClient<Boolean, Throwable> { it.gui.screen() == null }) { "Галерея не закрилась після вибору" }
            server.on { _, player -> check(Disguise.wornBy(player.uuid)?.id == strangerId) { "Обраного обличчя не надіто" } }

            // Oneself in the borrowed face.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            server.runCommand("tp @a ~ ~ ~ 0 10")
            context.waitTicks(10)
            context.takeScreenshot("faces-own")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            // The stranger dies in the Faceless's face: the game names the face, not the stranger.
            var faceName = ""
            server.on { srv, player ->
                faceName = player.gameProfile.name
                val stranger = srv.playerList.getPlayer(strangerId)!!
                Disguise.wear(stranger, Disguise.faceOf(player))
                stranger.kill(stranger.level())
            }
            context.waitTicks(3)
            val death = said.lastOrNull { it.contains(faceName) || it.contains(STRANGER) }
            check(death != null && faceName in death && STRANGER !in death) { "Смерть видала справжнє ім'я: $said" }
        }
    }

    /** A second player with no client: placed the way vanilla places one, its client "loaded". */
    private fun skinOf(id: UUID) = DefaultPlayerSkin.get(id).body().texturePath()

    private fun stranger(server: MinecraftServer, near: ServerPlayer, id: UUID): ServerPlayer {
        val level = near.level()
        val cookie = CommonListenerCookie.createInitial(GameProfile(id, STRANGER), false)
        val player = ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation())
        val connection = Connection(PacketFlow.SERVERBOUND)
        EmbeddedChannel(connection)
        server.playerList.placeNewPlayer(connection, player, cookie)
        player.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        player.teleportTo(level, near.x, near.y, near.z + 4.0, emptySet(), 180f, 0f, false)
        return player
    }

    private fun seen(server: TestServerContext, id: UUID): Boolean =
        server.computeOnServer<Boolean, Throwable> { srv -> Faces.seenBy(srv.playerList.players.first { it.uuid != id }).any { it.first.id == id } }

    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityServices.cooldowns.forget(player.uuid)
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first { it.gameProfile.name != STRANGER }) }

    private companion object {
        const val STRANGER = "Stranger"
    }
}
