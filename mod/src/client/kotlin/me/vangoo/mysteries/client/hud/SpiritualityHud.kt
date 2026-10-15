package me.vangoo.mysteries.client.hud

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.abs

/**
 * Spirituality: a bar at the top centre and the numbers under it.
 *
 * A separate element rather than a line in [AbilityHud], because their visibility rules differ.
 * The ability panel is a momentary reference: it appears on change and fades. Spirituality is a
 * resource the player watches constantly, like health; it never fades and does not hide behind
 * an open screen (the wheel in particular, where the cast cost is decided). It disappears only
 * on F1 and when the player is not a Beyonder.
 *
 * The pathway colour is deliberately absent here: it stays with the hairline under the ability
 * panel.
 *
 * The numbers are built on the tick — and only when they really changed. In the frame only the
 * bar's fraction is computed. The bar catches up with a new number in ~200 ms (half the gap per
 * tick, eased out) while the caption jumps at once: the number is the truth, the bar a gesture.
 */
object SpiritualityHud : HudElement {

    /** The width is measured with the text: the caption is longer than the bar, and the panel follows it. */
    private class Panel(val caption: Component, val critical: Boolean, val width: Int)

    private var shown: Pair<Int, Int>? = null
    private var panel: Panel? = null

    /** The bar's drawn fraction on this tick and the previous one, interpolated by `partialTick`. */
    private var level = -1f
    private var previousLevel = -1f

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("spirituality_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        val spirituality = ClientAbilityState.spirituality
        if (!shouldShow(client) || spirituality == null || spirituality.maximum <= 0) {
            shown = null
            panel = null
            level = -1f
            return
        }

        val target = spirituality.current.toFloat() / spirituality.maximum
        previousLevel = level
        level = when {
            // A first sight of the bar, or motion turned off: no catching up from nowhere.
            level < 0f || ClientOptions.reducedMotion -> target
            abs(target - level) < SNAP -> target
            else -> level + (target - level) * CATCH_UP
        }
        if (previousLevel < 0f) previousLevel = level

        val fresh = spirituality.current to spirituality.maximum
        if (fresh == shown) return

        val caption = Component.translatable(
            "hud.$MOD_ID.spirituality",
            spirituality.current,
            spirituality.maximum,
        )
        shown = fresh
        panel = Panel(
            caption = caption,
            critical = spirituality.isCritical(),
            width = maxOf(BAR_WIDTH, client.font.width(caption)) + PADDING * 2,
        )
    }

    private fun shouldShow(client: Minecraft): Boolean =
        ClientOptions.hud != HudMode.NEVER &&
            ClientOptions.showSpirituality &&
            client.player != null &&
            !client.gui.hud.isHidden() &&
            ClientAbilityState.isBeyonder

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val panel = panel ?: return
        val spirituality = ClientAbilityState.spirituality ?: return

        val alpha = ClientOptions.opacity
        val scale = ClientOptions.hudScale
        val font = Minecraft.getInstance().font

        graphics.pose().pushMatrix()
        graphics.pose().scale(scale, scale)

        val screenWidth = (graphics.guiWidth() / scale).toInt()
        val width = panel.width
        val height = PADDING * 2 + BAR_HEIGHT + CAPTION_GAP + font.lineHeight
        val x = (screenWidth - width) / 2
        val y = TOP_MARGIN

        UiTheme.panel(graphics, x, y, width, height, alpha)

        // The bar is narrower than the caption, so it is centred on the panel rather than set from the edge.
        val barX = x + (width - BAR_WIDTH) / 2
        val barY = y + PADDING
        val drawn = previousLevel + (level - previousLevel) * delta.getGameTimeDeltaPartialTick(false)
        val fill = if (panel.critical) UiTheme.CRITICAL else UiTheme.ACCENT
        UiTheme.bar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, drawn, fill, alpha)

        // The stretch the selected ability will cost: what will be left is visible in advance.
        costOfSelected()?.let { cost ->
            val after = (spirituality.current - cost).toFloat() / spirituality.maximum
            UiTheme.barSlice(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, after, drawn, UiTheme.ACCENT_DEEP, alpha)
        }

        graphics.centeredText(
            font,
            panel.caption,
            x + width / 2,
            barY + BAR_HEIGHT + CAPTION_GAP,
            UiTheme.argb(if (panel.critical) UiTheme.CRITICAL else UiTheme.TEXT, alpha),
        )

        graphics.pose().popMatrix()
    }

    /** The price of what is selected now — or `null` if there is nothing to pay for or nothing to pay with. */
    private fun costOfSelected(): Int? {
        val entry = ClientAbilityState.selected?.let(ClientAbilityState::entry) ?: return null
        if (entry.type != AbilityType.ACTIVE || entry.cost <= 0) return null
        return entry.cost.takeIf { ClientAbilityState.canAfford(entry) }
    }

    private const val PADDING = 4
    private const val TOP_MARGIN = 4
    private const val BAR_WIDTH = 90
    private const val BAR_HEIGHT = 4
    private const val CAPTION_GAP = 2

    /** Half the gap per tick: ~94 % of the way in four ticks, i.e. 200 ms. */
    private const val CATCH_UP = 0.5f
    private const val SNAP = 0.002f
}
