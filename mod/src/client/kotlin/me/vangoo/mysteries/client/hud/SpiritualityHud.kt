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

/**
 * Духовність: бар угорі по центру й числа під ним.
 *
 * Окремий елемент, а не рядок в [AbilityHud], бо в них різні правила видимості.
 * Панель здібності — довідка на мить: проявляється на зміну й згасає. Духовність —
 * ресурс, за яким гравець стежить постійно, як за здоров'ям; вона не згасає ніколи й
 * не ховається за відкритим екраном (зокрема за колесом, де ціна касту й вирішується).
 * Зникає лише на F1 і коли гравець не Потойбічний.
 *
 * Кольору шляху тут немає навмисно: він лишається за волоском під панеллю здібності.
 *
 * Числа будуються в такті — і лише коли справді змінились. У кадрі рахується
 * тільки частка бару.
 */
object SpiritualityHud : HudElement {

    /** Ширина рахується разом із текстом: підпис довший за бар, і панель міряється по ньому. */
    private class Panel(val caption: Component, val critical: Boolean, val width: Int)

    private var shown: Pair<Int, Int>? = null
    private var panel: Panel? = null

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("spirituality_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        val spirituality = ClientAbilityState.spirituality
        if (!shouldShow(client) || spirituality == null || spirituality.maximum <= 0) {
            shown = null
            panel = null
            return
        }

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

        // Бар вужчий за підпис, тож ставиться по центру панелі, а не від краю.
        val barX = x + (width - BAR_WIDTH) / 2
        val barY = y + PADDING
        val level = spirituality.current.toFloat() / spirituality.maximum
        val fill = if (panel.critical) UiTheme.CRITICAL else UiTheme.ACCENT
        UiTheme.bar(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, level, fill, alpha)

        // Ділянка, якої коштуватиме обрана здібність: видно наперед, що лишиться.
        costOfSelected()?.let { cost ->
            val after = (spirituality.current - cost).toFloat() / spirituality.maximum
            UiTheme.barSlice(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, after, level, UiTheme.ACCENT_DEEP, alpha)
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

    /** Ціна того, що зараз обрано, — або `null`, якщо платити нема за що чи нема чим. */
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
}
