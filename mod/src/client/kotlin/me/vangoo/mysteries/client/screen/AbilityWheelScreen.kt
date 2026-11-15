package me.vangoo.mysteries.client.screen

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.client.hud.SpiritualityHud
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import me.vangoo.mysteries.net.payload.MarionetteStatePayload
import me.vangoo.mysteries.entity.MarionetteEntity
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The ability wheel: hold the cast key, point, let go — and the card under the cursor is now the
 * one the cast key fires. The wheel **picks, it does not cast** (the player's decision): a tap of
 * the key casts what was picked, so choosing in calm and striking later are two separate moves.
 *
 * It lives while [key] is held. Releasing it sends `SELECT` for the card under the cursor (a
 * toggle too — tapping the key will flip it), or nothing from the dead centre.
 *
 * **Pinning.** Held past 1.5 s, a thin ring round the centre starts to fill; at 4 s it turns gold,
 * clicks, and the wheel is pinned: letting go no longer closes it, a click on a card picks that card
 * and the wheel stays, and the cast key (pressed and let go) or Escape closes it. The pin is for
 * sorting out a loadout in calm, not for a fight: a fight never holds the key for four seconds.
 *
 * **Walking.** The wheel hands the movement keys back to the world every tick, so the player walks,
 * jumps, sneaks and sprints with it open; the camera stays still, as the mouse is on the wheel.
 * Vanilla reads those keys every tick whatever screen is open — the screen only stopped them by
 * never setting them — and on close `grabMouse` re-reads every key, so nothing stays stuck down.
 *
 * It shows the active group. The mouse wheel walks the pages of every group in turn (a group of
 * more than twelve takes several), and the group of the page it stops on becomes the active one —
 * its hotkeys with it. Groups and keys are set up in [AbilityBookScreen]; the wheel only uses them. The screen closes at once; the
 * HUD shows the new pick when `ABILITY_STATE` brings it back.
 *
 * The client dims what it thinks is unavailable but never withholds the request — the server
 * decides. The one exception is a permanent passive, which has nothing to pick at all.
 *
 * Built from [ClientAbilityState]'s cache, so opening it costs no round trip. Cards are rebuilt
 * on the tick (cooldown seconds change at most 20 times a second), the frame only reads them.
 *
 * Two motions, both on the client tick plus `partialTick` rather than a wall clock: the wheel
 * opens from 0.88 to full size in 100 ms, and the card under the cursor grows to 1.08 in 80 ms.
 * There is no closing motion on purpose: a screen lingering after the key is up would swallow the
 * next tap of it. `reducedMotion` turns both off.
 *
 * **Modes.** A card whose ability has several ways of use (the Underworld door's four) fans them out
 * the moment it is hovered: a band unfolds round the outside of the ring from the card, one slice per
 * mode, each with its name on a row of its own beside the band ([WheelLayout.arc]: the rows curve with
 * the ring and never share a line). Past the ring the cursor reads the arc, inside it the cards, so
 * the way out to the modes never drops the card they belong to. Releasing on a row picks the ability
 * in that mode; releasing on the card keeps the mode already picked. The rest of the ring's marks fade
 * while the arc is open; an arc on the left takes the side panel's place. Without a mouse, ↑/↓ walk
 * the rows of a card that has them (and turn the page of one that does not).
 *
 * **Threads.** A Marionettist holding puppets gets a second ring beside this one: an arrow card stands
 * off the ring's right side, and resting the cursor on it (or a click, or Tab) turns to the puppets, drawn
 * as their own bodies seen three-quarters on. The same arc opens from a puppet, this time of
 * what it can be told: come, follow, stay, guard, strike your target, press the block you were
 * looking at, speak, swap places, possess, cut. The wheel always opens on the abilities: a fight must
 * not land on the puppets by accident. A body this client does not have (too far) is drawn from what
 * the server says of it.
 *
 * **The side panel.** Whatever the cursor rests on is read at the left of the screen, away from the
 * ring: an ability's name, mode, Sequence and description; a puppet's name, what it was, its health,
 * how far and which way it stands, what it is doing. Nothing that only explains the wheel itself.
 */
class AbilityWheelScreen(private val key: KeyMapping) : Screen(Component.translatable("wheel.$MOD_ID.title")) {

    private class Card(
        val entry: AbilityRosterPayload.Entry,
        val dx: Int,
        val dy: Int,
        /** The cost numbers; [spirit] — they are spirituality, so the icon goes in front. */
        val price: Component?,
        val spirit: Boolean,
        val critical: Boolean,
        val selected: Boolean,
        val marker: Component?,
        val cooldown: Component?,
        val remaining: Float,
        val dim: Boolean,
        val slot: Component?,
        /** The Sequence number and where it stands: just outside the card, like an hour mark. */
        val numeral: String?,
        val numeralX: Int,
        val numeralY: Int,
    )

    /** One ring: up to twelve abilities of one group. */
    private class Page(val group: Int, val entries: List<AbilityRosterPayload.Entry>, val label: Component?)

    private var entries: List<AbilityRosterPayload.Entry> = ClientAbilityState.entries
    private var pages: List<Page> = pagesOf()
    private var page = startPage()
    private var cards: List<Card> = emptyList()
    private var hovered = -1
    private var caption: List<FormattedCharSequence> = emptyList()
    private var pageLabel: Component? = null
    private var accent = UiTheme.ACCENT

    /** Client ticks since the wheel opened, and the moment (in the same ticks) the hover last moved. */
    private var age = 0
    private var hoverSince = 0f

    /** The hovered card's arc of sub-choices, their names, and how late each unfolds; empty: it has none. */
    private var arc: List<WheelLayout.Slice> = emptyList()
    private var arcLabels: List<Component> = emptyList()
    private var arcDelays: FloatArray = FloatArray(0)
    /** The row of the mode already picked; `-1` for none (and on the threads ring). */
    private var arcPicked = -1
    private var hoveredMode = -1

    private val centerX get() = width / 2
    private val centerY get() = height / 2

    /** The player's own menu blur, borrowed while the wheel eases it in; `-1` — not read yet. */
    private var ownBlur = -1

    /** Held long enough: the wheel outlives the key. [released] — the key has been let go since. */
    private var pinned = false
    private var released = false

    private enum class Tab { ABILITIES, THREADS }

    /** The wheel's verbs for a puppet, in the column's order: errands first, the thread itself last. */
    private enum class Verb(val action: MarionetteOrderPayload.Action) {
        COME(MarionetteOrderPayload.Action.COME),
        FOLLOW(MarionetteOrderPayload.Action.FOLLOW),
        STAND(MarionetteOrderPayload.Action.STAND),
        GUARD(MarionetteOrderPayload.Action.GUARD),
        ATTACK(MarionetteOrderPayload.Action.ATTACK),
        USE(MarionetteOrderPayload.Action.USE),
        SPEAK(MarionetteOrderPayload.Action.SPEAK),
        SWAP(MarionetteOrderPayload.Action.SWAP),
        POSSESS(MarionetteOrderPayload.Action.POSSESS),
        CUT(MarionetteOrderPayload.Action.CUT),
    }

    private var tab = Tab.ABILITIES

    /** Where each puppet stands on the ring, from the centre. */
    private var threadCards: List<Pair<Int, Int>> = emptyList()

    /** Stand-in bodies for puppets this client has no entity for, by puppet. */
    private val bodies = HashMap<UUID, LivingEntity>()

    /** When the cursor came onto the current pill — cutting a thread wants a second of it. */
    private var verbSince = 0f

    /** Set when "Speak" was picked: the wheel gives way to the line's text box. */
    private var speakTo: MarionetteStatePayload.Entry? = null

    /** The puppet under the cursor, laid out for the side panel; `null` when none is. */
    private class PuppetInfo(
        val entry: MarionetteStatePayload.Entry,
        val name: Component,
        /** What it was; `null` when that is all its name says (a pig called Pig). */
        val kind: Component?,
        val health: Component,
        /** Blocks away, or "another world" with no dial beside it. */
        val distance: Component,
        val order: Component,
        val note: Component?,
    )

    private var puppetInfo: PuppetInfo? = null

    /** The cursor rests on the arrow card to the other ring, and since when. */
    private var switchHover = false
    private var switchSince = 0f

    /**
     * The block the crosshair held as the wheel opened, out to a gesture's reach: what "use" presses.
     * Once the wheel is up, the world behind it cannot be pointed at.
     */
    private val openedOn: BlockPos? = Minecraft.getInstance().player
        ?.pick(GESTURE_REACH, 1f, false)
        ?.let { it as? BlockHitResult }
        ?.takeIf { it.type == HitResult.Type.BLOCK }
        ?.blockPos

    /** The keys the wheel hands back to the world, and whether each was down on the last tick. */
    private val movement by lazy { with(minecraft.options) { listOf(keyUp, keyDown, keyLeft, keyRight, keyJump, keyShift, keySprint) } }
    private val movementDown = BooleanArray(7)

    override fun init() {
        // `init` runs again on a resize; the setting is read once, before the wheel touches it.
        if (ownBlur < 0) ownBlur = minecraft.options.menuBackgroundBlurriness().get()
        rebuild()
        rebuildArc()
        updateCaption()
    }

    override fun isPauseScreen(): Boolean = false

    /**
     * The world behind blurs and darkens, both easing in over 150 ms.
     *
     * The blur's radius is no parameter of the call: the shader reads it from the player's
     * "menu background blur" setting every frame. So the wheel walks that setting from 0 up to
     * the player's own value and puts it back in [removed]. A player who set it to 0 gets no blur
     * here either — only the darkening.
     */
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val shown = easeOut(now() / BACKDROP_TICKS)
        val option = minecraft.options.menuBackgroundBlurriness()
        val radius = (ownBlur * shown).roundToInt()
        if (option.get() != radius) option.set(radius)
        if (radius >= 1) graphics.blurBeforeThisStratum()
        graphics.fill(0, 0, width, height, UiTheme.argb(UiTheme.PANEL, DARKEN * shown))
    }

    override fun removed() {
        if (ownBlur >= 0) minecraft.options.menuBackgroundBlurriness().set(ownBlur)
    }

    override fun tick() {
        age++
        val player = minecraft.player
        if (player == null || !player.isAlive || ClientAbilityState.entries.isEmpty()) {
            onClose()
            return
        }
        // An ascension mid-hold: follow the same identity if it survived, otherwise the centre.
        if (ClientAbilityState.entries !== entries) {
            val kept = hoveredEntry()?.identity
            entries = ClientAbilityState.entries
            pages = pagesOf()
            page = page.coerceAtMost(pages.size - 1)
            hovered = kept?.let { id -> onPage().indexOfFirst { it.identity == id } } ?: -1
            hoveredMode = -1
            rebuildArc()
            updateCaption()
        }
        // A release the screen never saw (it beat the screen open, or the window lost focus) still
        // counts — otherwise the wheel would sit there and pin itself.
        if (!pinned && !ModKeys.isHeld(minecraft, KeyMappingHelper.getBoundKeyOf(key))) {
            commit()
            return
        }
        if (!pinned && age >= PIN_TICKS) {
            pinned = true
            minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), PIN_PITCH, PIN_VOLUME))
            updateCaption()
        }
        if (tab == Tab.THREADS) {
            if (!ClientMarionettes.hasThreads) switchTab() else if (hovered >= ClientMarionettes.entries.size) hover(-1)
            bodies.keys.retainAll(ClientMarionettes.entries.map { it.id }.toSet())
        }
        if (switchHover && now() - switchSince >= SWITCH_DWELL_TICKS) switchTab()
        walk()
        rebuild()
        // Health, distance and the strand countdown move on their own.
        if (tab == Tab.THREADS) updateCaption()
    }

    /**
     * The movement keys follow the keyboard while the wheel is open. Only on an edge: a toggle-sneak
     * flips on every `setDown(true)`, so writing the level each tick would flip it every tick.
     */
    private fun walk() {
        movement.forEachIndexed { index, mapping ->
            val down = ModKeys.isHeld(minecraft, KeyMappingHelper.getBoundKeyOf(mapping))
            if (down != movementDown[index]) {
                mapping.setDown(down)
                movementDown[index] = down
            }
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        // The wheel is drawn scaled about its centre, so the cursor is measured in the same units.
        val scale = ClientOptions.hudScale
        val dx = (mouseX - centerX) / scale
        val dy = (mouseY - centerY) / scale
        val row = WheelLayout.pickArc(dx, dy, count(), CARD, arc)
        hoverMode(row)
        if (row >= 0) return
        val onSwitch = onSwitcher(dx, dy)
        if (onSwitch != switchHover) {
            switchHover = onSwitch
            switchSince = now()
        }
        if (onSwitch) {
            hover(-1)
            return
        }
        hover(WheelLayout.pick(dx, dy, count(), ClientOptions.wheelDeadZoneRatio))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        return flip(if (scrollY < 0) 1 else -1)
    }

    /**
     * The wheel without a mouse: left and right walk the ring, up and down turn the page, Enter uses
     * the card. Escape is the vanilla close — the same as letting go in the centre.
     */
    override fun keyPressed(event: KeyEvent): Boolean {
        val size = count()
        when {
            // The key's own repeats while held, and its press on a pinned wheel: the release decides.
            key.matches(event) -> Unit
            event.key() == GLFW.GLFW_KEY_TAB && ClientMarionettes.hasThreads -> switchTab()
            event.isRight && size > 0 -> hover(Math.floorMod(hovered + 1, size))
            event.isLeft && size > 0 -> hover(Math.floorMod(if (hovered < 0) -1 else hovered - 1, size))
            (event.isDown || event.isUp) && arc.isNotEmpty() ->
                hoverMode(Math.floorMod(hoveredMode + if (event.isDown) 1 else -1, arc.size + 1).let { if (it == arc.size) -1 else it })
            event.isDown -> flip(1)
            event.isUp -> flip(-1)
            event.isConfirmation -> commit()
            else -> return super.keyPressed(event)
        }
        return true
    }

    private fun hover(index: Int) {
        if (index == hovered) return
        hovered = index
        hoverSince = now()
        hoveredMode = -1
        rebuildArc()
        updateCaption()
    }

    private fun hoverMode(index: Int) {
        if (index == hoveredMode) return
        hoveredMode = index
        verbSince = now()
        updateCaption()
    }

    /** How many cards the ring holds on this tab. */
    private fun count(): Int = if (tab == Tab.THREADS) ClientMarionettes.entries.size else onPage().size

    /** Where card [index] stands from the centre, on either tab. */
    private fun offsetOf(index: Int): Pair<Int, Int>? =
        if (tab == Tab.THREADS) threadCards.getOrNull(index) else cards.getOrNull(index)?.let { it.dx to it.dy }

    private fun switchTab() {
        tab = if (tab == Tab.ABILITIES && ClientMarionettes.hasThreads) Tab.THREADS else Tab.ABILITIES
        hovered = -1
        hoveredMode = -1
        switchHover = false
        rebuild()
        rebuildArc()
        updateCaption()
    }

    /**
     * Client ticks since opening, with the fraction of the current one. Not the `partialTick`
     * screens are handed: on 26.2 `Gui` passes `getGameTimeDeltaTicks()` — the last frame's length
     * — so `age + partialTick` jumped back and forth every frame and the opening stuttered.
     */
    private fun now(): Float = age + minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)

    /** The hovered card has sub-choices: its arc is open. */
    private fun expanded(): Boolean = arc.isNotEmpty()

    /** How far the arc has unfolded, 0 when none is open. */
    private fun arcOpen(now: Float): Float = if (expanded()) easeOut((now - hoverSince) / EXPAND_TICKS) else 0f

    /**
     * The arc is built on hover, not in the frame: the labels are components. The picked mode is told
     * by its colour, not a mark. Rows nearer the card in angle unfold first, so the arc fans out of it.
     */
    private fun rebuildArc() {
        val at = offsetOf(hovered)
        val puppet = hoveredPuppet()
        val entry = hoveredEntry()
        arcLabels = when {
            at == null -> emptyList()
            puppet != null -> Verb.entries.map { verb ->
                val name = if (verb == Verb.POSSESS && ClientMarionettes.possessing == puppet.id) "leave" else verb.name.lowercase()
                // "Use" without a block looked at on opening has nothing to press: it reads as such.
                Component.translatable("wheel.$MOD_ID.verb.$name").let { if (verb == Verb.USE && openedOn == null) it.withColor(UiTheme.MUTED) else it }
            }
            entry != null -> {
                val picked = ClientAbilityState.modeOf(entry.identity)
                entry.modes.map { mode -> if (mode.id == picked) mode.name.copy().withColor(accent) else mode.name }
            }
            else -> emptyList()
        }
        arcPicked = entry?.modes?.indexOfFirst { it.id == ClientAbilityState.modeOf(entry.identity) } ?: -1
        if (at == null || arcLabels.isEmpty()) {
            arc = emptyList()
            arcDelays = FloatArray(0)
            return
        }
        val half = (height / 2 / ClientOptions.hudScale).toInt() - SCREEN_MARGIN
        val avoid = switcherRect()?.let { (_, y, _, h) -> y - SWITCH_SLACK - 1..y + h + SWITCH_SLACK + 1 }
        arc = WheelLayout.arc(at.first, at.second, count(), arcLabels.size, half, avoid)
        val card = WheelLayout.degrees(at.first.toDouble(), at.second.toDouble())
        val order = arc.indices.sortedBy { abs((arc[it].from + arc[it].to) / 2 - card) }
        arcDelays = FloatArray(arc.size).also { delays -> order.forEachIndexed { rank, i -> delays[i] = rank * ARC_STAGGER_TICKS } }
    }

    /** The mode under the cursor, if the column is open and the cursor is on a pill. */
    private fun hoveredModeId(): String? =
        hoveredMode.takeIf { it >= 0 && expanded() }?.let { hoveredEntry()?.modes?.getOrNull(it)?.id }

    /** Next or previous page; the group of the page becomes the active one, its hotkeys with it. */
    private fun flip(step: Int): Boolean {
        if (tab == Tab.THREADS || pages.size <= 1) return false
        page = Math.floorMod(page + step, pages.size)
        ClientOptions.activeGroup = pages[page].group
        hovered = -1
        hoveredMode = -1
        rebuildArc()
        updateCaption()
        rebuild()
        return true
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (!key.matches(event)) return super.keyReleased(event)
        castKeyReleased()
        return true
    }

    /** The cast key may be bound to a mouse button (Mouse4) — release works the same way. */
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!key.matchesMouse(event)) return super.mouseReleased(event)
        castKeyReleased()
        return true
    }

    /** A pinned wheel takes clicks: the card under the cursor is picked, and the wheel stays. */
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || key.matchesMouse(event)) return super.mouseClicked(event, doubleClick)
        val scale = ClientOptions.hudScale
        val dx = ((event.x() - centerX) / scale).toInt()
        val dy = ((event.y() - centerY) / scale).toInt()
        if (onSwitcher(dx.toDouble(), dy.toDouble())) {
            switchTab()
            return true
        }
        if (!pinned) return super.mouseClicked(event, doubleClick)
        commit()
        return true
    }

    /**
     * The ordinary wheel: let go — pick and close. A pinned one ignores the release that pinned it
     * and closes on the next, so the key is up when the world gets it back — a close on the press
     * would hand the world a held key, and its release would cast.
     */
    private fun castKeyReleased() {
        when {
            !pinned -> commit()
            released -> onClose()
            else -> released = true
        }
    }

    /** Pick the card under the cursor; only the ordinary wheel closes after it — or gives way to a puppet's line. */
    private fun commit() {
        select()
        speakTo?.let { puppet ->
            speakTo = null
            minecraft.gui.setScreen(PuppetSpeechScreen(puppet.id, puppet.name))
            return
        }
        if (!pinned) onClose()
    }

    private fun select() {
        if (tab == Tab.THREADS) return order()
        hoveredEntry()?.takeIf { it.type != AbilityType.PERMANENT_PASSIVE }?.let {
            ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(Action.SELECT, it.identity, hoveredModeId()))
        }
    }

    /**
     * The puppet under the cursor: the verb on the pill picked, or — on the card itself — that puppet
     * becomes the one the threads key and Possession act on. Cutting wants a second's rest on its pill.
     */
    private fun order() {
        val puppet = hoveredPuppet() ?: return
        val verb = hoveredVerb()
        val payload = when {
            verb == null -> MarionetteOrderPayload(MarionetteOrderPayload.Action.SELECT, puppet = puppet.id)
            verb == Verb.CUT && now() - verbSince < CUT_HOLD_TICKS -> return
            verb == Verb.SPEAK -> {
                speakTo = puppet
                return
            }
            else -> MarionetteOrderPayload(verb.action, puppet = puppet.id, block = openedOn)
        }
        ModChannels.MARIONETTE_ORDER.sendToServer(payload)
    }

    private fun hoveredPuppet(): MarionetteStatePayload.Entry? =
        if (tab == Tab.THREADS) ClientMarionettes.entries.getOrNull(hovered) else null

    private fun hoveredVerb(): Verb? = hoveredMode.takeIf { it >= 0 && expanded() }?.let { Verb.entries.getOrNull(it) }

    // ── State ───────────────────────────────────────────────────────────────────

    /** Every group that holds something right now, each cut into rings of twelve. */
    private fun pagesOf(): List<Page> {
        val named = ClientOptions.groups.size > 1
        val built = ClientOptions.groups.flatMapIndexed { index, group ->
            val chunks = ModKeys.visible(group).chunked(WheelLayout.PAGE_SIZE)
            chunks.mapIndexed { n, chunk ->
                val parts = buildList {
                    if (named) add(groupTitle(group).string)
                    if (chunks.size > 1) add("${n + 1}/${chunks.size}")
                }
                Page(index, chunk, parts.takeIf { it.isNotEmpty() }?.let { Component.literal(it.joinToString(" ")) })
            }
        }
        return built.ifEmpty { listOf(Page(0, emptyList(), null)) }
    }

    /** The active group's page holding the selection, else its first page, else the first of all. */
    private fun startPage(): Int {
        val active = ClientOptions.activeGroup
        val holding = pages.indexOfFirst { p -> p.group == active && p.entries.any { it.identity == ClientAbilityState.selected } }
        if (holding >= 0) return holding
        return pages.indexOfFirst { it.group == active }.coerceAtLeast(0)
    }

    private fun onPage(): List<AbilityRosterPayload.Entry> = pages[page].entries

    private fun hoveredEntry(): AbilityRosterPayload.Entry? = if (tab == Tab.THREADS) null else onPage().getOrNull(hovered)

    /**
     * The side panel's text: the hovered ability's name, the mode it will be used in, its Sequence
     * and description; or, on the threads ring, the hovered puppet's card. Nothing hovered, nothing.
     */
    private fun updateCaption() {
        if (tab == Tab.THREADS) {
            caption = emptyList()
            puppetInfo = hoveredPuppet()?.let(::infoOf)
            return
        }
        puppetInfo = null
        val entry = hoveredEntry()
        if (entry == null) {
            caption = emptyList()
            return
        }
        val width = panelWidth()
        val mode = hoveredMode.takeIf { it >= 0 }?.let { entry.modes.getOrNull(it) }
            ?: entry.modes.firstOrNull { it.id == ClientAbilityState.modeOf(entry.identity) }
        caption = buildList {
            addAll(font.split(entry.name, width))
            mode?.let { add(it.name.copy().withColor(accent).visualOrderText) }
            add(originLine(entry.origin, accent).visualOrderText)
            if (ClientOptions.showDescriptions) {
                add(FormattedCharSequence.EMPTY)
                addAll(font.split(entry.description, width))
            }
        }
    }

    /** As wide as the room left of the ring allows, never wider than a comfortable line. */
    private fun panelWidth(): Int {
        val ring = ((WheelLayout.radius(count()) + CARD / 2 + PANEL_GAP) * ClientOptions.hudScale).roundToInt()
        return (centerX - ring - PANEL_MARGIN - PANEL_PADDING * 2).coerceIn(PANEL_MIN_WIDTH, CAPTION_WIDTH)
    }

    /** What the panel says of [puppet], each part in its own colour. */
    private fun infoOf(puppet: MarionetteStatePayload.Entry): PuppetInfo {
        val pathway = puppet.pathway
        val kind = when {
            pathway != null && puppet.sequence >= 0 -> Component.translatable(
                "wheel.$MOD_ID.puppet.beyonder", Component.translatable("book.$MOD_ID.pathway.${pathway.lowercase()}"), puppet.sequence,
            ).withColor(PathwayBranding.textOf(pathway).packed)
            puppet.type == MARIONETTE_TYPE -> Component.translatable("wheel.$MOD_ID.puppet.human").withColor(UiTheme.MUTED)
            else -> (typeOf(puppet)?.description?.copy() ?: Component.literal(puppet.type)).withColor(UiTheme.MUTED)
        }
        val distance = if (puppet.distance < 0) {
            Component.translatable("wheel.$MOD_ID.puppet.elsewhere").withColor(UiTheme.MUTED)
        } else {
            Component.translatable("wheel.$MOD_ID.puppet.distance", puppet.distance).withColor(DISTANCE)
        }
        val note = when {
            puppet.strandSeconds > 0 -> {
                val left = "${puppet.strandSeconds / 60}:${(puppet.strandSeconds % 60).toString().padStart(2, '0')}"
                Component.translatable("wheel.$MOD_ID.puppet.strand", left).withColor(UiTheme.CRITICAL)
            }
            puppet.id == ClientMarionettes.possessing -> Component.translatable("wheel.$MOD_ID.puppet.inside").withColor(UiTheme.ACCENT)
            else -> null
        }
        return PuppetInfo(
            entry = puppet,
            name = Component.literal(puppet.name).withColor(UiTheme.TEXT),
            kind = kind.takeUnless { it.string == puppet.name },
            health = Component.literal("${puppet.health.roundToInt()}/${puppet.maxHealth.roundToInt()}").withColor(HEALTH),
            distance = distance,
            order = Component.literal(puppet.order.caption.replaceFirstChar { it.uppercase() }).withColor(colorOf(puppet.order)),
            note = note,
        )
    }

    private fun colorOf(order: MarionetteStatePayload.Order): Int = when (order) {
        MarionetteStatePayload.Order.STAND -> UiTheme.MUTED
        MarionetteStatePayload.Order.GOING, MarionetteStatePayload.Order.USING -> GOING
        MarionetteStatePayload.Order.FOLLOWING -> FOLLOWING
        MarionetteStatePayload.Order.GUARDING -> GUARDING
        MarionetteStatePayload.Order.ASSISTING -> ASSISTING
        MarionetteStatePayload.Order.ATTACKING -> UiTheme.CRITICAL
    }

    /** Which way the puppet stands from where the player looks, in radians: 0 ahead, growing to the right. */
    private fun bearing(puppet: MarionetteStatePayload.Entry): Double {
        val player = minecraft.player ?: return 0.0
        val to = puppet.at.subtract(player.position())
        val yaw = Math.toRadians(player.yRot.toDouble())
        val ahead = -to.x * sin(yaw) + to.z * cos(yaw)
        val right = -to.x * cos(yaw) - to.z * sin(yaw)
        return atan2(right, ahead)
    }

    private fun typeOf(puppet: MarionetteStatePayload.Entry): EntityType<*>? =
        Identifier.tryParse(puppet.type)?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null) }

    /** The live body if this client has it, else a stand-in built from what the server said. */
    private fun bodyOf(puppet: MarionetteStatePayload.Entry): LivingEntity? {
        val level = minecraft.level ?: return null
        val live = level.getEntity(puppet.entity) as? LivingEntity
        if (live != null && live.uuid == puppet.id && !(live is MarionetteEntity && live.hiddenForDriver)) return live
        return bodies.getOrPut(puppet.id) {
            val made = typeOf(puppet)?.create(level, EntitySpawnReason.LOAD) as? LivingEntity
                ?: return null
            (made as? MarionetteEntity)?.let { body -> puppet.face?.let(body::setProfile) }
            SHOWN_SLOTS.forEachIndexed { index, slot -> made.setItemSlot(slot, puppet.equipment.getOrElse(index) { net.minecraft.world.item.ItemStack.EMPTY }) }
            made
        }
    }

    private fun rebuild() {
        accent = ClientAbilityState.roster?.let { PathwayBranding.textOf(it.pathwayId).packed } ?: UiTheme.ACCENT
        if (tab == Tab.THREADS) {
            cards = emptyList()
            val n = ClientMarionettes.entries.size
            threadCards = List(n) { WheelLayout.offset(it, n).let { (dx, dy) -> dx to dy } }
            pageLabel = null
            return
        }
        val shown = onPage()
        cards = shown.mapIndexed { index, entry ->
            val (dx, dy) = WheelLayout.offset(index, shown.size)
            val (nx, ny) = WheelLayout.hourMark(dx, dy, CARD, font.lineHeight)
            val seconds = ClientAbilityState.remainingSeconds(entry.identity)
            val affordable = ClientAbilityState.canAfford(entry)
            Card(
                entry = entry,
                dx = dx,
                dy = dy,
                price = priceOf(entry),
                spirit = entry.cost > 0 || entry.upkeep > 0,
                critical = entry.type == AbilityType.ACTIVE && !affordable,
                selected = entry.identity == ClientAbilityState.selected,
                marker = when {
                    ClientAbilityState.isToggled(entry.identity) -> Component.literal("●")
                    entry.type == AbilityType.TOGGLEABLE_PASSIVE -> Component.literal("○")
                    ClientAbilityState.isSustaining(entry.identity) -> Component.literal("◐")
                    else -> null
                },
                cooldown = if (seconds > 0 && ClientOptions.showCooldownNumbers) Component.literal("$seconds") else null,
                remaining = 1f - ClientAbilityState.cooldownProgress(entry),
                dim = entry.type == AbilityType.PERMANENT_PASSIVE,
                slot = ModKeys.hotkeyLabel(entry.identity, group = ClientOptions.groups.getOrElse(pages[page].group) { ClientOptions.group }),
                numeral = entry.origin?.level?.toString(),
                numeralX = nx,
                numeralY = ny,
            )
        }
        pageLabel = pages[page].label
    }

    /** Same wording as the HUD: one-off, upkeep, or both. The spirituality icon is drawn, not typed. */
    private fun priceOf(entry: AbilityRosterPayload.Entry): Component? {
        val parts = buildList {
            if (entry.cost > 0) add("${entry.cost}")
            if (entry.upkeep > 0) add("${entry.upkeep}/с")
        }
        if (parts.isEmpty()) return null
        return Component.literal(parts.joinToString(" + "))
    }

    // ── Frame ───────────────────────────────────────────────────────────────────

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = now()
        val opening = easeOut(now / OPEN_TICKS)
        val alpha = ClientOptions.opacity * opening
        val grow = 1f + (HOVER_SCALE - 1f) * easeOut((now - hoverSince) / HOVER_TICKS)

        // The same size multiplier as the HUD, applied about the wheel's centre. The cursor is
        // measured against the settled size: the opening lasts two ticks, too short to aim in.
        val scale = ClientOptions.hudScale * (OPEN_SCALE + (1f - OPEN_SCALE) * opening)
        graphics.pose().pushMatrix()
        graphics.pose().translate(centerX.toFloat(), centerY.toFloat())
        graphics.pose().scale(scale, scale)
        graphics.pose().translate(-centerX.toFloat(), -centerY.toFloat())

        val skin = ClientOptions.wheelSkin
        if (count() > 0) skin.ring(graphics, centerX, centerY, WheelLayout.radius(count()), count(), alpha, accent)
        if (tab == Tab.THREADS) puppets(graphics, alpha, grow)
        switcherCard(graphics, alpha)
        val pathway = ClientAbilityState.roster?.pathwayId

        // Pass one: panels and icons.
        cards.forEachIndexed { index, card ->
            val x = centerX + card.dx - CARD / 2
            val y = centerY + card.dy - CARD / 2
            val a = if (card.dim) alpha * DIM else alpha
            grown(graphics, card, if (index == hovered) grow else 1f) {
                skin.body(graphics, x, y, a)
                graphics.item(card.entry.icon, x + ICON_INSET, y + ICON_INSET)
            }
        }

        // Items go in their own pass; everything that must lie over the icons needs a new stratum.
        graphics.nextStratum()

        // An open arc runs where the hour marks and the neighbours' prices stand: they make way.
        val rest = 1f - arcOpen(now)
        cards.forEachIndexed { index, card ->
            val x = centerX + card.dx - CARD / 2
            val y = centerY + card.dy - CARD / 2
            val a = if (card.dim) alpha * DIM else alpha
            grown(graphics, card, if (index == hovered) grow else 1f) {
                UiTheme.cooldownVeil(graphics, x + ICON_INSET, y + ICON_INSET, ICON, card.remaining, a)
                skin.emblem(graphics, x, y, pathway, accent, a)
                card.cooldown?.let { graphics.centeredText(font, it, x + CARD / 2, y + CARD / 2 - 4, UiTheme.argb(UiTheme.TEXT, a)) }
                card.marker?.let { graphics.text(font, it, x + CARD - 5, y - 3, UiTheme.argb(UiTheme.ACCENT, a)) }
                card.slot?.let { graphics.text(font, it, x - 2, y - 3, UiTheme.argb(UiTheme.MUTED, a)) }
                card.price?.let {
                    val width = UiTheme.priceWidth(font, it, card.spirit)
                    UiTheme.price(graphics, font, it, card.critical, card.spirit, x + CARD / 2 - width / 2, y + CARD + 2, if (index == hovered) a else a * rest)
                }
                card.numeral?.let {
                    val seal = (if (index == hovered) alpha else a * NUMERAL_ALPHA) * rest
                    if (seal > 0f) graphics.centeredText(font, it, centerX + card.numeralX, centerY + card.numeralY - font.lineHeight / 2, UiTheme.argb(accent, seal))
                }
                // The pathway colour appears in the hover outline, the hour marks and the skin's centre ornament.
                skin.marks(graphics, x, y, index == hovered, card.selected, card.critical, if (index == hovered) alpha else a, accent)
            }
        }

        // Centre: a cross-hair (a pin once pinned) in the hold ring, and the page counter.
        holdRing(graphics, now, alpha)
        if (pinned) pin(graphics, UiTheme.argb(UiTheme.ACCENT, alpha))
        else graphics.centeredText(font, "+", centerX, centerY - 4, UiTheme.argb(UiTheme.MUTED, alpha))
        pageLabel?.let { graphics.centeredText(font, it, centerX, centerY + 8, UiTheme.argb(UiTheme.MUTED, alpha)) }
        switcherMarks(graphics, now, alpha)

        arcOf(graphics, now, alpha)

        graphics.pose().popMatrix()

        // The side panel is not scaled with the ring: it is read, not aimed at. An arc opening to the
        // left stands where the panel does, and the panel gives way while it is open.
        graphics.nextStratum()
        val panel = if (arc.firstOrNull()?.right == false) alpha * rest else alpha
        if (panel > 0f) if (tab == Tab.THREADS) puppetPanel(graphics, panel) else abilityPanel(graphics, panel)

        // The spirituality bar was drawn before the screen, so the blur took it too. It is where
        // the cost of the card under the cursor is read, so it is drawn again, sharp, on top.
        graphics.nextStratum()
        SpiritualityHud.extractRenderState(graphics, minecraft.deltaTracker)
    }

    /**
     * The open arc, over everything else: the band unfolds out of the card's angle over 120 ms, and
     * each name fades in sliding out from the ring, the rows nearest the card first. A row that had to
     * slide away from its card is tied back to it by a one-pixel stem. The hovered slice is lit and
     * thicker, and its name steps out; the mode already picked keeps the pathway's colour.
     */
    private fun arcOf(graphics: GuiGraphicsExtractor, now: Float, alpha: Float) {
        val (cardX, cardY) = offsetOf(hovered) ?: return
        if (!expanded()) return
        val open = arcOpen(now).toDouble()
        val card = WheelLayout.degrees(cardX.toDouble(), cardY.toDouble())
        val band = WheelLayout.band(count())
        val gap = Math.toDegrees(SLICE_GAP / band)
        val idle = UiTheme.argb(UiTheme.MUTED, alpha * ARC_IDLE)
        graphics.nextStratum()
        arc.forEachIndexed { index, slice ->
            val hot = index == hoveredMode
            val from = card + (slice.from + gap - card) * open
            val to = card + (slice.to - gap - card) * open
            slice.stem?.let { stem ->
                val near = card + ((if (stem < slice.from) slice.from else slice.to) - card) * open
                bandArc(graphics, band + 1, 1, minOf(stem, near), maxOf(stem, near), idle)
            }
            val color = if (hot || index == arcPicked) UiTheme.argb(accent, alpha * if (hot) 1f else ARC_IDLE) else idle
            val inner = if (hot) -1 else 0
            val thickness = if (hot) HOT_BAND else BAND
            // Cutting a thread burns its slice red over a second before it can be let go on.
            val burnt = if (hot && tab == Tab.THREADS && index == Verb.CUT.ordinal) ((now - verbSince) / CUT_HOLD_TICKS).coerceIn(0f, 1f) else 0f
            val split = from + (to - from) * burnt
            if (burnt > 0f) bandArc(graphics, band + inner, thickness, from, split, UiTheme.argb(UiTheme.CRITICAL, alpha))
            bandArc(graphics, band + inner, thickness, split, to, color)

            val shown = easeOut((now - hoverSince - arcDelays[index]) / EXPAND_TICKS)
            if (shown <= 0f) return@forEachIndexed
            // ponytail: a name wider than the room beside the arc runs off a 240-px-tall screen's edge;
            // the longest today ("Говорити від її імені") just fits. Trim with `plainSubstrByWidth` if one does not.
            val label = arcLabels[index]
            val out =(if (hot) HOT_STEP else 0) - ((1f - shown) * LABEL_SLIDE).roundToInt()
            val x = if (slice.right) centerX + slice.x + out else centerX + slice.x - font.width(label) - out
            graphics.text(font, label, x, centerY + slice.y + 1, UiTheme.argb(if (hot) UiTheme.TEXT else UiTheme.MUTED, alpha * shown))
        }
    }

    /**
     * A pixel arc [thickness] pixels deep from [radius] outwards, between two angles clockwise from
     * straight up. Each pixel once: the slices are translucent, and a pixel laid twice reads as a dot.
     */
    private fun bandArc(graphics: GuiGraphicsExtractor, radius: Int, thickness: Int, from: Double, to: Double, color: Int) {
        if (to <= from) return
        val a0 = Math.toRadians(from)
        val a1 = Math.toRadians(to)
        val steps = ((a1 - a0) * (radius + thickness)).roundToInt().coerceAtLeast(1)
        val laid = HashSet<Long>()
        for (i in 0..steps) {
            val a = a0 + (a1 - a0) * i / steps
            for (r in radius until radius + thickness) {
                val x = centerX + (sin(a) * r).roundToInt()
                val y = centerY - (cos(a) * r).roundToInt()
                if (laid.add(x.toLong() shl 32 or (y.toLong() and 0xFFFFFFFFL))) graphics.fill(x, y, x + 1, y + 1, color)
            }
        }
    }

    /**
     * The hold ring: a one-pixel circle round the centre, filled clockwise from the top between 1.5 s
     * and 4 s of holding, breathing while it fills; gold and whole once pinned.
     */
    private fun holdRing(graphics: GuiGraphicsExtractor, now: Float, alpha: Float) {
        val fill = if (pinned) 1f else ((now - RING_TICKS) / (PIN_TICKS - RING_TICKS)).coerceAtMost(1f)
        if (fill <= 0f) return
        val breath = if (pinned || ClientOptions.reducedMotion) 1f else 0.75f + 0.25f * sin(now * BREATH_RATE)
        val lit = UiTheme.argb(if (pinned) UiTheme.ACCENT else UiTheme.TEXT, alpha * breath)
        val track = UiTheme.argb(UiTheme.MUTED, alpha * TRACK_ALPHA)
        val steps = (2 * PI * RING_RADIUS).roundToInt()
        for (i in 0 until steps) {
            val angle = 2 * PI * i / steps
            val x = centerX + (sin(angle) * RING_RADIUS).roundToInt()
            val y = centerY - (cos(angle) * RING_RADIUS).roundToInt()
            graphics.fill(x, y, x + 1, y + 1, if (i < fill * steps) lit else track)
        }
    }

    /** A pixel pin where the cross-hair was: a head three wide, a shoulder, and the point. */
    private fun pin(graphics: GuiGraphicsExtractor, color: Int) {
        val x = centerX - 2
        val y = centerY - 4
        graphics.fill(x + 1, y, x + 4, y + 2, color)
        graphics.fill(x, y + 2, x + 5, y + 3, color)
        graphics.fill(x + 2, y + 3, x + 3, y + 7, color)
    }

    /**
     * The threads tab's cards: each puppet's own body, three-quarters on, standing in its card — the
     * live one if this client has it, otherwise a stand-in in its face and its gear.
     */
    private fun puppets(graphics: GuiGraphicsExtractor, alpha: Float, grow: Float) {
        val skin = ClientOptions.wheelSkin
        ClientMarionettes.entries.forEachIndexed { index, puppet ->
            val (dx, dy) = threadCards.getOrNull(index) ?: return@forEachIndexed
            val x = centerX + dx - CARD / 2
            val y = centerY + dy - CARD / 2
            skin.body(graphics, x, y, alpha)
            bodyOf(puppet)?.let { body ->
                val size = (MODEL_HEIGHT / maxOf(body.bbHeight, body.bbWidth, 0.5f)).roundToInt()
                // A point up and to the right of the card for the body to face: three-quarters on.
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, x, y - 2, x + CARD, y + CARD, size, MODEL_LIFT,
                    (x + CARD / 2 + FACING_X).toFloat(), (y + FACING_Y).toFloat(), body,
                )
            }
            graphics.nextStratum()
            if (puppet.id == ClientMarionettes.possessing) graphics.text(font, "◉", x + CARD - 5, y - 3, UiTheme.argb(UiTheme.ACCENT, alpha))
            if (puppet.strandSeconds > 0) graphics.text(font, "!", x - 2, y - 3, UiTheme.argb(UiTheme.CRITICAL, alpha))
            skin.marks(graphics, x, y, index == hovered, puppet.id == ClientMarionettes.selected, false, alpha, accent)
        }
    }

    // ── The other ring ──────────────────────────────────────────────────────────

    /** Where the arrow card to the other ring stands, `[x, y, w, h]` from the centre; `null` for whoever holds no threads. */
    private fun switcherRect(): IntArray? =
        if (ClientMarionettes.hasThreads) WheelLayout.switcher(count(), CARD) else null

    private fun onSwitcher(dx: Double, dy: Double): Boolean {
        val (x, y, w, h) = switcherRect() ?: return false
        return dx >= x - SWITCH_SLACK && dx < x + w + SWITCH_SLACK && dy >= y - SWITCH_SLACK && dy < y + h + SWITCH_SLACK
    }

    /** The arrow card's body and what lies the other way: thread for the puppets, the chosen ability for the abilities. */
    private fun switcherCard(graphics: GuiGraphicsExtractor, alpha: Float) {
        val (x, y) = switcherRect() ?: return
        val left = centerX + x
        val top = centerY + y
        ClientOptions.wheelSkin.body(graphics, left, top, alpha * if (switchHover) 1f else SWITCH_IDLE)
        val icon = if (tab == Tab.ABILITIES) THREAD_ICON else
            (ClientAbilityState.entries.firstOrNull { it.identity == ClientAbilityState.selected } ?: ClientAbilityState.entries.firstOrNull())?.icon
        icon?.let { graphics.item(it, left + ICON_INSET, top + ICON_INSET) }
    }

    /** Over the icons: the pixel arrow pointing out, and the rest filling under the card until it turns. */
    private fun switcherMarks(graphics: GuiGraphicsExtractor, now: Float, alpha: Float) {
        val (x, y, w, h) = switcherRect() ?: return
        val left = centerX + x
        val top = centerY + y
        val color = UiTheme.argb(if (switchHover) accent else UiTheme.MUTED, alpha)
        chevron(graphics, left + w + CHEVRON_GAP, top + h / 2 - CHEVRON_HALF, color)
        if (switchHover) {
            val filled = ((now - switchSince) / SWITCH_DWELL_TICKS).coerceIn(0f, 1f)
            UiTheme.hairline(graphics, left, top + h + 2, (w * filled).roundToInt(), accent, alpha)
        }
        ClientOptions.wheelSkin.marks(graphics, left, top, switchHover, false, false, alpha, accent)
    }

    /** A pixel chevron pointing right, twice [CHEVRON_HALF] plus one tall, two pixels thick. */
    private fun chevron(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        for (k in 0..CHEVRON_HALF) {
            graphics.fill(x + k, y + k, x + k + 2, y + k + 1, color)
            graphics.fill(x + k, y + 2 * CHEVRON_HALF - k, x + k + 2, y + 2 * CHEVRON_HALF - k + 1, color)
        }
    }

    // ── The side panel ──────────────────────────────────────────────────────────

    private fun abilityPanel(graphics: GuiGraphicsExtractor, alpha: Float) {
        if (caption.isEmpty()) return
        val line = font.lineHeight + 1
        val width = caption.maxOf { font.width(it) }
        val height = caption.size * line - 1
        val (x, y) = panelAt()
        UiTheme.panel(graphics, x - PANEL_PADDING, y - PANEL_PADDING, width + PANEL_PADDING * 2, height + PANEL_PADDING * 2, alpha)
        caption.forEachIndexed { index, text ->
            val color = if (index == 0) UiTheme.TEXT else UiTheme.MUTED
            graphics.text(font, text, x, y + index * line, UiTheme.argb(color, alpha))
        }
    }

    /**
     * The puppet's card: its name, what it was, then a row of the two numbers that matter (a heart
     * with its health, a small dial pointing where it stands with the distance), what it is doing,
     * and the one warning, if any.
     */
    private fun puppetPanel(graphics: GuiGraphicsExtractor, alpha: Float) {
        val info = puppetInfo ?: return
        val line = font.lineHeight + 2
        val dialed = info.entry.distance >= 0
        val stats = ICON_TEXT + font.width(info.health) + STAT_GAP + (if (dialed) ICON_TEXT else 0) + font.width(info.distance)
        val rows = listOfNotNull(info.name, info.kind, info.order, info.note)
        val width = maxOf(stats, rows.maxOf { font.width(it) })
        val height = (rows.size + 1) * line - 1
        val (x, top) = panelAt()
        UiTheme.panel(graphics, x - PANEL_PADDING, top - PANEL_PADDING, width + PANEL_PADDING * 2, height + PANEL_PADDING * 2, alpha)

        var y = top
        graphics.text(font, info.name, x, y, UiTheme.argb(UiTheme.TEXT, alpha))
        y += line
        info.kind?.let {
            graphics.text(font, it, x, y, UiTheme.argb(UiTheme.MUTED, alpha))
            y += line
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART, x, y - 1, ICON_SIZE, ICON_SIZE, UiTheme.argb(0xFFFFFF, alpha))
        graphics.text(font, info.health, x + ICON_TEXT, y, UiTheme.argb(HEALTH, alpha))
        var at = x + ICON_TEXT + font.width(info.health) + STAT_GAP
        if (dialed) {
            dial(graphics, at, y - 1, bearing(info.entry), alpha)
            at += ICON_TEXT
        }
        graphics.text(font, info.distance, at, y, UiTheme.argb(DISTANCE, alpha))
        y += line
        graphics.text(font, info.order, x, y, UiTheme.argb(UiTheme.MUTED, alpha))
        info.note?.let { graphics.text(font, it, x, y + line, UiTheme.argb(UiTheme.MUTED, alpha)) }
    }

    /**
     * The panel's text starts here: at the screen's left, level with the top of the ring, so it grows
     * down beside the ring and stays clear of the spirituality flask by the hotbar.
     */
    private fun panelAt(): Pair<Int, Int> {
        val ringTop = centerY - ((WheelLayout.radius(count()) + CARD / 2) * ClientOptions.hudScale).roundToInt()
        return (PANEL_MARGIN + PANEL_PADDING) to ringTop.coerceAtLeast(PANEL_MARGIN + PANEL_PADDING)
    }

    /** A nine-pixel ring with a dot on it where the puppet stands, ahead at the top. */
    private fun dial(graphics: GuiGraphicsExtractor, x: Int, y: Int, angle: Double, alpha: Float) {
        val ring = UiTheme.argb(DISTANCE, alpha * DIAL_RING_ALPHA)
        DIAL.forEach { (dx, dy) -> graphics.fill(x + 4 + dx, y + 4 + dy, x + 5 + dx, y + 5 + dy, ring) }
        val px = x + 4 + (sin(angle) * DIAL_REACH).roundToInt()
        val py = y + 4 - (cos(angle) * DIAL_REACH).roundToInt()
        graphics.fill(px, py, px + 2, py + 2, UiTheme.argb(DISTANCE, alpha))
    }

    /** Draws [body] scaled by [factor] about the card's centre; 1 draws it as it is. */
    private inline fun grown(graphics: GuiGraphicsExtractor, card: Card, factor: Float, body: () -> Unit) {
        if (factor == 1f) return body()
        val cx = (centerX + card.dx).toFloat()
        val cy = (centerY + card.dy).toFloat()
        graphics.pose().pushMatrix()
        graphics.pose().translate(cx, cy)
        graphics.pose().scale(factor, factor)
        graphics.pose().translate(-cx, -cy)
        body()
        graphics.pose().popMatrix()
    }

    /** 0..1 progress, eased out; `reducedMotion` jumps straight to the end. */
    private fun easeOut(t: Float): Float {
        if (ClientOptions.reducedMotion) return 1f
        val clamped = t.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    private companion object {
        const val CARD = 24
        const val ICON = 16
        const val ICON_INSET = (CARD - ICON) / 2
        const val DIM = 0.45f
        const val CAPTION_WIDTH = 200

        /** 100 ms from 0.88 to full size; 80 ms for the hovered card to reach 1.08. */
        const val OPEN_TICKS = 2f
        const val OPEN_SCALE = 0.88f
        const val HOVER_TICKS = 1.6f
        const val HOVER_SCALE = 1.08f

        /**
         * The arc: 120 ms to unfold, each row's name 15 ms behind the one nearer the card, sliding
         * this far out from the ring; the pixel gap between slices and how bright a slice is at rest;
         * how deep a slice is, hovered or not (hovered reaches a pixel further in), and how far the
         * hovered name steps out.
         */
        const val EXPAND_TICKS = 2.4f
        const val ARC_STAGGER_TICKS = 0.3f
        const val LABEL_SLIDE = 6
        const val SLICE_GAP = 2.0
        const val ARC_IDLE = 0.6f
        const val BAND = 3
        const val HOT_BAND = 4
        const val HOT_STEP = 3

        /** 150 ms for the world behind to blur and darken; how dark it ends up. */
        const val BACKDROP_TICKS = 3f
        const val DARKEN = 0.45f

        /** The hour marks stay quiet until their card is hovered. */
        const val NUMERAL_ALPHA = 0.6f

        /** The hold ring shows from 1.5 s and pins at 4 s; its radius, unlit track and breathing. */
        const val RING_TICKS = 30f
        const val PIN_TICKS = 80
        const val RING_RADIUS = 9
        const val TRACK_ALPHA = 0.25f
        const val BREATH_RATE = 0.5f
        const val PIN_PITCH = 1.8f
        const val PIN_VOLUME = 0.3f

        /** How far the crosshair is read when the wheel opens: as far as a puppet can be pointed. */
        const val GESTURE_REACH = 64.0

        /** A second's rest on "cut" before letting go cuts. */
        const val CUT_HOLD_TICKS = 20f

        /** A puppet's body in its card: how tall, how far up, and the point it turns to face. */
        const val MODEL_HEIGHT = 11f
        const val MODEL_LIFT = 0.0625f
        const val FACING_X = 40
        const val FACING_Y = -6

        const val SCREEN_MARGIN = 2
        const val MARIONETTE_TYPE = "mysteries_above:marionette"

        /**
         * The arrow card to the other ring: how far off the ring it stands, how much slack the cursor
         * has around it, how dim it is while not rested on, and 300 ms of rest to turn the ring.
         */
        const val SWITCH_SLACK = 3
        const val SWITCH_IDLE = 0.6f
        const val SWITCH_DWELL_TICKS = 6f
        const val CHEVRON_GAP = 3
        const val CHEVRON_HALF = 3

        /**
         * The side panel: its margin from the screen's edge, padding, room left for the Sequence
         * numbers beside the ring's cards, and its narrowest text.
         */
        const val PANEL_MARGIN = 6
        const val PANEL_PADDING = 4
        const val PANEL_GAP = 16
        const val PANEL_MIN_WIDTH = 80

        /** A nine-pixel icon and the gap after it; the gap between the two numbers of the stats row. */
        const val ICON_SIZE = 9
        const val ICON_TEXT = ICON_SIZE + 2
        const val STAT_GAP = 10

        /** The puppet card's colours: health, distance, and what it is doing. */
        const val HEALTH = 0xE26A6A
        const val DISTANCE = 0x7FB2E5
        const val GOING = 0xD9D3C4
        const val FOLLOWING = 0x8CCB8A
        const val GUARDING = 0x7FA0E5
        const val ASSISTING = 0xE0A35A

        const val DIAL_RING_ALPHA = 0.45f
        const val DIAL_REACH = 2.5

        /** The outline of a circle four pixels across the centre, as offsets from it. */
        val DIAL: List<Pair<Int, Int>> = (-4..4).flatMap { dx -> (-4..4).map { dy -> dx to dy } }
            .filter { (dx, dy) -> kotlin.math.abs(kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) - 4.0) < 0.5 }

        val HEART: Identifier = Identifier.withDefaultNamespace("hud/heart/full")

        /** What lies the other way from the abilities: the threads. */
        val THREAD_ICON: ItemStack by lazy { ItemStack(Items.STRING) }

        /** The order of [MarionetteStatePayload.Entry.equipment]. */
        val SHOWN_SLOTS = listOf(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    }
}
