package me.vangoo.mysteries.client.hud

import me.vangoo.domain.hud.HudPart

import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.MarionetteStatePayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * The row of threads: one card per marionette, above the hotbar.
 *
 * This is the answer to "a menu is not a way to command a squad". The plugin opened a six-row
 * chest listing the caster's puppets as player heads, and every switch cost two clicks and a
 * closed inventory — in a fight, an eternity. A row that is simply **there**, with the mouse wheel
 * stepping along it while the thread key is held, costs no clicks at all and never covers the
 * world.
 *
 * Each card answers the four things worth knowing at a glance: who it is, how hurt it is, what it
 * is doing, and how far away. A puppet out of control replaces the last two with its countdown,
 * in red — the one number that means *go there now*.
 *
 * ## Nothing here is decided
 *
 * Every value came from the server in `MarionetteStatePayload`. The rebuild happens on the tick,
 * not the frame, and only when the payload actually changed — a packet a second against sixty
 * frames, so the frame does geometry and nothing else.
 */
object MarionetteHud : HudElement {

    private class Card(
        val name: Component,
        val status: Component,
        val statusColor: Int,
        val health: Float,
        val selected: Boolean,
        val possessed: Boolean,
        val strained: Boolean,
    )

    private var built: MarionetteStatePayload? = null
    private var cards: List<Card> = emptyList()
    private val examples = (1..3).map { i -> Card(
        Component.translatable("hud.mysteries_above.preview_marionette", i),
        Component.translatable("hud.mysteries_above.preview_order"), UiTheme.MUTED,
        .75f, i == 1, false, false)
    }

    private fun shownCards(preview: Boolean) = if (preview && cards.isEmpty()) examples else cards

    private fun columns(screenWidth: Int, preview: Boolean) = minOf(shownCards(preview).size,
        ((screenWidth - 8) / (ClientOptions.hudScale * (CARD_WIDTH + CARD_GAP))).toInt().coerceAtLeast(1))

    fun dimensions(screenWidth: Int, screenHeight: Int, preview: Boolean = false): Pair<Int, Int>? {
        val count = shownCards(preview).size
        if (count == 0) return null
        val columns = columns(screenWidth, preview)
        val rows = (count + columns - 1) / columns
        return columns * (CARD_WIDTH + CARD_GAP) - CARD_GAP to rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP
    }

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("marionette_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register { rebuildIfChanged() }
    }

    private fun rebuildIfChanged() {
        val state = ClientMarionettes.state
        if (state === built) return
        built = state
        cards = state.entries.map { entry -> card(entry, state) }
    }

    private fun card(entry: MarionetteStatePayload.Entry, state: MarionetteStatePayload): Card {
        val strained = entry.strandSeconds > 0
        val status = when {
            strained -> Component.literal("нитка рветься ${entry.strandSeconds / 60}:${"%02d".format(entry.strandSeconds % 60)}")
            state.possessing == entry.id -> Component.literal("ви всередині")
            entry.distance < 0 -> Component.literal(entry.order.caption)
            else -> Component.literal("${entry.order.caption} · ${entry.distance} бл.")
        }
        return Card(
            name = Component.literal(entry.name.take(NAME_CHARS)),
            status = status,
            statusColor = if (strained) UiTheme.CRITICAL else UiTheme.MUTED,
            health = if (entry.maxHealth > 0f) entry.health / entry.maxHealth else 0f,
            selected = state.selected == entry.id,
            possessed = state.possessing == entry.id,
            strained = strained,
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (cards.isEmpty() || !visible()) return
        val box = HudPlacement.boxes(graphics.guiWidth(), graphics.guiHeight())[HudPart.MARIONETTES] ?: return
        draw(graphics, box)
    }

    fun draw(graphics: GuiGraphicsExtractor, box: HudBox, preview: Boolean = false) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(box.rect.x.toFloat(), box.rect.y.toFloat())
        graphics.pose().scale(box.scale, box.scale)
        val columns = columns(graphics.guiWidth(), preview)
        shownCards(preview).forEachIndexed { index, card ->
            draw(graphics, card, (index % columns) * (CARD_WIDTH + CARD_GAP), (index / columns) * (CARD_HEIGHT + CARD_GAP))
        }

        graphics.pose().popMatrix()
    }

    private fun visible(): Boolean {
        val client = Minecraft.getInstance()
        return ClientOptions.hud != HudMode.NEVER &&
            client.player != null &&
            client.gui.screen() == null &&
            !client.gui.hud.isHidden() &&
            ClientMarionettes.hasThreads
    }

    private fun draw(graphics: GuiGraphicsExtractor, card: Card, x: Int, y: Int) {
        val font = Minecraft.getInstance().font
        val alpha = ClientOptions.opacity

        UiTheme.panel(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, alpha)

        val nameColor = if (card.possessed) UiTheme.ACCENT else UiTheme.TEXT
        graphics.text(font, card.name, x + PADDING + MARK_WIDTH, y + PADDING, UiTheme.argb(nameColor, alpha))

        // The selected card carries the only mark, and it is the same arrow the ability panel uses
        // for "this is the one in your hand".
        if (card.selected) {
            graphics.text(font, MARK, x + PADDING, y + PADDING, UiTheme.argb(UiTheme.ACCENT, alpha))
        }

        val barColor = if (card.strained) UiTheme.CRITICAL else UiTheme.ACCENT
        UiTheme.bar(graphics, x + PADDING, y + BAR_Y, CARD_WIDTH - PADDING * 2, BAR_HEIGHT, card.health, barColor, alpha)

        val textScale = minOf(1f, (CARD_WIDTH - PADDING * 2).toFloat() / font.width(card.status).coerceAtLeast(1))
        graphics.pose().pushMatrix()
        graphics.pose().translate((x + PADDING).toFloat(), (y + STATUS_Y).toFloat())
        graphics.pose().scale(textScale, textScale)
        graphics.text(font, card.status, 0, 0, UiTheme.argb(card.statusColor, alpha))
        graphics.pose().popMatrix()
    }

    private val MARK: Component = Component.literal("▸")

    private const val MARK_WIDTH = 6
    private const val NAME_CHARS = 12
    private const val CARD_WIDTH = 84
    private const val CARD_HEIGHT = 34
    private const val CARD_GAP = 3
    private const val PADDING = 4

    private const val BAR_Y = 15
    private const val BAR_HEIGHT = 3
    private const val STATUS_Y = 22
}
