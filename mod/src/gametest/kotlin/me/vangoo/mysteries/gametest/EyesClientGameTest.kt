package me.vangoo.mysteries.gametest

import com.mojang.blaze3d.platform.NativeImage
import me.vangoo.mysteries.client.render.PuppetSkins
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin

/**
 * Glowing eyes in the eyes a skin actually has (`SkinEyes`): Steve as he is, Steve with tall narrow eyes, with big
 * two-colour eyes, and with sunglasses drawn on the second layer. The player is put in each skin made in place (the
 * offline client fetches none), the Arbiter's gold is lit in their eyes, and the camera looks them in the face at night.
 * Run with `-Ponly=eyes`.
 */
@Suppress("UnstableApiUsage")
class EyesClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set midnight")
            server.runCommand("gamemode creative @a")
            server.runCommand("tp @a 0.5 -60 0.5 0 0")
            server.runOnServer<Throwable> { Eyes.light(it.playerList.players.first(), EyeGlow.GOLD) }
            context.runOnClient<Throwable> {
                it.options.setCameraType(CameraType.THIRD_PERSON_FRONT)
                it.options.fov().set(30)
            }
            context.waitTicks(20)

            for ((name, paint) in SKINS) {
                context.runOnClient<Throwable> { client -> dress(client, name, paint) }
                context.waitTicks(10)
                context.takeScreenshot("eyes-$name")
            }
            context.runOnClient<Throwable> { client -> PuppetSkins.dress(client.player!!.uuid, null) }
        }
    }

    /** Steve's own skin with [paint] over it, registered and worn by the local player. */
    private fun dress(client: Minecraft, name: String, paint: (NativeImage) -> Unit) {
        val image = client.resourceManager.open(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")).use { NativeImage.read(it) }
        paint(image)
        val id = Identifier.fromNamespaceAndPath("mysteries_above_gametest", "skin/$name")
        client.textureManager.register(id, DynamicTexture({ id.toString() }, image))
        PuppetSkins.dress(client.player!!.uuid, PlayerSkin(ClientAsset.ResourceTexture(id, id), null, null, PlayerModelType.WIDE, false))
    }

    private companion object {
        const val WHITE = 0xFFF5F5F5.toInt()
        const val BLUE = 0xFF2850C8.toInt()
        const val SHADES = 0xFF101010.toInt()

        /** Steve's face cell ([c], [r] within the 8×8 front of the head) on the first layer, or the second. */
        fun NativeImage.face(c: Int, r: Int, argb: Int, over: Boolean = false) = setPixel((if (over) 40 else 8) + c, 8 + r, argb)

        /** Steve's own eyes painted over with his skin, so only the new ones are eyes. */
        fun NativeImage.blind() {
            val skin = getPixel(8 + 3, 8 + 5)
            for (c in listOf(1, 2, 5, 6)) face(c, 4, skin)
        }

        val SKINS: List<Pair<String, (NativeImage) -> Unit>> = listOf(
            "steve" to { _ -> },
            "tall" to { image ->
                image.blind()
                for (r in 2..4) {
                    image.face(2, r, if (r < 4) WHITE else BLUE)
                    image.face(5, r, if (r < 4) WHITE else BLUE)
                }
            },
            "big" to { image ->
                image.blind()
                for (r in 2..4) for ((c, colour) in listOf(1 to WHITE, 2 to BLUE, 5 to BLUE, 6 to WHITE)) image.face(c, r, colour)
            },
            "glasses" to { image ->
                for (c in 0..7) image.face(c, 4, SHADES, over = true)
                image.face(0, 3, SHADES, over = true)
                image.face(7, 3, SHADES, over = true)
            },
        )
    }
}
