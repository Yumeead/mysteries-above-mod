package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.client.beyonder.ClientAbilityState
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
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.level.block.LeverBlock
import org.lwjgl.glfw.GLFW

/**
 * The Marionettist's threads on the wheel: the arrow card to the puppets' ring, the puppets drawn as
 * their bodies, their card at the left, the column of what they can be told, and one order given by
 * real input (hold R, rest on the arrow, rest on the card, down twice, let go: "Follow"). The other
 * verbs go through the same handler the wheel's packet reaches, and each is checked by what it does
 * to the world: a lever flipped, a sheep struck, places swapped, a thread cut.
 *
 * The puppets are seized straight away: breaking a beast's will takes fifteen seconds of a real cast,
 * and that is Marionette Control's own scene in `FoolClientGameTest`. What each order makes a body
 * do is `MarionetteGameTests`.
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
            server.runCommand("difficulty normal")
            server.runCommand("fill ~-12 ~-1 ~-12 ~12 ~-1 ~24 minecraft:stone_bricks")
            // The world is generated anew each run: a hill or a tree on the yard would be in a puppet's way.
            server.runCommand("fill ~-12 ~ ~-12 ~12 ~5 ~24 minecraft:air")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Fool 5 @a")
            server.runCommand("mysteries mastery 100 @a")
            server.runCommand("summon pig ~-2 ~ ~5")
            server.runCommand("summon husk ~2 ~ ~5 {NoAI:0b}")
            context.waitTicks(10)
            server.on { _, player ->
                val level = player.level()
                level.getEntitiesOfClass(Mob::class.java, player.boundingBox.inflate(10.0)) { it.type == EntityTypes.PIG || it.type == EntityTypes.HUSK }
                    .sortedBy { it.type != EntityTypes.PIG }
                    .forEach { Marionettes.seize(player, it) }
                Marionettes.pushState(player)
                check(Marionettes.of(player.uuid).size == 2) { "Маріонеток не дві" }
            }
            context.waitTicks(5)

            // Left alone, neither puppet goes anywhere or looks for a fight.
            val still = server.compute { _, player -> Marionettes.of(player.uuid).map { it.position() } }
            context.waitTicks(40)
            server.on { _, player ->
                Marionettes.of(player.uuid).zip(still).forEach { (puppet, at) ->
                    check(puppet.position().distanceTo(at) < 0.5) { "${puppet.name.string} пішла сама: ${puppet.position().distanceTo(at)} бл." }
                    check((puppet as Mob).target == null) { "${puppet.name.string} сама обрала ціль" }
                }
            }

            // The wheel; resting on the arrow card off its right side turns it to the puppets.
            context.input.holdKey(GLFW.GLFW_KEY_R)
            context.waitTicks(8)
            val arrow = context.computeOnClient<DoubleArray, Throwable> { client ->
                val scale = client.window.guiScale
                val (x, _, w) = WheelLayout.switcher(ClientAbilityState.entries.size.coerceAtMost(WheelLayout.PAGE_SIZE), CARD)
                doubleArrayOf(client.window.screenWidth / 2.0 + (x + w / 2) * scale, client.window.screenHeight / 2.0)
            }
            // The first move after a screen opens is swallowed by the mouse handler: move twice.
            context.input.setCursorPos(arrow[0] + 2, arrow[1] + 2)
            context.waitTicks(1)
            context.input.setCursorPos(arrow[0], arrow[1])
            context.waitTicks(3)
            context.takeScreenshot("threads-arrow")
            context.waitTicks(6)
            val cursor = context.computeOnClient<DoubleArray, Throwable> { client ->
                val scale = client.window.guiScale
                val (dx, dy) = WheelLayout.offset(0, 2)
                doubleArrayOf(client.window.screenWidth / 2.0 + dx * scale, client.window.screenHeight / 2.0 + dy * scale)
            }
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

            // Strike my target: the Marionettist hits a sheep, and the husk goes for it with its own hands.
            server.runCommand("summon sheep ~6 ~ ~6 {NoAI:1b}")
            context.waitTicks(2)
            order(server, EntityTypes.HUSK, Action.ATTACK)
            server.on { _, player ->
                sheep(player).first().hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1f)
            }
            context.waitTicks(100)
            server.on { _, player ->
                val husk = puppet(player, EntityTypes.HUSK)
                val sheep = sheep(player)
                check(sheep.isEmpty() || sheep.first().lastHurtByMob === husk) {
                    "Лушпак не підтримав удар: ${Marionettes.orderOf(husk)}, ціль ${(husk as Mob).target?.name?.string}"
                }
            }

            // Swap places with the husk.
            var before = net.minecraft.world.phys.Vec3.ZERO
            server.on { _, player -> before = puppet(player, EntityTypes.HUSK).position() }
            order(server, EntityTypes.HUSK, Action.SWAP)
            context.waitTicks(3)
            context.takeScreenshot("threads-swapped")
            server.on { _, player -> check(player.position().distanceTo(before) < 0.5) { "Місцями не помінялись" } }

            // Speak, guard, come, and cut: the pig falls.
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

    private fun sheep(player: ServerPlayer): List<Sheep> =
        player.level().getEntitiesOfClass(Sheep::class.java, player.boundingBox.inflate(24.0))

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }

    private fun <T> TestServerContext.compute(block: (MinecraftServer, ServerPlayer) -> T): T =
        computeOnServer<T, Throwable> { srv -> block(srv, srv.playerList.players.first()) }

    private companion object {
        /** A wheel card's size, as the wheel draws it. */
        const val CARD = 24
    }
}
