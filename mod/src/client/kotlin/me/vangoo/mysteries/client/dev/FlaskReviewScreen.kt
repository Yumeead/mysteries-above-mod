package me.vangoo.mysteries.client.dev

import me.vangoo.domain.PathwayBranding
import me.vangoo.mysteries.client.hud.SpiritualityHud
import me.vangoo.mysteries.client.render.FlaskStyles
import me.vangoo.mysteries.client.render.SpiritualityFlask
import me.vangoo.mysteries.client.render.SpiritualityOrb
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Dev-only contact sheet using the production renderer and numbers over the actual world. */
class FlaskReviewScreen(private val level: Float, private val backing: Boolean,
                       private val showNumbers: Boolean = true, private val referenceBackground: Boolean = false,
                       private val orb: Boolean = false) : Screen(Component.literal("Flask review")) {
    override fun isPauseScreen() = false
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (referenceBackground) {
            graphics.fill(0, 0, width, height, 0xFF242329.toInt())
            graphics.nextStratum()
        }
        graphics.text(font, "${(level * 1000).toInt() / 10f}% / shadow=$backing", 8, 4, -1, true)
        PathwayBranding.names.sorted().forEachIndexed { index, pathway ->
            val style = checkNotNull(FlaskStyles.of(pathway))
            check(style.tag.path == "spirituality/${pathway.lowercase()}/tag") { "Fallback art used for $pathway" }
            val x = (width - 456) / 2 + index % 6 * 76
            val y = 19 + index / 6 * 60
            graphics.pose().pushMatrix()
            graphics.pose().translate(x.toFloat(), y.toFloat())
            graphics.pose().pushMatrix()
            graphics.pose().scale(.65f, .65f)
            graphics.text(font, pathway, 0, 0, -1, true)
            graphics.pose().popMatrix()
            if (orb) SpiritualityOrb.draw(graphics, style, pathway, 16, 15, level, level, 0f,
                false, false, true, .85f, 0f)
            else SpiritualityFlask.draw(graphics, style, 0, 4, level, level, 0f,
                false, false, true, .85f, 0f, backing)
            graphics.pose().translate(0f, 4f)
            if (showNumbers) SpiritualityHud.drawFlaskNumbers(graphics, (14001 * level).toInt().toString(), "/14001")
            graphics.pose().popMatrix()
            graphics.nextStratum()
        }
    }
}
