package me.vangoo.mysteries.client.hud

import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The Seer's dream as they see it: the world near the mark, grey and blurred, easing in and out —
 * instead of the black of sleep. The haze says how clearly the spirit world showed it.
 *
 * The blur is the screen's own, and a frame may blur once: a screen opened over the dream (they all
 * draw after the HUD) blurs instead, and the grey alone stays.
 */
object DreamVision : HudElement {

    private var left = 0
    private var total = 0
    private var haze = 0f

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, modId("dream_vision"), this)
    }

    fun accept(payload: WorldEffectPayload.Vision) {
        left = payload.ticks
        total = payload.ticks
        haze = payload.haze
    }

    fun tick() {
        if (left > 0) left--
    }

    fun clear() {
        left = 0
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (left <= 0) return
        val remaining = left - delta.getGameTimeDeltaPartialTick(false)
        val fade = minOf((total - remaining) / EASE, remaining / EASE, 1f).coerceAtLeast(0f)
        if (Minecraft.getInstance().gui.screen() == null) graphics.blurBeforeThisStratum()
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), UiTheme.argb(GREY, haze * fade))
        // The edges darker: a glimpse, not a window.
        val band = graphics.guiHeight() / EDGE_SHARE
        val edge = UiTheme.argb(UiTheme.PANEL, EDGE_ALPHA * fade)
        graphics.fill(0, 0, graphics.guiWidth(), band, edge)
        graphics.fill(0, graphics.guiHeight() - band, graphics.guiWidth(), graphics.guiHeight(), edge)
    }

    private const val GREY = 0x8C8C92
    private const val EASE = 6f
    private const val EDGE_SHARE = 8
    private const val EDGE_ALPHA = 0.55f
}
