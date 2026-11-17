package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.client.render.Lashes
import me.vangoo.mysteries.client.render.WorldEffects
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.CameraType
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes

/**
 * The Justiciar's first six powers (Seq. 9 and 7) as a player sees them, cast for real through the request a
 * key press sends: Authority's frame and blow, the Gaze's eyes, the whip, the needle, the hoops and the
 * chain of bolts. Step 1 of the effects plan (artifact 3MmcvvuFaZxwwZzWxdRpUv, §8).
 *
 * Whatever rides the caster (frame, eyes) is shot from the front. The screenshots land in
 * `mod/build/run/clientGameTest/screenshots`; run only this with `-Ponly=justiciar`.
 */
@Suppress("UnstableApiUsage")
class JusticiarClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("weather clear")
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-10 ~-1 ~-6 ~10 ~-1 ~16 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Justiciar 5 @a")
            server.runCommand("time set noon")
            context.waitTicks(10)

            // Authority: the aura kindles, steps up at each knock, flares at the blow and burns out. From the front, so
            // the eyes show too; held between shots, like the whip.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            husks(server, 9.0)
            cast(server, "justiciar_authority")
            var aged = 0
            for (at in listOf(2, 8, 30, 41, 44, 81, 110, 121, 123, 126, 130, 136)) {
                context.waitTicks(at - aged)
                aged = at
                if (at == 8) assertEyes(context, EyeGlow.GOLD)
                context.runOnClient<Throwable> { WorldEffects.held = true }
                context.takeScreenshot("justiciar-authority-$at")
                context.runOnClient<Throwable> { WorldEffects.held = false }
            }
            context.waitTicks(20)

            // The aura on the move: running sideways it trails and leans back, stopping it swings past and settles,
            // jumping it lags. The body is moved a step a tick by the server and stands still while a shot is taken.
            // `execute at @s`: a bare `tp @a ~0.3 ~ ~` from the console counts `~` from the console, not the player.
            husks(server, 9.0)
            context.waitTicks(40)
            cast(server, "justiciar_authority")
            context.waitTicks(30)
            fun shot(name: String) {
                context.runOnClient<Throwable> { WorldEffects.held = true }
                context.takeScreenshot("justiciar-authority-move-$name")
                context.runOnClient<Throwable> { WorldEffects.held = false }
            }
            shot("0-still")
            for (step in 1..14) {
                server.runCommand("execute as @a at @s run tp @s ~0.3 ~ ~")
                context.waitTick()
                if (step == 6 || step == 14) shot("1-run-$step")
            }
            for ((name, ticks) in listOf("2-stop-1" to 1, "3-stop-4" to 3, "4-stop-8" to 4, "5-stop-16" to 8)) {
                context.waitTicks(ticks)
                shot(name)
            }
            server.runCommand("execute as @a at @s run tp @s ~ ~1.5 ~")
            context.waitTick()
            shot("6-jump-1")
            context.waitTicks(3)
            shot("7-jump-4")
            context.waitTicks(12)
            shot("8-landed")
            server.runCommand("execute as @a at @s run tp @s ~-4.2 ~ ~")
            context.waitTicks(80)

            // The stare: gold eyes once one gaze has held for a second.
            husks(server, 6.0)
            context.waitTicks(30)
            assertEyes(context, EyeGlow.GOLD)
            context.takeScreenshot("justiciar-gaze-gold")
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            // The whip: swung over the shoulder, unrolled, the crack, drawn back. The effects are held between
            // shots and let through one tick at a time, so each screenshot (which takes longer than a tick) lands
            // on its own phase.
            husks(server, 6.0)
            for (view in listOf(CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK)) {
                context.runOnClient<Throwable> { it.options.setCameraType(view) }
                context.waitTicks(2)
                cast(server, "justiciar_whip_of_pain")
                // From behind, the target straight ahead is hidden by one's own back: turn so the blow is seen side-on.
                if (view == CameraType.THIRD_PERSON_BACK) server.runCommand("tp @a ~ ~ ~ ~50 ~")
                context.waitTick()
                for (shot in 0 until WorldEffectPayload.Lash.Shape.WHIP.life) {
                    context.runOnClient<Throwable> { WorldEffects.held = true }
                    context.takeScreenshot("justiciar-whip-${view.name.lowercase()}-$shot")
                    context.runOnClient<Throwable> { WorldEffects.held = false }
                    context.waitTick()
                }
                context.waitTicks(5)
            }
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
            context.waitTicks(30)
            context.takeScreenshot("justiciar-whip-answers")

            // The needle: the eyes burn white for the charge, then one straight pixel.
            context.waitTicks(200)
            // No one to look at: with an enemy already in the crosshair the needle goes at once and the eyes never burn.
            server.runCommand("kill @e[type=husk]")
            context.waitTicks(30)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            cast(server, "justiciar_psychic_piercing")
            context.waitTicks(6)
            assertEyes(context, EyeGlow.WHITE)
            context.takeScreenshot("justiciar-piercing-charge")
            // The effects are held from before the target shows up, so the first frame is the bolts' first tick. Then
            // the caster turns, so the bolts out of the eyes and the spirit knocked back are seen side-on.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            context.runOnClient<Throwable> { WorldEffects.held = true }
            husks(server, 4.0)
            context.waitTicks(4)
            server.runCommand("tp @a ~ ~ ~ ~60 ~")
            context.waitTicks(2)
            heldSeries(context, "justiciar-piercing", WorldEffectPayload.Lash.Shape.NEEDLE.life)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }

            // The bonds: three chains out of the ground, coiling and snapping tight one after another, red-hot, then
            // cooling iron, then falling apart at the end of the term. Held between shots, like the whip: tick by
            // tick through the coiling, once mid-term, and every other tick through the breaking.
            context.waitTicks(100)
            villager(server)
            cast(server, "justiciar_brand_of_restraint")
            var age = 0
            for (at in (1..15) + listOf(40) + (106..120 step 2)) {
                context.waitTicks(at - age)
                age = at
                context.runOnClient<Throwable> { WorldEffects.held = true }
                context.takeScreenshot("justiciar-brand-$at")
                context.runOnClient<Throwable> { WorldEffects.held = false }
            }
            server.runCommand("time set midnight")
            context.waitTicks(40)
            villager(server)
            cast(server, "justiciar_brand_of_restraint")
            context.waitTicks(40)
            context.takeScreenshot("justiciar-brand-iron-night")
            server.runCommand("time set noon")

            // The charged weapon: lightning over the sword in hand, first person and from behind; then a real blow,
            // and the arc that jumps off the blade onto the husk.
            context.waitTicks(160)
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword")
            husks(server, 2.5)
            context.waitTicks(5)
            cast(server, "justiciar_psychic_lashing")
            context.waitTicks(4)
            heldSeries(context, "justiciar-lashing-coat-first", 3)
            // From behind and turned away from the husk, so the sword in the right hand is not hidden by the body.
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
            server.runCommand("tp @a ~ ~ ~ ~-70 ~")
            context.waitTicks(10)
            heldSeries(context, "justiciar-lashing-coat-third", 3)
            husks(server, 2.5)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
            context.waitTicks(5)
            context.runOnClient<Throwable> { client ->
                val player = client.player!!
                val husk = client.level!!.entitiesForRendering().filter { it.type == EntityTypes.HUSK }.minBy { it.distanceTo(player) }
                client.gameMode!!.attack(player, husk)
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
                WorldEffects.held = true
            }
            context.waitTicks(3)
            heldSeries(context, "justiciar-lashing-arc", WorldEffectPayload.Lash.Shape.BOLT.life)
            server.runCommand("kill @e[type=husk]")
        }
    }

    /**
     * [count] screenshots one tick apart, the effects held while each is taken (a screenshot takes longer than a
     * tick). Leaves them running.
     */
    private fun heldSeries(context: ClientGameTestContext, name: String, count: Int) {
        for (shot in 0 until count) {
            context.runOnClient<Throwable> { WorldEffects.held = true }
            context.takeScreenshot("$name-$shot")
            context.runOnClient<Throwable> { WorldEffects.held = false }
            context.waitTick()
        }
    }

    /** Husks that stand still, [ahead] blocks in front of the player, who looks at the nearest. */
    private fun husks(server: TestServerContext, ahead: Double) {
        server.runCommand("kill @e[type=husk]")
        server.runCommand("summon husk ~ ~ ~$ahead {NoAI:1b}")
        server.runCommand("summon husk ~3 ~ ~${ahead + 2} {NoAI:1b}")
        server.on { srv, player ->
            srv.overworld().getEntities(EntityTypes.HUSK, player.boundingBox.inflate(12.0)) { true }
                .minByOrNull { it.distanceTo(player) }?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, it.eyePosition) }
        }
    }

    /** A villager that stands still three blocks ahead: nothing to smoke or burn, so the hoops are what shows. */
    private fun villager(server: TestServerContext) {
        server.runCommand("kill @e[type=villager]")
        server.runCommand("summon villager ~ ~ ~3 {NoAI:1b}")
        server.on { srv, player ->
            srv.overworld().getEntities(EntityTypes.VILLAGER, player.boundingBox.inflate(12.0)) { true }
                .firstOrNull()?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, it.eyePosition) }
        }
    }

    /** The client's own view of the eyes: what the attachment's sync actually delivered. */
    private fun assertEyes(context: ClientGameTestContext, glow: EyeGlow) =
        context.runOnClient<Throwable> { val seen = it.player!!.getAttached(Eyes.GLOW); check(seen == glow) { "Очі гравця на клієнті: $seen, а мали б $glow" } }

    /** Draws [shape] from the player to the [target]th husk the client knows, as if the server had just sent it. */
    private fun lash(context: ClientGameTestContext, shape: WorldEffectPayload.Lash.Shape, target: Int) =
        context.runOnClient<Throwable> { client ->
            val player = client.player!!
            val husks: List<Entity> = client.level!!.entitiesForRendering().filter { it.type == EntityTypes.HUSK }.toList()
            val to = husks.getOrNull(target) ?: error("На клієнті немає шашки №$target")
            Lashes.accept(WorldEffectPayload.Lash(player.id, to.id, shape, 0xEBC38E))
        }

    /** The request a key press sends, with a full tank and no cooldown in the way. */
    private fun cast(server: TestServerContext, identity: String) = server.on { _, player ->
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: error("Гравець не Потойбічний")
        AbilityServices.cooldowns.forget(player.uuid)
        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
        AbilityRequests.cast(player, AbilityIdentity.of(identity))
    }

    private fun TestServerContext.on(block: (MinecraftServer, ServerPlayer) -> Unit) =
        runOnServer<Throwable> { srv -> block(srv, srv.playerList.players.first()) }
}
