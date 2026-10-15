package me.vangoo.mysteries.client.screen

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * The ability wheel: hold the cast key, point, let go.
 *
 * It lives only while [key] is held. Releasing it uses the card under the cursor (`SELECT_AND_CAST`,
 * or `TOGGLE` for a switch — see `ModKeys.use`), or nothing from the dead centre.
 *
 * It shows the active group. The mouse wheel walks the pages of every group in turn (a group of
 * more than twelve takes several), and the group of the page it stops on becomes the active one —
 * its hotkeys with it. Groups and keys are set up in [AbilityBookScreen]; the wheel only uses them. The screen closes at once and draws nothing as
 * "cast": the truth comes back with `ABILITY_STATE`, and a refusal with the usual action-bar line.
 *
 * The client dims what it thinks is unavailable but never withholds the request — the server
 * decides. The one exception is a permanent passive, which has nothing to cast at all.
 *
 * Built from [ClientAbilityState]'s cache, so opening it costs no round trip. Cards are rebuilt
 * on the tick (cooldown seconds change at most 20 times a second), the frame only reads them.
 */
class AbilityWheelScreen(private val key: KeyMapping) : Screen(Component.translatable("wheel.$MOD_ID.title")) {

    private class Card(
        val entry: AbilityRosterPayload.Entry,
        val dx: Int,
        val dy: Int,
        val price: Component?,
        val critical: Boolean,
        val marker: Component?,
        val cooldown: Component?,
        val remaining: Float,
        val dim: Boolean,
        val slot: Component?,
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

    private val centerX get() = width / 2
    private val centerY get() = height / 2

    override fun init() {
        rebuild()
    }

    override fun isPauseScreen(): Boolean = false

    /** No blur: the wheel is used mid-fight, and the world must stay readable behind it. */
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun tick() {
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
            updateCaption()
        }
        rebuild()
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        // The wheel is drawn scaled about its centre, so the cursor is measured in the same units.
        val scale = ClientOptions.hudScale
        hover(WheelLayout.pick(
            (mouseX - centerX) / scale,
            (mouseY - centerY) / scale,
            onPage().size,
            ClientOptions.wheelDeadZoneRatio,
        ))
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
            event.isRight && size > 0 -> hover(Math.floorMod(hovered + 1, size))
            event.isLeft && size > 0 -> hover(Math.floorMod(if (hovered < 0) -1 else hovered - 1, size))
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
        updateCaption()
    }

    /** Next or previous page; the group of the page becomes the active one, its hotkeys with it. */
    private fun flip(step: Int): Boolean {
        if (pages.size <= 1) return false
        page = Math.floorMod(page + step, pages.size)
        ClientOptions.activeGroup = pages[page].group
        hovered = -1
        updateCaption()
        rebuild()
        return true
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (!key.matches(event)) return super.keyReleased(event)
        commit()
        return true
    }

    /** The cast key may be bound to a mouse button (Mouse4) — release works the same way. */
    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!key.matchesMouse(event)) return super.mouseReleased(event)
        commit()
        return true
    }

    private fun commit() {
        hoveredEntry()?.let { ModKeys.use(it.identity) }
        onClose()
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
        caption = when {
            entry == null -> listOf(Component.translatable("wheel.$MOD_ID.cancel").visualOrderText)
            ClientOptions.showDescriptions ->
                listOf(entry.name.visualOrderText) + font.split(entry.description, CAPTION_WIDTH)
            else -> listOf(entry.name.visualOrderText)
        }
    }

    private fun rebuild() {
        accent = ClientAbilityState.roster?.let { PathwayBranding.textOf(it.pathwayId).packed } ?: UiTheme.ACCENT
        val shown = onPage()
        cards = shown.mapIndexed { index, entry ->
            val (dx, dy) = WheelLayout.offset(index, shown.size)
            val seconds = ClientAbilityState.remainingSeconds(entry.identity)
            val affordable = ClientAbilityState.canAfford(entry)
            Card(
                entry = entry,
                dx = dx,
                dy = dy,
                price = priceOf(entry, affordable),
                critical = entry.type == AbilityType.ACTIVE && !affordable,
                marker = when {
                    ClientAbilityState.isToggled(entry.identity) -> Component.literal("●")
                    entry.type == AbilityType.TOGGLEABLE_PASSIVE -> Component.literal("○")
                    ClientAbilityState.isSustaining(entry.identity) -> Component.literal("◐")
                    else -> null
                },
                cooldown = if (seconds > 0 && ClientOptions.showCooldownNumbers) Component.literal("$seconds") else null,
                remaining = 1f - ClientAbilityState.cooldownProgress(entry),
                dim = entry.type == AbilityType.PERMANENT_PASSIVE,
                slot = ModKeys.hotkeyLabel(entry.identity, ClientOptions.groups.getOrElse(pages[page].group) { ClientOptions.group }),
            )
        }
        pageLabel = pages[page].label
        if (caption.isEmpty()) updateCaption()
    }

    /** Same wording as the HUD: one-off, upkeep, or both. A shape (✕) as well as a colour. */
    private fun priceOf(entry: AbilityRosterPayload.Entry, affordable: Boolean): Component? {
        val parts = buildList {
            if (entry.cost > 0) add("✦ ${entry.cost}")
            if (entry.upkeep > 0) add("✦ ${entry.upkeep}/с")
        }
        if (parts.isEmpty()) return if (entry.type == AbilityType.PERMANENT_PASSIVE) Component.literal("—") else null
        val text = parts.joinToString(" · ")
        return Component.literal(if (entry.type == AbilityType.ACTIVE && !affordable) "✕ $text" else text)
    }

    // ── Frame ───────────────────────────────────────────────────────────────────

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val alpha = ClientOptions.opacity

        // The same size multiplier as the HUD, applied about the wheel's centre.
        val scale = ClientOptions.hudScale
        graphics.pose().pushMatrix()
        graphics.pose().translate(centerX.toFloat(), centerY.toFloat())
        graphics.pose().scale(scale, scale)
        graphics.pose().translate(-centerX.toFloat(), -centerY.toFloat())

        // Pass one: panels and icons.
        for (card in cards) {
            val x = centerX + card.dx - CARD / 2
            val y = centerY + card.dy - CARD / 2
            val a = if (card.dim) alpha * DIM else alpha
            UiTheme.panel(graphics, x, y, CARD, CARD, a)
            graphics.item(card.entry.icon, x + ICON_INSET, y + ICON_INSET)
        }

        // Items go in their own pass; everything that must lie over the icons needs a new stratum.
        graphics.nextStratum()

        cards.forEachIndexed { index, card ->
            val x = centerX + card.dx - CARD / 2
            val y = centerY + card.dy - CARD / 2
            val a = if (card.dim) alpha * DIM else alpha

            UiTheme.cooldownVeil(graphics, x + ICON_INSET, y + ICON_INSET, ICON, card.remaining, a)
            card.cooldown?.let { graphics.centeredText(font, it, x + CARD / 2, y + CARD / 2 - 4, UiTheme.argb(UiTheme.TEXT, a)) }
            card.marker?.let { graphics.text(font, it, x + CARD - 5, y - 3, UiTheme.argb(UiTheme.ACCENT, a)) }
            card.slot?.let { graphics.text(font, it, x - 2, y - 3, UiTheme.argb(UiTheme.MUTED, a)) }
            card.price?.let {
                val color = if (card.critical) UiTheme.CRITICAL else UiTheme.MUTED
                graphics.centeredText(font, it, x + CARD / 2, y + CARD + 2, UiTheme.argb(color, a))
            }
            if (index == hovered) {
                // The pathway colour appears here and nowhere else in the wheel.
                graphics.outline(x - 1, y - 1, CARD + 2, CARD + 2, UiTheme.argb(accent, alpha))
            }
        }

        // Centre: a cross-hair, the page counter, then the caption below the ring.
        graphics.centeredText(font, "+", centerX, centerY - 4, UiTheme.argb(UiTheme.MUTED, alpha))
        pageLabel?.let { graphics.centeredText(font, it, centerX, centerY + 8, UiTheme.argb(UiTheme.MUTED, alpha)) }

        val ringBottom = centerY + WheelLayout.radius(cards.size) + CARD / 2 + font.lineHeight + 6
        caption.forEachIndexed { line, text ->
            val color = if (line == 0 && hovered >= 0) UiTheme.TEXT else UiTheme.MUTED
            graphics.centeredText(font, text, centerX, ringBottom + line * (font.lineHeight + 1), UiTheme.argb(color, alpha))
        }

        graphics.pose().popMatrix()
    }

    private companion object {
        const val CARD = 24
        const val ICON = 16
        const val ICON_INSET = (CARD - ICON) / 2
        const val DIM = 0.45f
        const val CAPTION_WIDTH = 220
    }
}
