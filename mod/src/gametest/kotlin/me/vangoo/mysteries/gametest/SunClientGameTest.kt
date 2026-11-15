package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.effect.worldText
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.ChatFormatting
import net.minecraft.client.CameraType
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Sun's powers as a player sees them, cast for real through the same request a key press
 * sends: the beam, the gathering in the sky and the pillar, the fire, the wave, the stamp, the
 * holy water — and, from the front, everything that rides the caster: the halo, the small sun,
 * the burning fists, the suns in the eyes, the sparks of the consecrated blade.
 *
 * At night, because that is where light is judged. The screenshots land in
 * `mod/build/run/clientGameTest/screenshots`.
 */
@Suppress("UnstableApiUsage")
class SunClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("weather clear")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-10 ~-1 ~-4 ~10 ~-1 ~16 minecraft:stone_bricks")
            server.runCommand("tp @a ~ ~ ~ 0 0")
            server.runCommand("mysteries become Sun 5 @a")
            server.runCommand("time set midnight")
            context.waitTicks(10)

            husk(server)
            cast(server, "sun_sunshine")
            // The light flares in over its first ticks: at two it has barely begun to show.
            context.waitTicks(4)
            context.takeScreenshot("sun-sunshine")

            context.waitTicks(10)
            // The pillar and the fire take ground: aimed at the husk's feet, not its eyes.
            husk(server, feet = true)
            cast(server, "sun_holy_light_summoning")
            context.waitTicks(5)
            context.takeScreenshot("sun-holy-light-gather")
            context.waitTicks(5)
            context.takeScreenshot("sun-holy-light-strike")

            context.waitTicks(30)
            husk(server, feet = true)
            cast(server, "sun_fire_of_light")
            context.waitTicks(20)
            context.takeScreenshot("sun-fire-of-light")

            context.waitTicks(150)
            husk(server)
            server.on { _, player -> player.isShiftKeyDown = true }
            cast(server, "sun_notarization")
            context.waitTicks(3)
            context.takeScreenshot("sun-notarization")
            server.on { _, player -> player.isShiftKeyDown = false }

            server.runCommand("kill @e[type=husk]")
            context.waitTicks(25)
            cast(server, "sun_holy_wave")
            context.waitTicks(7)
            context.takeScreenshot("sun-holy-wave")

            context.waitTicks(20)
            server.runCommand("give @a minecraft:potion[minecraft:potion_contents={potion:\"minecraft:water\"}]")
            cast(server, "sun_holy_water")
            // No frame of the kindling: while the client takes a screenshot the integrated server
            // ticks on, so a half-second window is already over by the time the next frame is shot.
            context.waitTicks(12)
            context.takeScreenshot("sun-holy-water-splash")

            context.waitTicks(20)
            server.on { srv, player ->
                val at = player.getEyePosition(1f).add(0.0, 0.2, 4.0)
                val text = Component.literal("Мир · 60 с\nприйняти — присісти й подивитися на нотаріуса").withStyle(ChatFormatting.GOLD)
                worldText(AbilityServices.scheduling, srv.overworld(), at, text, 100)
            }
            context.waitTicks(5)
            context.takeScreenshot("sun-world-text")

            // By day, before anything rides the caster: from the eye, it would fill the frame.
            byDay(context, server)
            server.runCommand("time set midnight")

            // Everything that rides the caster, seen from the front.
            context.waitTicks(100)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_FRONT) }
            for (id in listOf("sun_halo", "sun_daytime", "sun_night_vision", "sun_holy_oath", "sun_cleave_of_purification")) {
                cast(server, id)
            }
            context.waitTicks(30)
            context.takeScreenshot("sun-self")
            // The toggles go out the way they came on.
            for (id in listOf("sun_halo", "sun_daytime", "sun_night_vision")) cast(server, id)
            context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
        }
    }

    /**
     * By day the light comes from the sun: the pillar slants in from it, and looked up along, the
     * shaft runs into the sun's disc. The caster's own ray seen from the eye, and the oath's shaft.
     */
    private fun byDay(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("time set 2000")
        context.waitTicks(40)
        husk(server, feet = true)
        cast(server, "sun_holy_light_summoning")
        context.waitTicks(12)
        context.takeScreenshot("sun-holy-light-morning")
        server.on { srv, player -> player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition(1f).add(toSun(srv).scale(100.0))) }
        context.waitTicks(1)
        context.takeScreenshot("sun-holy-light-into-the-sun")

        context.waitTicks(40)
        husk(server)
        cast(server, "sun_sunshine")
        context.waitTicks(4)
        context.takeScreenshot("sun-sunshine-day")

        context.waitTicks(20)
        server.runCommand("kill @e[type=husk]")
        context.runOnClient<Throwable> { it.options.setCameraType(CameraType.THIRD_PERSON_BACK) }
        server.runCommand("tp @a ~ ~ ~ 0 0")
        cast(server, "sun_holy_oath")
        context.waitTicks(5)
        context.takeScreenshot("sun-holy-oath-morning")
        context.runOnClient<Throwable> { it.options.setCameraType(CameraType.FIRST_PERSON) }
    }

    /** The way to the sun as the sky places it — the same sum the pathway does. */
    private fun toSun(server: MinecraftServer): Vec3 {
        val angle = Math.toRadians(server.overworld().environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE).toDouble())
        return Vec3(-sin(angle), cos(angle), 0.0)
    }

    /** A husk six blocks ahead that stands still — the unclean every strike is aimed at, at its eyes or its feet. */
    private fun husk(server: TestServerContext, feet: Boolean = false) {
        server.runCommand("kill @e[type=husk]")
        server.runCommand("summon husk ~ ~ ~6 {NoAI:1b}")
        server.on { srv, player ->
            srv.overworld().getEntities(EntityTypes.HUSK, player.boundingBox.inflate(10.0)) { true }
                .firstOrNull()?.let { player.lookAt(EntityAnchorArgument.Anchor.EYES, if (feet) it.position() else it.eyePosition) }
        }
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
