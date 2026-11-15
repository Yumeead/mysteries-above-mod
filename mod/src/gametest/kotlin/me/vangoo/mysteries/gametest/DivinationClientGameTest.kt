package me.vangoo.mysteries.gametest

import com.mojang.authlib.GameProfile
import io.netty.channel.embedded.EmbeddedChannel
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.screen.ChoiceScreen
import me.vangoo.mysteries.pathways.fool.Faces
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * The Seer's divination in one client: a coin asked whether it is safe ahead; the spirit pendulum
 * leaning towards a stranger the Seer has seen; the pendulum snapping when the stranger is a
 * Beyonder far above the Seer (and the Seer struck back); and a dream at night that carries the
 * sleeper near the stranger for four seconds and wakes them in bed.
 *
 * The stranger is a second player made on the server, with no client: it is only ever seen.
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class DivinationClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("gamerule doMobSpawning false")
            server.runCommand("time set noon")
            server.runCommand("fill ~-8 ~-1 ~-8 ~8 ~-1 ~16 minecraft:stone_bricks")
            server.runCommand("fill ~-8 ~ ~-8 ~8 ~4 ~16 minecraft:air")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 9 @a")
            server.runCommand("mysteries mastery 100 @a")
            context.waitTicks(10)

            var strangerId = UUID(0, 0)
            server.on { srv, player -> strangerId = stranger(srv, player).uuid }
            var waited = 0
            while (!server.computeOnServer<Boolean, Throwable> { srv -> Faces.seenBy(seer(srv)).any { it.first.id == strangerId } } && waited < 60) {
                context.waitTicks(1)
                waited++
            }

            // The coin: "is it safe where I look?"
            hold(server, Items.GOLD_NUGGET)
            cast(server)
            pick(context, 0)
            context.waitTicks(22)
            context.takeScreenshot("divination-coin")
            context.waitTicks(30)

            // The pendulum, towards the stranger ahead.
            hold(server, Items.IRON_CHAIN)
            cast(server)
            pick(context, 0)
            context.waitTicks(12)
            context.takeScreenshot("divination-pendulum")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            context.waitTicks(6)
            context.takeScreenshot("divination-pendulum-front")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
            context.waitTicks(15 * 20)

            // The stranger made a Beyonder four Sequences above: the chain spins, snaps, and strikes back.
            server.runCommand("mysteries become Fool 5 $STRANGER")
            server.runCommand("gamemode survival @a")
            context.waitTicks(5)
            var before = 0f
            server.on { _, player -> before = player.health }
            cast(server)
            pick(context, 0)
            context.waitTicks(6)
            context.takeScreenshot("divination-snap")
            server.on { _, player -> check(player.health < before) { "Обрив маятника не вдарив: ${player.health} з $before" } }
            server.runCommand("gamemode creative @a")
            server.runCommand("mysteries forget $STRANGER")

            // The dream: in bed at night, the sleeper goes near the stranger for four seconds.
            server.runCommand("time set midnight")
            server.runCommand("setblock ~2 ~ ~ minecraft:red_bed[facing=south,part=foot]")
            server.runCommand("setblock ~2 ~ ~1 minecraft:red_bed[facing=south,part=head]")
            context.waitTicks(5)
            var bed = BlockPos.ZERO
            server.on { _, player ->
                bed = player.blockPosition().offset(2, 0, 1)
                player.startSleepInBed(bed)
            }
            context.waitTicks(10)
            cast(server)
            pick(context, 0)
            context.waitTicks(12)
            context.takeScreenshot("divination-dream")
            server.on { _, player -> check(player.gameMode() == GameType.SPECTATOR) { "Сон не забрав сплячого: ${player.gameMode()}" } }
            context.waitTicks(100)
            server.on { _, player ->
                check(player.gameMode() == GameType.CREATIVE) { "Сплячий не повернувся у свій режим: ${player.gameMode()}" }
                check(player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(bed)) < 3.0) { "Сплячий прокинувся не біля ліжка" }
            }
        }
    }

    /** Presses the [index]-th option of the questions screen the cast opened. */
    private fun pick(context: ClientGameTestContext, index: Int) {
        context.waitTicks(3)
        context.runOnClient<Throwable> { client ->
            val screen = client.gui.screen() as? ChoiceScreen ?: error("Екран «Питання» не відкрився: ${client.gui.screen()}")
            screen.children().filterIsInstance<Button>()[index].onPress(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        }
    }

    private fun hold(server: TestServerContext, item: net.minecraft.world.item.Item) =
        server.on { _, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(item)) }

    private fun cast(server: TestServerContext) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityServices.cooldowns.forget(player.uuid)
        AbilityRequests.cast(player, AbilityIdentity.of("fool_divination_arts"))
    }

    private fun stranger(server: MinecraftServer, near: ServerPlayer): ServerPlayer {
        val level = near.level()
        val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), STRANGER), false)
        val player = ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation())
        val connection = Connection(PacketFlow.SERVERBOUND)
        EmbeddedChannel(connection)
        server.playerList.placeNewPlayer(connection, player, cookie)
        player.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        player.teleportTo(level, near.x, near.y, near.z + 8.0, emptySet(), 180f, 0f, false)
        return player
    }

    private fun seer(server: MinecraftServer): ServerPlayer = server.playerList.players.first { it.gameProfile.name != STRANGER }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, seer(srv)) }

    private companion object {
        const val STRANGER = "Stranger"
    }
}
