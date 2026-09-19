package me.vangoo.mysteries.client.input

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CastRequestPayload
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import kotlin.math.abs
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.KeyMapping
import net.minecraft.world.InteractionResult
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

    /**
     * Fool, Seq. 5: hold it and your hands are on the threads.
     *
     * A held key rather than a screen, and the whole reason the marionettes needed no menu: while
     * it is down the mouse wheel steps along the row of puppets on the HUD, right-click gives the
     * one you picked an order, and sneak plus left-click cuts its thread. Let go and the mouse is
     * a mouse again.
     */
    private val THREADS: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.threads", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY),
    )

    /** How many ticks the key has been held down. Zero — released. */
    private var heldTicks = 0

    /** The menu has already been opened by this hold: releasing after it must not cast. */
    private var opened = false

    /** The hotbar slot the wheel was stolen from, to be put back when the threads are let go. */
    private var borrowedSlot = -1

    private var sentYaw = Float.NaN
    private var sentPitch = Float.NaN

    fun register() {
        // START, not END: vanilla drains the click queues inside `Minecraft.tick()`, so a handler
        // at the end of the tick would find `consumeClick()` already false every time. The threads
        // get first refusal on the mouse, and hand it back the moment the key is released.
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            threads(client)
            drive(client)
        }

        // Consuming the click stops a *click*; holding the button is a separate path through
        // `Minecraft.handleKeybinds`, so the interactions themselves are refused too. All five are
        // ready Fabric hooks — step one of the search order, and no mixin in sight.
        UseBlockCallback.EVENT.register { _, _, _, _ -> refuse() }
        UseItemCallback.EVENT.register { _, _, _ -> refuse() }
        UseEntityCallback.EVENT.register { _, _, _, _, _ -> refuse() }
        AttackBlockCallback.EVENT.register { _, _, _, _, _ -> refuse() }
        AttackEntityCallback.EVENT.register { _, _, _, _, _ -> refuse() }

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

    /**
     * Thread mode: the row of puppets is on screen, and the mouse belongs to it.
     *
     * ## The wheel is read through the hotbar, not through a scroll hook
     *
     * Vanilla turns a wheel notch into a hotbar slot change before anything the mod could hook,
     * and Fabric offers no scroll event. So the slot change **is** the notch: the difference is
     * measured, sent as a step along the row, and the slot is put straight back. The player sees
     * their hotbar stand still. `WindFlight` and the spirit world already steer by the selected
     * slot for the same reason — the wheel is server-visible input that costs no packet of ours.
     */
    private fun threads(client: net.minecraft.client.Minecraft) {
        val player = client.player
        if (player == null || !ClientMarionettes.hasThreads) {
            borrowedSlot = -1
            return
        }

        if (!THREADS.isDown) {
            borrowedSlot = -1
            return
        }

        val slot = player.inventory.selectedSlot
        if (borrowedSlot < 0) {
            borrowedSlot = slot
        } else if (slot != borrowedSlot) {
            // Shortest way round the nine slots: scrolling one notch off slot 0 lands on slot 8,
            // and that is one step back along the row, not eight forward.
            val raw = slot - borrowedSlot
            val step = when {
                raw > HOTBAR_SLOTS / 2 -> raw - HOTBAR_SLOTS
                raw < -HOTBAR_SLOTS / 2 -> raw + HOTBAR_SLOTS
                else -> raw
            }
            player.inventory.selectedSlot = borrowedSlot
            ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload.cycle(step))
        }

        // Right-click: an order. The server reads the crosshair and the sneak and picks the verb.
        if (client.options.keyUse.consumeClick()) {
            ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload(MarionetteOrderPayload.Action.ORDER))
        }
        // Sneak plus left-click: cut the thread. The sneak is the confirmation — a puppet does not
        // get to die to a stray click.
        if (client.options.keyAttack.consumeClick() && player.isShiftKeyDown) {
            ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload(MarionetteOrderPayload.Action.CUT))
        }
    }

    /**
     * While possessing: the head and the fist, which are the two things vanilla stops sending.
     *
     * With the camera on a puppet the client keeps sending the movement **keys** (the server reads
     * them as `ServerPlayer.lastClientInput`) but not the rotation — `LocalPlayer.sendPosition`
     * sits behind `isControlledCamera`. So the rotation comes down the mod's own channel, and only
     * while it changes. The blow goes the same way because vanilla's own attack is measured from
     * the body, standing wherever it was left, and the server drops it on reach.
     */
    private fun drive(client: net.minecraft.client.Minecraft) {
        val player = client.player
        if (player == null || ClientMarionettes.possessing == null) {
            sentYaw = Float.NaN
            return
        }

        if (sentYaw.isNaN() || abs(player.yRot - sentYaw) > AIM_EPSILON || abs(player.xRot - sentPitch) > AIM_EPSILON) {
            sentYaw = player.yRot
            sentPitch = player.xRot
            ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload.look(sentYaw, sentPitch))
        }

        if (client.options.keyAttack.consumeClick()) {
            ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload(MarionetteOrderPayload.Action.STRIKE))
        }
    }

    /**
     * Whether the world should ignore this hand right now.
     *
     * Two cases, and they are the same case: the hands are on the threads, or the hands are a
     * hundred blocks away inside a puppet. Either way a click means something other than "put a
     * block here", and vanilla must not act on it.
     */
    private fun refuse(): InteractionResult =
        if (THREADS.isDown || ClientMarionettes.possessing != null) InteractionResult.FAIL else InteractionResult.PASS

    /** A quarter of a second: longer than a click, shorter than a deliberate hold. */
    private const val HOLD_TICKS = 5

    private const val HOTBAR_SLOTS = 9

    /** Half a degree: below what a hand can hold still, above what a packet a tick is worth. */
    private const val AIM_EPSILON = 0.5f
}
