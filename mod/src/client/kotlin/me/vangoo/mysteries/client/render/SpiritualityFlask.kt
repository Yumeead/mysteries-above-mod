package me.vangoo.mysteries.client.render

import me.vangoo.domain.hud.FlaskLiquid
import me.vangoo.domain.hud.LiquidPaintingBlend
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

object SpiritualityFlask {
    fun draw(graphics: GuiGraphicsExtractor, style: FlaskStyle, x: Int, y: Int,
             level: Float, trail: Float, time: Float, low: Boolean, gaining: Boolean,
             reducedMotion: Boolean, opacity: Float, movement: Float, backing: Boolean) {
        val clock = if (reducedMotion) 0f else time
        val alpha = opacity * if (low) .72f + sin(clock / 31f) * .015f else 1f
        val height = style.bottom - style.top + 1
        val filled = FlaskLiquid.filledRows(height, level)
        val surface = style.bottom + 1 - filled
        val spentSurface = style.bottom + 1 - FlaskLiquid.filledRows(height, trail)
        val surfaceSpan = style.rows.getOrNull(surface)?.firstOrNull()
        fun sprite(id: net.minecraft.resources.Identifier) =
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, style.width, style.height,
                UiTheme.argb(0xFFFFFF, alpha))
        fun tag() {
            graphics.pose().pushMatrix()
            graphics.pose().translate(x + 40f, y + 15f)
            graphics.pose().rotate(if (reducedMotion) 0f else sin(clock / 19f) * movement * style.tagSwing)
            graphics.pose().translate(-x - 40f, -y - 15f)
            sprite(style.tag)
            graphics.pose().popMatrix()
        }
        if (backing) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.backing, x, y,
                style.width, style.height, UiTheme.argb(0xFFFFFF, opacity))
            graphics.nextStratum()
        }
        if (style.paintings.isNotEmpty()) {
            sprite(style.frame)
            sprite(style.engraving)
            sprite(style.glass)
            graphics.nextStratum()
            val painting = LiquidPaintingBlend.at(style.paintings, level, style.bottom)
            val spent = if (trail > level) LiquidPaintingBlend.at(style.paintings, trail, style.bottom) else null
            for (row in style.top..style.bottom) for (span in style.rows[row]) {
                var run = span.first
                var previous = 0
                for (column in span.first..span.last + 1) {
                    var color = if (column <= span.last) painting?.color(column, row, style.bottom) ?: 0 else 0
                    if (color == 0 && column <= span.last && spent != null) {
                        val old = spent.color(column, row, style.bottom)
                        if (old ushr 24 > 0) color = (old and 0xFFFFFF) or (((old ushr 24) * .17f).roundToInt() shl 24)
                    }
                    if (column > span.first && color != previous) {
                        if (previous != 0) graphics.fill(x + run, y + row, x + column, y + row + 1, previous)
                        run = column
                    }
                    previous = color
                }
            }
            // Painted highlights and motifs already belong to the reference; do not tint or cover them.
            graphics.nextStratum()
            sprite(style.neck)
            tag()
            return
        }
        for (row in style.top..style.bottom) for (span in style.rows[row]) {
            var run = span.first
            var previous = 0
            for (column in span.first..span.last + 1) {
                val color = when {
                    column > span.last -> 0
                    row >= surface && filled > 0 -> {
                        var tone = FlaskLiquid.shade(column, row, surface, style.bottom, clock, style.palette.size)
                        tone = (tone + style.tones[row][column] * 2) / 3
                        if (surfaceSpan != null && row - surface in 0..2) {
                            val middle = (surfaceSpan.first + surfaceSpan.last) / 2f
                            val radius = maxOf(1f, (surfaceSpan.last - surfaceSpan.first) / 2f)
                            val side = abs(column - middle) / radius
                            // A fixed ellipse marks the level; motion never tilts or waves its surface.
                            tone = when {
                                filled <= 2 -> style.palette.lastIndex
                                row == surface && side < .72f -> style.palette.lastIndex
                                row == surface + 1 && side >= .85f -> style.palette.lastIndex
                                row == surface + 2 && side < .72f -> style.palette.lastIndex
                                else -> style.palette.lastIndex - 1
                            }
                        }
                        UiTheme.argb(style.palette[tone], alpha)
                    }
                    row >= spentSurface && trail > level -> UiTheme.argb(style.palette.last(), alpha * .17f)
                    else -> 0
                }
                if (column > span.first && color != previous) {
                    if (previous != 0) graphics.fill(x + run, y + row, x + column, y + row + 1, previous)
                    run = column
                }
                previous = color
            }
        }
        if (filled >= 4) {
            val count = style.particleCount + if (gaining && !reducedMotion) 2 else 0
            for (i in 0 until count) {
                val (u, v) = FlaskLiquid.particle(style.flow, clock / style.periodTicks, i)
                val column = (16 + u * 29).roundToInt()
                val row = (surface + 2 + v * (filled - 3)).roundToInt()
                for ((dx, dy) in style.particle) {
                    if (row + dy >= surface + 2 && style.contains(column + dx, row + dy))
                        graphics.fill(x + column + dx, y + row + dy, x + column + dx + 1, y + row + dy + 1,
                            UiTheme.argb(style.palette.last(), alpha * .42f))
                }
            }
        }
        graphics.nextStratum()
        sprite(style.frame)
        sprite(style.neck)
        sprite(style.engraving)
        tag()
        // Glass highlights stay above the liquid even when the globe is completely full.
        graphics.nextStratum()
        sprite(style.glass)
    }
}
