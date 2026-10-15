package me.vangoo.mysteries.client.screen

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
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
 * It lives only while [key] is held. Releasing it sends `SELECT_AND_CAST` for the card under the
 * cursor, or nothing from the dead centre. The screen closes at once and draws nothing as
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
    )

    private var entries: List<AbilityRosterPayload.Entry> = ClientAbilityState.entries
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
            page = page.coerceAtMost(WheelLayout.pages(entries.size) - 1)
            hovered = kept?.let { id -> onPage().indexOfFirst { it.identity == id } } ?: -1
            updateCaption()
        }
        rebuild()
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val picked = WheelLayout.pick(
            mouseX - centerX,
            mouseY - centerY,
            onPage().size,
            ClientOptions.wheelDeadZoneRatio,
        )
        if (picked != hovered) {
            hovered = picked
            updateCaption()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val pages = WheelLayout.pages(entries.size)
        if (pages <= 1 || scrollY == 0.0) return false
        page = Math.floorMod(page + if (scrollY < 0) 1 else -1, pages)
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
        val entry = hoveredEntry()
        if (entry != null && entry.type != AbilityType.PERMANENT_PASSIVE) {
            ModChannels.ABILITY_ACTION.sendToServer(
                AbilityActionPayload(AbilityActionPayload.Action.SELECT_AND_CAST, entry.identity),
            )
        }
        onClose()
    }

    // ── State ───────────────────────────────────────────────────────────────────

    private fun startPage(): Int {
        val index = entries.indexOfFirst { it.identity == ClientAbilityState.selected }
        return if (index < 0) 0 else index / WheelLayout.PAGE_SIZE
    }

    private fun onPage(): List<AbilityRosterPayload.Entry> =
        entries.drop(page * WheelLayout.PAGE_SIZE).take(WheelLayout.PAGE_SIZE)

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
            )
        }
        val pages = WheelLayout.pages(entries.size)
        pageLabel = if (pages > 1) Component.literal("${page + 1} / $pages") else null
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
    }

    private companion object {
        const val CARD = 24
        const val ICON = 16
        const val ICON_INSET = (CARD - ICON) / 2
        const val DIM = 0.45f
        const val CAPTION_WIDTH = 220
    }
}
