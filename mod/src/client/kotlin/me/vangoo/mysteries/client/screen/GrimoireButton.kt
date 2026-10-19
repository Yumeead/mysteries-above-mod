package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.client.render.GrimoireSkin
import me.vangoo.mysteries.client.render.UiTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class GrimoireButton(x: Int, y: Int, width: Int, height: Int, message: Component,
                     private val selected: () -> Boolean = { false }, private val icon: Identifier? = null,
                     private val goldSelection: Boolean = false,
                     private val action: () -> Unit) : AbstractButton(x, y, width, height, message) {
    override fun onPress(input: InputWithModifiers) = action()

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val on = selected()
        val background = if (goldSelection) { if (on) 0x30281D else 0x14191F } else if (on) GrimoireSkin.BURGUNDY else 0x191519
        val edge = if (goldSelection) { if (isHoveredOrFocused || on) 0xDFC28B else 0xB99A61 }
            else if (isHoveredOrFocused || on) GrimoireSkin.GOLD else GrimoireSkin.EDGE
        graphics.fill(x, y, x + width, y + height, UiTheme.argb(background, 1f))
        graphics.outline(x, y, width, height, UiTheme.argb(edge, 1f))
        if (goldSelection && on) graphics.fill(x + 3, y + 5, x + 5, y + height - 5, UiTheme.argb(0xDFC28B, 1f))
        val alpha = if (active) 1f else .4f
        if (icon != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + (width - 16) / 2,
            y + (height - 16) / 2, 16, 16, UiTheme.argb(0xFFFFFF, alpha))
        else {
            val font = Minecraft.getInstance().font
            val scale = if (goldSelection) 1f else minOf(1f, (width - 8).toFloat() / font.width(message).coerceAtLeast(1))
            graphics.pose().pushMatrix()
            if (goldSelection) graphics.pose().translate((x + width / 2).toFloat(), (y + (height - 9) / 2).toFloat())
            else graphics.pose().translate(x + width / 2f, y + (height - 9 * scale) / 2f)
            graphics.pose().scale(scale, scale)
            graphics.text(font, message, -font.width(message) / 2, 0,
                UiTheme.argb(if (on) UiTheme.TEXT else UiTheme.MUTED, alpha))
            graphics.pose().popMatrix()
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
        if (selected()) output.add(NarratedElementType.HINT, Component.translatable("hud.mysteries_above.selected"))
    }
}
