package me.vangoo.mysteries.client.screen

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.hud.SpiritualityHud
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
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
 * **Pinning.** Held past 1.5 s, a thin ring round the centre starts to fill; at 6 s it turns gold,
 * clicks, and the wheel is pinned: letting go no longer closes it, a click on a card picks that card
 * and the wheel stays, and the cast key (pressed and let go) or Escape closes it. The pin is for
 * sorting out a loadout in calm, not for a fight — a fight never holds the key for six seconds.
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
 * **Modes.** A card whose ability has several ways of use (the Underworld door's four) opens a
 * column of mode pills beside itself once the cursor has rested on it for 200 ms — a dwell, so a
 * sweep across the ring does not flick columns open. The pills slide out of the card. Releasing on
 * a pill picks the ability in that mode; releasing on the card keeps the mode already picked. While the
 * cursor travels from the card to its pills, the card under the angle does not change. Without a
 * mouse, ↑/↓ walk the pills of a card that has them (and turn the page of one that does not).
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

    /** The hovered card's mode pills (`[x, y, w, h]` from the centre) and their labels; empty — it has none. */
    private var pills: List<IntArray> = emptyList()
    private var pillLabels: List<Component> = emptyList()
    private var hoveredMode = -1

    private val centerX get() = width / 2
    private val centerY get() = height / 2

    /** The player's own menu blur, borrowed while the wheel eases it in; `-1` — not read yet. */
    private var ownBlur = -1

    /** Held long enough: the wheel outlives the key. [released] — the key has been let go since. */
    private var pinned = false
    private var released = false

    /** The keys the wheel hands back to the world, and whether each was down on the last tick. */
    private val movement by lazy { with(minecraft.options) { listOf(keyUp, keyDown, keyLeft, keyRight, keyJump, keyShift, keySprint) } }
    private val movementDown = BooleanArray(7)

    override fun init() {
        // `init` runs again on a resize; the setting is read once, before the wheel touches it.
        if (ownBlur < 0) ownBlur = minecraft.options.menuBackgroundBlurriness().get()
        rebuild()
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
            rebuildPills()
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
        walk()
        rebuild()
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
        val card = cards.getOrNull(hovered)
        if (card != null && expanded()) {
            val mode = WheelLayout.pickMode(dx, dy, pills)
            if (mode >= 0 || WheelLayout.inCorridor(dx, dy, card.dx, card.dy, CARD, pills)) {
                hoverMode(mode)
                return
            }
        }
        hover(WheelLayout.pick(dx, dy, onPage().size, ClientOptions.wheelDeadZoneRatio))
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
        val size = onPage().size
        when {
            // The key's own repeats while held, and its press on a pinned wheel: the release decides.
            key.matches(event) -> Unit
            event.isRight && size > 0 -> hover(Math.floorMod(hovered + 1, size))
            event.isLeft && size > 0 -> hover(Math.floorMod(if (hovered < 0) -1 else hovered - 1, size))
            (event.isDown || event.isUp) && pills.isNotEmpty() -> {
                // The keyboard does not wait for the dwell: it opens the column at once.
                hoverSince = minOf(hoverSince, now() - DWELL_TICKS)
                hoverMode(Math.floorMod(hoveredMode + if (event.isDown) 1 else -1, pills.size + 1).let { if (it == pills.size) -1 else it })
            }
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
        rebuildPills()
        updateCaption()
    }

    private fun hoverMode(index: Int) {
        if (index == hoveredMode) return
        hoveredMode = index
        updateCaption()
    }

    /**
     * Client ticks since opening, with the fraction of the current one. Not the `partialTick`
     * screens are handed: on 26.2 `Gui` passes `getGameTimeDeltaTicks()` — the last frame's length
     * — so `age + partialTick` jumped back and forth every frame and the opening stuttered.
     */
    private fun now(): Float = age + minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)

    /** The column is open: the card has modes and the cursor has rested on it long enough. */
    private fun expanded(): Boolean = pills.isNotEmpty() && now() - hoverSince >= DWELL_TICKS

    /** The pills are built on hover, not in the frame: the labels are components. */
    private fun rebuildPills() {
        val entry = hoveredEntry()
        if (entry == null || entry.modes.isEmpty()) {
            pills = emptyList()
            pillLabels = emptyList()
            return
        }
        val picked = ClientAbilityState.modeOf(entry.identity)
        pillLabels = entry.modes.map { mode ->
            if (mode.id == picked) Component.literal("● ").append(mode.name) else mode.name
        }
        val (dx, dy) = WheelLayout.offset(hovered, onPage().size)
        pills = WheelLayout.modeColumn(dx, dy, CARD, IntArray(pillLabels.size) { font.width(pillLabels[it]) + PILL_PADDING * 2 })
    }

    /** The mode under the cursor, if the column is open and the cursor is on a pill. */
    private fun hoveredModeId(): String? =
        hoveredMode.takeIf { it >= 0 && expanded() }?.let { hoveredEntry()?.modes?.getOrNull(it)?.id }

    /** Next or previous page; the group of the page becomes the active one, its hotkeys with it. */
    private fun flip(step: Int): Boolean {
        if (pages.size <= 1) return false
        page = Math.floorMod(page + step, pages.size)
        ClientOptions.activeGroup = pages[page].group
        hovered = -1
        hoveredMode = -1
        rebuildPills()
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
        if (!pinned || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || key.matchesMouse(event)) return super.mouseClicked(event, doubleClick)
        select()
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

    /** Pick the card under the cursor; only the ordinary wheel closes after it. */
    private fun commit() {
        select()
        if (!pinned) onClose()
    }

    private fun select() {
        hoveredEntry()?.takeIf { it.type != AbilityType.PERMANENT_PASSIVE }?.let {
            ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(Action.SELECT, it.identity, hoveredModeId()))
        }
    }

    // ── State ───────────────────────────────────────────────────────────────────

    /** Every group that holds something right now, each cut into rings of twelve. */
    private fun pagesOf(): List<Page> {
        val named = ClientOptions.groups.size > 1
        val built = ClientOptions.groups.flatMapIndexed { index, group ->
            val chunks = ModKeys.visible(group).chunked(WheelLayout.PAGE_SIZE)
            chunks.mapIndexed { n, chunk ->
                val parts = buildList {
                    if (named) add(groupTitle(group).string)
                    if (chunks.size > 1) add("${n + 1} / ${chunks.size}")
                }
                Page(index, chunk, parts.takeIf { it.isNotEmpty() }?.let { Component.literal(it.joinToString(" · ")) })
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

    private fun hoveredEntry(): AbilityRosterPayload.Entry? = onPage().getOrNull(hovered)

    private fun updateCaption() {
        val entry = hoveredEntry()
        val mode = hoveredMode.takeIf { it >= 0 }?.let { entry?.modes?.getOrNull(it) }
        val title = when {
            entry == null && pinned -> Component.translatable("wheel.$MOD_ID.pinned", key.translatedKeyMessage)
            entry == null -> Component.translatable("wheel.$MOD_ID.cancel")
            mode != null -> entry.name.copy().append(" · ").append(mode.name)
            else -> entry.name
        }
        caption = buildList {
            add(title.visualOrderText)
            if (entry != null) add(originLine(entry.origin, accent).visualOrderText)
            if (entry != null && ClientOptions.showDescriptions) {
                if (mode == null && entry.modes.isNotEmpty()) {
                    add(Component.translatable("wheel.$MOD_ID.modes_hint").visualOrderText)
                }
                addAll(font.split(entry.description, CAPTION_WIDTH))
            }
        }
    }


    private fun rebuild() {
        accent = ClientAbilityState.roster?.let { PathwayBranding.textOf(it.pathwayId).packed } ?: UiTheme.ACCENT
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
        if (caption.isEmpty()) updateCaption()
    }

    /** Same wording as the HUD: one-off, upkeep, or both. The spirituality icon is drawn, not typed. */
    private fun priceOf(entry: AbilityRosterPayload.Entry): Component? {
        val parts = buildList {
            if (entry.cost > 0) add("${entry.cost}")
            if (entry.upkeep > 0) add("${entry.upkeep}/с")
        }
        if (parts.isEmpty()) return if (entry.type == AbilityType.PERMANENT_PASSIVE) Component.literal("—") else null
        return Component.literal(parts.joinToString(" · "))
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
        if (cards.isNotEmpty()) skin.ring(graphics, centerX, centerY, WheelLayout.radius(cards.size), cards.size, alpha, accent)
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
                    UiTheme.price(graphics, font, it, card.critical, card.spirit, x + CARD / 2 - width / 2, y + CARD + 2, a)
                }
                card.numeral?.let {
                    val seal = if (index == hovered) alpha else a * NUMERAL_ALPHA
                    graphics.centeredText(font, it, centerX + card.numeralX, centerY + card.numeralY - font.lineHeight / 2, UiTheme.argb(accent, seal))
                }
                // The pathway colour appears in the hover outline, the hour marks and the skin's centre ornament.
                skin.marks(graphics, x, y, index == hovered, card.selected, card.critical, if (index == hovered) alpha else a, accent)
            }
        }

        // Centre: a cross-hair (a pin once pinned) in the hold ring, the page counter, then the caption below the ring.
        holdRing(graphics, now, alpha)
        if (pinned) pin(graphics, UiTheme.argb(UiTheme.ACCENT, alpha))
        else graphics.centeredText(font, "+", centerX, centerY - 4, UiTheme.argb(UiTheme.MUTED, alpha))
        pageLabel?.let { graphics.centeredText(font, it, centerX, centerY + 8, UiTheme.argb(UiTheme.MUTED, alpha)) }

        val ringBottom = centerY + WheelLayout.radius(cards.size) + CARD / 2 + font.lineHeight + 6
        caption.forEachIndexed { line, text ->
            val color = if (line == 0 && hovered >= 0) UiTheme.TEXT else UiTheme.MUTED
            graphics.centeredText(font, text, centerX, ringBottom + line * (font.lineHeight + 1), UiTheme.argb(color, alpha))
        }

        modePills(graphics, now, alpha)

        graphics.pose().popMatrix()

        // The spirituality bar was drawn before the screen, so the blur took it too. It is where
        // the cost of the card under the cursor is read, so it is drawn again, sharp, on top.
        graphics.nextStratum()
        SpiritualityHud.extractRenderState(graphics, minecraft.deltaTracker)
    }

    /**
     * The open column: each pill slides out of the card towards its place and fades in, eased
     * out over 120 ms after the dwell. Above everything else — it stands across a neighbour.
     */
    private fun modePills(graphics: GuiGraphicsExtractor, now: Float, alpha: Float) {
        val card = cards.getOrNull(hovered) ?: return
        if (!expanded()) return
        val open = easeOut((now - hoverSince - DWELL_TICKS) / EXPAND_TICKS)
        val a = alpha * open
        graphics.nextStratum()
        pills.forEachIndexed { index, (x, y, w, h) ->
            // Still at `1 - open` of the way from the card's centre to the pill's own.
            val back = 1f - open
            graphics.pose().pushMatrix()
            graphics.pose().translate(back * (card.dx - (x + w / 2)), back * (card.dy - (y + h / 2)))
            val left = centerX + x
            val top = centerY + y
            UiTheme.panel(graphics, left, top, w, h, a)
            val color = if (index == hoveredMode) UiTheme.TEXT else UiTheme.MUTED
            graphics.text(font, pillLabels[index], left + PILL_PADDING, top + 2, UiTheme.argb(color, a))
            if (index == hoveredMode) graphics.outline(left - 1, top - 1, w + 2, h + 2, UiTheme.argb(accent, a))
            graphics.pose().popMatrix()
        }
    }

    /**
     * The hold ring: a one-pixel circle round the centre, filled clockwise from the top between 1.5 s
     * and 6 s of holding, breathing while it fills; gold and whole once pinned.
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
        const val CAPTION_WIDTH = 220

        /** 100 ms from 0.88 to full size; 80 ms for the hovered card to reach 1.08. */
        const val OPEN_TICKS = 2f
        const val OPEN_SCALE = 0.88f
        const val HOVER_TICKS = 1.6f
        const val HOVER_SCALE = 1.08f

        /** 200 ms of rest on a card before its modes open, then 120 ms to slide out. */
        const val DWELL_TICKS = 4f
        const val EXPAND_TICKS = 2.4f
        const val PILL_PADDING = 3

        /** 150 ms for the world behind to blur and darken; how dark it ends up. */
        const val BACKDROP_TICKS = 3f
        const val DARKEN = 0.45f

        /** The hour marks stay quiet until their card is hovered. */
        const val NUMERAL_ALPHA = 0.6f

        /** The hold ring shows from 1.5 s and pins at 6 s; its radius, unlit track and breathing. */
        const val RING_TICKS = 30f
        const val PIN_TICKS = 120
        const val RING_RADIUS = 9
        const val TRACK_ALPHA = 0.25f
        const val BREATH_RATE = 0.5f
        const val PIN_PITCH = 1.8f
        const val PIN_VOLUME = 0.3f
    }
}
