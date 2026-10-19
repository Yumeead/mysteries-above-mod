package me.vangoo.mysteries.client.render

import me.vangoo.domain.hud.OrbMask
import me.vangoo.domain.hud.TagAttachment
import com.mojang.blaze3d.platform.NativeImage
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import kotlin.math.sin

fun orbGlowPulse(time: Float, reducedMotion: Boolean): Float =
    if (reducedMotion) 1f else .8f + .2f * sin(time / 16f)

fun orbFloatOffset(time: Float, reducedMotion: Boolean): Float =
    if (reducedMotion) 0f else .6f * sin(time / 24f)

/** Adapts only the mask and exterior layers; the existing liquid renderer receives its original paintings. */
object SpiritualityOrb {
    private val blank = modId("bottom_panel/blank")
    private val layers = listOf(
        modId("bottom_panel/orb_frame_28"),modId("bottom_panel/orb_backing_28"),
        modId("bottom_panel/orb_glass_28"),modId("bottom_panel/orb_glow_28"))
    private val engravings = listOf("Mother","Justiciar").associateWith {
        modId("bottom_panel/engraving_${it.lowercase()}") }
    private var source: FlaskStyle? = null
    private var masked: FlaskStyle? = null
    private var attachment = TagAttachment(40.5f, 13.5f)

    private fun masked(style: FlaskStyle): FlaskStyle {
        if (source !== style) {
            val texture = Identifier.fromNamespaceAndPath(style.tag.namespace, "textures/gui/sprites/${style.tag.path}.png")
            attachment = Minecraft.getInstance().resourceManager.open(texture).use { stream ->
                NativeImage.read(stream).use { image ->
                    TagAttachment.find(image.width, image.height) { x, y -> image.getPixel(x,y) ushr 24 > 0 }
                }
            }
            source = style
            val rows = List(style.height) { y ->
                val pixels = (OrbMask.x until OrbMask.x + OrbMask.size).filter { x -> OrbMask.contains(x,y) }
                if (pixels.isEmpty()) emptyList() else listOf(pixels.first()..pixels.last())
            }
            masked = style.copy(frame = blank, glass = blank, backing = blank, neck = blank,
                tag = blank, engraving = blank, rows = rows, tagSwing = 0f)
        }
        return checkNotNull(masked)
    }

    fun draw(graphics: GuiGraphicsExtractor, style: FlaskStyle, pathway: String?, x: Int, y: Int,
             level: Float, trail: Float, time: Float, low: Boolean, gaining: Boolean,
             reducedMotion: Boolean, opacity: Float, movement: Float, tagAngle: Float = 0f) {
        val styleMasked = masked(style)
        val size = OrbMask.size
        val (frame,backing,glass,glow) = layers
        graphics.pose().pushMatrix()
        graphics.pose().translate(0f, orbFloatOffset(time, reducedMotion))
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, glow,
            x - 9, y - 9, size + 20, size + 20,
            UiTheme.argb(style.palette.last(), opacity * (.4f + .6f * level.coerceIn(0f, 1f)) *
                orbGlowPulse(time, reducedMotion)))
        graphics.nextStratum()
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame,
            x - 3, y - 3, size + 8, size + 8)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, backing, x + 1, y + 1, size, size)
        SpiritualityFlask.draw(graphics, styleMasked, x + 1 - OrbMask.x, y + 1 - OrbMask.y, level, trail, time,
            low, gaining, reducedMotion, opacity, movement, false)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, glass, x + 1, y + 1, size, size,
            UiTheme.argb(0xFFFFFF, .45f + .3f * level))
        if (pathway == "Mother" || pathway == "Justiciar") {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, engravings.getValue(pathway),
                x + 1, y + 1, 28, 28)
        } else if (pathway != null && pathway != "Fool") {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.engraving, x - 7, y - 15,
                style.width, style.height, UiTheme.argb(0xFFFFFF, .32f))
        }
        // The tag hangs in front of the entire globe, including glass, engraving and liquid.
        graphics.nextStratum()
        val pivotX = x + 26.5f
        val pivotY = y + 1.5f
        graphics.pose().pushMatrix()
        graphics.pose().translate(pivotX, pivotY)
        graphics.pose().rotate(if (reducedMotion) 0f else tagAngle)
        graphics.pose().translate(-attachment.x, -attachment.y)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.tag, 0, 0, style.width, style.height)
        graphics.pose().popMatrix()
        graphics.pose().popMatrix()
    }
}
