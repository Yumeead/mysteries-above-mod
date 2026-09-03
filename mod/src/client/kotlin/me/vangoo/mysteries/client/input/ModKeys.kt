package me.vangoo.mysteries.client.input

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CastRequestPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

/**
 * The mod's keys.
 *
 * One key, two actions: **press and release — cast**, **hold — menu**. The vanilla inventory of
 * the mobile build behaves the same way, as does half the mods with an ability wheel, and for
 * the same reason: in a fight the hand must not go looking for a second key, and a menu is not
 * wanted in a fight.
 *
 * ## Why not "cast on R, menu on key X"
 *
 * That is how it was, and that is exactly what the flaw grew out of: the menu opened
 * **together** with the cast. Not because of the key — because the server's cast path sent
 * `BEYONDER_STATUS`, and the client opens a screen on that packet. The keys are only half the
 * fix; the other half is in `AbilityRequests`, which no longer sends a status snapshot after
 * every blow.
 *
 * ## The decision is made on release, not on press
 *
 * Otherwise "held" cannot be told from "pressed": at the moment of the press it is not yet known
 * which it will become. So the cast happens on release, if the key was held for less than
 * [HOLD_TICKS] ticks — a quarter of a second, i.e. less than a person holds deliberately and
 * more than an ordinary click lasts.
 *
 * On 26.2 a key category is not a string but a `KeyMapping.Category` with its own `Identifier`;
 * its caption comes from the `key.category.<namespace>.<path>` key.
 */
object ModKeys {

    private val CATEGORY = KeyMapping.Category.register(modId("abilities"))

    private val CAST: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY),
    )

    /** How many ticks the key has been held down. Zero — released. */
    private var heldTicks = 0

    /** The menu has already been opened by this hold: releasing after it must not cast. */
    private var opened = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            // The click queue must be drained, otherwise it piles up and one day spills out a
            // batch of casts. The decision itself is made from [heldTicks], not from the queue.
            @Suppress("ControlFlowWithEmptyBody")
            while (CAST.consumeClick()) { }

            if (CAST.isDown) {
                heldTicks++
                if (heldTicks >= HOLD_TICKS && !opened) {
                    opened = true
                    // The server opens the screen in its reply: only it knows the roster.
                    ModChannels.STATUS_REQUEST.sendToServer(StatusRequestPayload)
                }
                return@register
            }

            // The key was released. A cast only if this was a click, not a hold.
            if (heldTicks in 1 until HOLD_TICKS && !opened) {
                ModChannels.CAST_REQUEST.sendToServer(CastRequestPayload.INSTANCE)
            }
            heldTicks = 0
            opened = false
        }
    }

    /** A quarter of a second: longer than a click, shorter than a deliberate hold. */
    private const val HOLD_TICKS = 5
}
