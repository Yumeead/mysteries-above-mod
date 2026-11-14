package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload.Action
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.LeverBlock
import org.lwjgl.glfw.GLFW

/**
 * The Marionettist's threads on the wheel: the tab, the puppets drawn as their bodies, the column of
 * what they can be told — and one order given by real input (hold R, Tab, rest on the card, ↓ ↓,
 * let go: "Follow"). The other verbs go through the same handler the wheel's packet reaches, and
 * each is checked by what it does to the world: a lever flipped, items brought, places swapped, an
 * item handed over, a thread cut.
 *
 * The puppets are seized straight away: breaking a beast's will takes fifteen seconds of a real cast,
 * and that is Marionette Control's own scene in `FoolClientGameTest`.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class ThreadsClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("fill ~-12 ~-1 ~-12 ~12 ~-1 ~24 minecraft:stone_bricks")
            // The world is generated anew each run: a hill or a tree on the yard would be in a puppet's way.
            server.runCommand("fill ~-12 ~ ~-12 ~12 ~5 ~24 minecraft:air")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 5 @a")
            server.runCommand("mysteries mastery 100 @a")
            server.runCommand("summon pig ~-2 ~ ~5")
            server.runCommand("summon husk ~2 ~ ~5 {NoAI:0b}")
            context.waitTicks(10)
            server.on { srv, player ->
                val level = player.level()
                level.getEntitiesOfClass(Mob::class.java, player.boundingBox.inflate(10.0)) { it.type == EntityTypes.PIG || it.type == EntityTypes.HUSK }
                    .sortedBy { it.type != EntityTypes.PIG }
                    .forEach { Marionettes.seize(player, it) }
                Marionettes.pushState(player)
                check(Marionettes.of(player.uuid).size == 2) { "Маріонеток не дві" }
            }
            context.waitTicks(10)

            // The wheel, its threads tab, the first puppet's card, its column.
            context.input.holdKey(GLFW.GLFW_KEY_R)
            context.waitTicks(8)
            context.input.pressKey(GLFW.GLFW_KEY_TAB)
            context.waitTicks(2)
            val cursor = context.computeOnClient<DoubleArray, Throwable> { client ->
                val scale = client.window.guiScale
                val (dx, dy) = WheelLayout.offset(0, 2)
                doubleArrayOf(client.window.screenWidth / 2.0 + dx * scale, client.window.screenHeight / 2.0 + dy * scale)
            }
            // The first move after a screen opens is swallowed by the mouse handler: move twice.
            context.input.setCursorPos(cursor[0] + 2, cursor[1] + 2)
            context.waitTicks(1)
            context.input.setCursorPos(cursor[0], cursor[1])
            context.waitTicks(8)
            context.takeScreenshot("threads-tab")
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.waitTicks(2)
            context.takeScreenshot("threads-verb")
            context.input.releaseKey(GLFW.GLFW_KEY_R)
            context.waitTicks(3)
            server.on { _, player ->
                val pig = puppet(player, EntityTypes.PIG)
                check(Marionettes.orderOf(pig) is MarionetteEntity.Order.Follow) { "«Слідувати» з колеса не дійшло: ${Marionettes.orderOf(pig)}" }
            }

            // Stay, then press the lever: the pig walks up to it and flips it.
            server.runCommand("setblock ~-2 ~ ~9 minecraft:lever[face=floor]")
            order(server, EntityTypes.PIG, Action.USE, block = { it.blockPosition().offset(-2, 0, 9) })
            context.waitTicks(100)
            server.on { _, player ->
                val lever = player.level().getBlockState(player.blockPosition().offset(-2, 0, 9))
                check(lever.getValue(LeverBlock.POWERED)) { "Маріонетка не натиснула важіль" }
            }

            // Gather: three things dropped near the husk end up at the Marionettist's feet.
            server.runCommand("summon item ~3 ~ ~8 {Item:{id:\"minecraft:apple\",count:1}}")
            server.runCommand("summon item ~1 ~ ~9 {Item:{id:\"minecraft:bread\",count:2}}")
            order(server, EntityTypes.HUSK, Action.GATHER)
            context.waitTicks(300)
            server.on { _, player ->
                // Dropped at the feet, and a player picks up what lies at their feet.
                val near = player.level().getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(3.0)).map { it.item.item } +
                    player.inventory.nonEquipmentItems.filterNot { it.isEmpty }.map { it.item }
                check(Items.APPLE in near && Items.BREAD in near) {
                    val husk = puppet(player, EntityTypes.HUSK)
                    val lying = player.level().getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(30.0)).map { "${it.item.item}@${it.blockPosition()}" }
                    "Зібране не принесено: $near; гравець ${player.blockPosition()}, лушпак ${husk.blockPosition()} ${Marionettes.orderOf(husk)} у руці ${husk.mainHandItem}; лежить $lying"
                }
            }

            // Hand over: the stick goes to the pig, its empty hand comes back.
            server.on { _, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.STICK)) }
            order(server, EntityTypes.PIG, Action.ARM)
            context.waitTicks(2)
            server.on { _, player ->
                check(puppet(player, EntityTypes.PIG).mainHandItem.`is`(Items.STICK) && player.mainHandItem.isEmpty) { "Предмет не передано" }
            }

            // Swap places with the husk.
            var before = net.minecraft.world.phys.Vec3.ZERO
            server.on { _, player -> before = puppet(player, EntityTypes.HUSK).position() }
            order(server, EntityTypes.HUSK, Action.SWAP)
            context.waitTicks(3)
            context.takeScreenshot("threads-swapped")
            server.on { _, player -> check(player.position().distanceTo(before) < 0.5) { "Місцями не помінялись" } }

            // Speak, guard, come — and cut: the pig falls.
            order(server, EntityTypes.PIG, Action.SPEAK, say = "Хрю.")
            order(server, EntityTypes.HUSK, Action.GUARD)
            order(server, EntityTypes.PIG, Action.COME)
            context.waitTicks(5)
            order(server, EntityTypes.PIG, Action.CUT)
            context.waitTicks(5)
            server.on { _, player -> check(Marionettes.of(player.uuid).size == 1) { "Нитку не обрізано" } }
        }
    }

    private fun order(
        server: TestServerContext,
        type: net.minecraft.world.entity.EntityType<*>,
        action: Action,
        block: (ServerPlayer) -> BlockPos? = { null },
        say: String = "",
    ) = server.on { _, player ->
        Marionettes.handle(player, MarionetteOrderPayload(action, puppet = puppet(player, type).uuid, block = block(player), say = say))
    }

    private fun puppet(player: ServerPlayer, type: net.minecraft.world.entity.EntityType<*>): LivingEntity =
        Marionettes.of(player.uuid).first { it.type == type }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
