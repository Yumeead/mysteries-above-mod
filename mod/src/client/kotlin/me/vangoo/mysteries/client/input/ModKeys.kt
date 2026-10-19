package me.vangoo.mysteries.client.input

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.mysteries.MOD_ID
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.client.AbilityGroup
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.client.screen.AbilityBookScreen
import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import org.lwjgl.glfw.GLFW

/**
 * The mod's keys.
 *
 * One key, two actions: **press and release — cast**, **hold — the ability wheel**. The vanilla inventory of
 * the mobile build behaves the same way, as does half the mods with an ability wheel, and for
 * the same reason: in a fight the hand must not go looking for a second key, and a menu is not
 * wanted in a fight.
 *
 * ## Why not "cast on R, menu on key X"
 *
 * That is how it was, and the menu kept opening **together** with the cast: a server packet
 * after every blow unfolded a screen. One key with two gestures leaves no second key to reach
 * for and no packet that opens anything.
 *
 * ## The decision is made on release, not on press
 *
 * Otherwise "held" cannot be told from "pressed": at the moment of the press it is not yet known
 * which it will become. So the cast happens on release, if the key was held for less than
 * `ClientOptions.wheelHoldMillis` (180 ms by default) — i.e. less than a person holds deliberately and
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

    /** Step through the abilities without a screen: the fight-time path, where freezing is not an option. */
    private val NEXT: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
    )

    private val PREV: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
    )

    /** Unbound: a rare action must not take a key from every player. */
    private val TOGGLE_HUD: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, CATEGORY),
    )

    /** The ability book: descriptions, groups and hotkeys. */
    private val BOOK: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY),
    )

    /** Hotkeys of the active group that were down on the previous tick: a cast fires on the press edge. */
    private val heldHotkeys = HashSet<String>()

    /** The mode to come back to when the HUD key shows the panel again. */
    private var shownHud = HudMode.AUTO

    /** How many ticks the key has been held down. Zero — released. */
    private var heldTicks = 0

    /** The menu has already been opened by this hold: releasing after it must not cast. */
    private var opened = false

    /** The hotbar slot the wheel was stolen from, to be put back when the threads are let go. */
    private var borrowedSlot = -1

    /** The puppet the local player is inside, hidden from its own driver; see [inside]. */
    private var worn: MarionetteEntity? = null

    fun register() {
        // START, not END: vanilla drains the click queues inside `Minecraft.tick()`, so a handler
        // at the end of the tick would find `consumeClick()` already false every time. The threads
        // get first refusal on the mouse, and hand it back the moment the key is released.
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            threads(client)
            inside(client)
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

            quickKeys(it)

            if (CAST.isDown) {
                heldTicks++
                if (heldTicks >= holdTicks() && !opened) {
                    opened = true
                    // Straight from the client cache — no round trip. The wheel takes over the key
                    // from here: its own `keyReleased` sends the cast, so this tick must not.
                    if (ClientAbilityState.entries.isNotEmpty()) {
                        Minecraft.getInstance().gui.setScreen(AbilityWheelScreen(CAST))
                    }
                }
                return@register
            }

            // The key was released. A cast only if this was a click, not a hold.
            if (heldTicks in 1 until holdTicks() && !opened) {
                ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload.castSelected())
            }
            heldTicks = 0
            opened = false
        }
    }

    /** Z/X, the book, the HUD key and the group hotkeys. Click queues only fill while no screen is open. */
    private fun quickKeys(client: Minecraft) {
        var step = 0
        while (NEXT.consumeClick()) step++
        while (PREV.consumeClick()) step--
        // Several presses in one tick become one request: the echo of the first has not arrived yet.
        if (step != 0) {
            val ids = visible().filter { it.type != AbilityType.PERMANENT_PASSIVE }.map { it.identity }
            ClientAbilityState.cycle(ids, ClientAbilityState.selected, step)?.let {
                ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(Action.SELECT, it))
            }
        }

        var book = false
        while (BOOK.consumeClick()) book = true
        // HUD settings remain available before choosing a pathway.
        if (book && client.gui.screen() == null && client.player != null) {
            client.gui.setScreen(AbilityBookScreen())
        }

        hotkeys(client)

        while (TOGGLE_HUD.consumeClick()) {
            if (ClientOptions.hud == HudMode.NEVER) {
                ClientOptions.hud = shownHud
            } else {
                shownHud = ClientOptions.hud
                ClientOptions.hud = HudMode.NEVER
            }
        }
    }

    /**
     * The active group's hotkeys. They are the player's own keys rather than `KeyMapping`s (any
     * number, per group), so there is no click queue: the key state is read once a tick and a cast
     * fires on the press edge. Nothing fires while a screen is open — chat included.
     *
     * ponytail: polled at 20 Hz, so a tap shorter than one tick (50 ms) can be missed; a GLFW key
     * callback hook would catch it if that ever shows up in play.
     */
    private fun hotkeys(client: Minecraft) {
        if (client.gui.screen() != null || client.player == null || !client.isWindowActive) {
            heldHotkeys.clear()
            return
        }
        val window = client.window
        for ((name, target) in ClientOptions.group.hotkeys) {
            val identity = AbilityGroup.abilityOf(target)
            val key = InputConstants.getKey(name)
            val down = when (key.type) {
                InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.value) == GLFW.GLFW_PRESS
                else -> InputConstants.isKeyDown(window, key.value)
            }
            if (!down) {
                heldHotkeys.remove(name)
            } else if (heldHotkeys.add(name) && ClientAbilityState.entry(identity) != null) {
                use(identity, AbilityGroup.modeOf(target))
            }
        }
    }

    fun isBookKey(event: KeyEvent): Boolean = BOOK.matches(event)

    /** The active group's abilities the player holds right now, in roster order. */
    fun visible(group: AbilityGroup = ClientOptions.group): List<AbilityRosterPayload.Entry> =
        ClientAbilityState.entries.filter { group.contains(it.identity) }

    /** The active group's hotkey label for [identity] (or one [mode] of it), or `null` if it has none. */
    fun hotkeyLabel(identity: String, mode: String? = null, group: AbilityGroup = ClientOptions.group): Component? =
        group.keyOf(AbilityGroup.target(identity, mode))?.let { InputConstants.getKey(it).displayName }

    /**
     * Use one ability straight away — a hotkey or the wheel's release.
     *
     * A toggle is flipped with `TOGGLE` rather than selected: it is set and forgotten, and taking
     * the cast key's place would leave the player tapping a switch mid-fight. A permanent passive
     * has nothing to use. An identity the roster no longer knows still goes out: the server says
     * `NotOwned`, the client decides nothing. [mode] — cast in that mode; `null` — in the picked one.
     */
    fun use(identity: String, mode: String? = null) {
        val action = when (ClientAbilityState.entry(identity)?.type) {
            AbilityType.PERMANENT_PASSIVE -> return
            AbilityType.TOGGLEABLE_PASSIVE -> Action.TOGGLE
            else -> Action.SELECT_AND_CAST
        }
        ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(action, identity, mode))
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
     * While possessing: the puppet the local player is standing inside is theirs to wear, not to
     * look at. It is marked so it is neither drawn around the camera nor caught by the crosshair
     * (`MarionetteEntity.hiddenForDriver`), and the mark comes off the moment they leave.
     */
    private fun inside(client: net.minecraft.client.Minecraft) {
        val puppet = ClientMarionettes.possessing?.let { client.level?.getEntity(it) } as? MarionetteEntity
        if (puppet === worn) return
        worn?.hiddenForDriver = false
        puppet?.hiddenForDriver = true
        worn = puppet
    }

    /**
     * Whether the world should ignore this hand right now: the hands are on the threads, and a
     * click means an order rather than "put a block here".
     */
    private fun refuse(): InteractionResult =
        if (THREADS.isDown) InteractionResult.FAIL else InteractionResult.PASS

    /** `wheelHoldMillis` in whole ticks, at least one: longer than a click, shorter than a deliberate hold. */
    private fun holdTicks(): Int = maxOf(1, (ClientOptions.wheelHoldMillis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK)

    private const val MILLIS_PER_TICK = 50

    private const val HOTBAR_SLOTS = 9
}
