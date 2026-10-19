package me.vangoo.mysteries.client.render

import me.vangoo.domain.hud.OrbMask
import me.vangoo.domain.hud.TagAttachment
import com.mojang.blaze3d.platform.NativeImage
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/** Adapts only the mask and exterior layers; the existing liquid renderer receives its original paintings. */
object SpiritualityOrb {
    private val blank = modId("bottom_panel/blank")
    private val layers = (8..28 step 2).associateWith { size -> listOf(
        modId("bottom_panel/orb_frame_$size"),modId("bottom_panel/orb_backing_$size"),modId("bottom_panel/orb_glass_$size")) }
    private val engravings = listOf("Mother","Justiciar").associateWith {
        modId("bottom_panel/engraving_${it.lowercase()}") }
    private var source: FlaskStyle? = null
    private var masked: FlaskStyle? = null
    private var window = OrbMask(17, 22, 28)
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
            window = style.paintings.maxByOrNull { it.level }?.let { OrbMask.fit(it.pixels) } ?: OrbMask(17,22,28)
            val rows = List(style.height) { y ->
                val pixels = (window.x until window.x + window.size).filter { x -> window.contains(x,y) }
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
        val size = window.size
        val margin = (28 - size) / 2
        val inside = 1 + margin
        val (frame,backing,glass) = layers.getValue(size)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame,
            x - 3 + margin, y - 3 + margin, size + 8, size + 8)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, backing, x + inside, y + inside, size, size)
        SpiritualityFlask.draw(graphics, styleMasked, x + inside - window.x, y + inside - window.y, level, trail, time,
            low, gaining, reducedMotion, opacity, movement, false)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, glass, x + inside, y + inside, size, size,
            UiTheme.argb(0xFFFFFF, .45f + .3f * level))
        if (pathway == "Mother" || pathway == "Justiciar") {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, engravings.getValue(pathway),
                x + 1, y + 1, 28, 28)
        } else if (pathway != null && pathway != "Fool") {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.engraving, x - 7, y - 15,
                style.width, style.height, UiTheme.argb(0xFFFFFF, .32f))
        }
        graphics.fill(x + 31 - margin, y + 12, x + 32 - margin, y + 18, UiTheme.argb(style.palette.last(), 1f))
        // The tag hangs in front of the entire globe, including glass, engraving and liquid.
        graphics.nextStratum()
        val pivotX = x + 26.5f - margin
        val pivotY = y + 1.5f + margin
        graphics.pose().pushMatrix()
        graphics.pose().translate(pivotX, pivotY)
        graphics.pose().rotate(if (reducedMotion) 0f else tagAngle)
        graphics.pose().translate(-attachment.x, -attachment.y)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.tag, 0, 0, style.width, style.height)
        graphics.pose().popMatrix()
    }
}
