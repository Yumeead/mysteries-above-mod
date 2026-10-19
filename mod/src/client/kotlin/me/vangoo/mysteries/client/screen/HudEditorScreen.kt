package me.vangoo.mysteries.client.screen

import me.vangoo.domain.hud.HudLayout
import me.vangoo.domain.hud.HudEdge
import me.vangoo.domain.hud.HudPart
import me.vangoo.domain.hud.HudPosition
import me.vangoo.domain.hud.HudRect
import me.vangoo.domain.hud.HudResize
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.hud.AbilityHud
import me.vangoo.mysteries.client.hud.BottomHud
import me.vangoo.mysteries.client.hud.HudBox
import me.vangoo.mysteries.client.hud.HudPlacement
import me.vangoo.mysteries.client.hud.MarionetteHud
import me.vangoo.mysteries.client.hud.SpiritualityHud
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.screen.HudSettingsScreen.Companion.tr
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

class HudEditorScreen(private val parent: Screen) : Screen(tr("move")) {
    private val pending = mutableMapOf<HudPart, HudPosition>()
    private val pendingScales = mutableMapOf<HudPart, Float>()
    private val handles = mutableMapOf<HudPart, Handle>()
    private var selected: HudPart? = null
    private var dragging: HudPart? = null
    private var resizing: HudResize? = null
    private var grabX = 0
    private var grabY = 0
    private lateinit var reset: GrimoireButton
    private var conflicts = emptySet<HudPart>()

    fun boxes(): Map<HudPart, HudBox> = HudPlacement.boxes(width, height, preview = true,
        overrides = pending, scaleOverrides = pendingScales).filterKeys {
            !BottomHud.enabled || it != HudPart.BAR
        }

    override fun init() {
        dragging = null
        resizing = null
        isDragging = false
        handles.clear()
        for ((part, box) in boxes()) {
            val handle = Handle(part, box.rect)
            handles[part] = handle
            addRenderableWidget(handle)
        }
        val gap = 4
        val doneWidth = font.width(tr("done")) + 16
        val allWidth = font.width(tr("reset_all")) + 16
        val oneWidth = minOf(font.width(tr("reset_one")) + 16, width - allWidth - doneWidth - 16)
        val left = (width - oneWidth - allWidth - doneWidth - gap * 2) / 2
        reset = addRenderableWidget(GrimoireButton(left, 8, oneWidth, 20, tr("reset_one")) {
            selected?.let { part ->
                pending.remove(part)
                pendingScales.remove(part)
                ClientOptions.hudSettings = ClientOptions.hudSettings.positioned(
                    ClientOptions.hudSettings.activePositions - part).copy(
                    scales = if (BottomHud.enabled) ClientOptions.hudSettings.scales else ClientOptions.hudSettings.scales - part)
            }
        })
        addRenderableWidget(GrimoireButton(left + oneWidth + gap, 8, allWidth, 20, tr("reset_all")) {
            pending.clear()
            pendingScales.clear()
            ClientOptions.hudSettings = ClientOptions.hudSettings.positioned(emptyMap()).copy(
                scales = if (BottomHud.enabled) ClientOptions.hudSettings.scales else emptyMap())
        })
        addRenderableWidget(GrimoireButton(left + oneWidth + allWidth + gap * 2, 8, doneWidth, 20, tr("done")) { onClose() })
    }

    override fun isPauseScreen() = false
    override fun onClose() {
        commit()
        minecraft.gui.setScreen(parent)
    }
    override fun removed() = commit()

    private fun commit() {
        if (pending.isNotEmpty() || pendingScales.isNotEmpty()) {
            ClientOptions.hudSettings = ClientOptions.hudSettings.positioned(
                ClientOptions.hudSettings.activePositions + pending).copy(
                scales = ClientOptions.hudSettings.scales + pendingScales)
            pending.clear()
            pendingScales.clear()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val onToolbar = children().filterIsInstance<GrimoireButton>().any {
            event.x() >= it.x && event.x() < it.x + it.width && event.y() >= it.y && event.y() < it.y + it.height
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !onToolbar) {
            val mouseX = event.x().toInt()
            val mouseY = event.y().toInt()
            val boxes = boxes()
            val edgeHit = if (BottomHud.enabled) null else (
                boxes.entries.lastOrNull { it.key == selected && HudEdge.at(it.value.rect, mouseX, mouseY) != null }
                    ?: boxes.entries.lastOrNull { HudEdge.at(it.value.rect, mouseX, mouseY) != null })
            val hit = edgeHit ?: boxes.entries.lastOrNull { it.value.rect.contains(mouseX, mouseY) }
            if (hit != null) {
                selected = hit.key
                dragging = hit.key
                val edge = if (edgeHit != null) HudEdge.at(hit.value.rect, mouseX, mouseY) else null
                resizing = edge?.let { HudResize(hit.value.rect, hit.value.scale / ClientOptions.hudScale, it) }
                grabX = if (edge != null) mouseX else mouseX - hit.value.rect.x
                grabY = if (edge != null) mouseY else mouseY - hit.value.rect.y
                focused = handles[hit.key]
                isDragging = true
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val part = dragging ?: return super.mouseDragged(event, dragX, dragY)
        resizing?.let { resize ->
            val (rect, scale) = resize.resized(event.x().toInt() - grabX, event.y().toInt() - grabY, width, height)
            pendingScales[part] = scale
            pending[part] = HudPosition.of(rect, width, height)
            return true
        }
        val boxes = boxes()
        val box = boxes[part] ?: return false
        val rect = box.rect.copy(x = event.x().toInt() - grabX, y = event.y().toInt() - grabY)
        val targets = boxes.filterKeys { it != part }.values.map { it.rect } + HudPlacement.obstacles(width, height)
        pending[part] = HudPosition.of(HudLayout.snap(rect, width, height, targets), width, height)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = null
            resizing = null
            isDragging = false
            commit()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (ModKeys.isBookKey(event)) { onClose(); return true }
        val part = selected
        if (part != null && focused is Handle) {
            val (dx, dy) = when (event.key()) {
                GLFW.GLFW_KEY_LEFT -> -1 to 0
                GLFW.GLFW_KEY_RIGHT -> 1 to 0
                GLFW.GLFW_KEY_UP -> 0 to -1
                GLFW.GLFW_KEY_DOWN -> 0 to 1
                else -> 0 to 0
            }
            if (dx != 0 || dy != 0) {
                val rect = boxes()[part]?.rect ?: return false
                pending[part] = HudPosition.of(rect.copy(x = rect.x + dx, y = rect.y + dy).clamped(width, height), width, height)
                commit()
                return true
            }
        }
        return super.keyPressed(event)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, UiTheme.argb(0x08080C, .15f))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val boxes = boxes()
        val vanilla = HudPlacement.obstacles(width, height)
        conflicts = boxes.filter { (part, box) ->
            // Transparent padding around the orb must not count as covering vanilla status icons.
            val body = if (BottomHud.enabled && part == HudPart.FLASK)
                HudRect(box.rect.x+3,box.rect.y+8,36,36) else box.rect
            vanilla.any(body::overlaps) ||
                boxes.any { (other, otherBox) -> part != other && box.rect.overlaps(otherBox.rect) }
        }.keys
        if (BottomHud.enabled) HudPlacement.boxes(width,height,true).filterKeys {
            it == HudPart.BAR
        }.values.forEach { SpiritualityHud.draw(graphics,it,minecraft.deltaTracker.getGameTimeDeltaPartialTick(false),preview=true) }
        for ((part, box) in boxes) {
            when (part) {
                HudPart.FLASK, HudPart.BAR -> SpiritualityHud.draw(graphics, box, minecraft.deltaTracker.getGameTimeDeltaPartialTick(false), preview = true)
                HudPart.ABILITY -> if (BottomHud.enabled) BottomHud.drawAbility(graphics,box,preview=true)
                    else AbilityHud.draw(graphics, box, preview = true)
                HudPart.MARIONETTES -> MarionetteHud.draw(graphics, box, preview = true)
                HudPart.AUXILIARY -> BottomHud.drawAuxiliary(graphics,box,preview=true)
            }
            handles[part]?.apply { x = box.rect.x; y = box.rect.y; width = box.rect.width; height = box.rect.height }
        }
        graphics.nextStratum()
        selected?.let { boxes[it]?.rect }?.let { rect ->
            val color = UiTheme.argb(UiTheme.ACCENT, .2f)
            graphics.fill(rect.x, 42, rect.x + 1, height, color)
            graphics.fill(0, rect.y, width, rect.y + 1, color)
        }
        reset.active = selected != null
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val hint = tr(if (BottomHud.enabled) "panel_drag_hint" else "drag_hint")
        val hintScale = minOf(1f, (width - 8).toFloat() / font.width(hint))
        graphics.pose().pushMatrix()
        graphics.pose().translate((width / 2).toFloat(), 33f)
        graphics.pose().scale(hintScale, hintScale)
        graphics.centeredText(font, hint, 0, 0, UiTheme.argb(UiTheme.TEXT, 1f))
        graphics.pose().popMatrix()
        if (conflicts.isNotEmpty()) graphics.centeredText(font, tr("overlap"), width / 2, height - 12, UiTheme.argb(UiTheme.CRITICAL, 1f))
    }

    private inner class Handle(private val part: HudPart, rect: HudRect) :
        AbstractButton(rect.x, rect.y, rect.width, rect.height, tr("part.${part.name.lowercase()}")) {
        override fun setFocused(focused: Boolean) {
            super.setFocused(focused)
            if (focused) selected = part
        }
        override fun onPress(input: InputWithModifiers) { selected = part }
        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val color = UiTheme.argb(if (part in conflicts) UiTheme.CRITICAL else UiTheme.ACCENT,
                if (selected == part || isHoveredOrFocused) 1f else .65f)
            for (dx in 0 until width step 4) {
                graphics.fill(x + dx, y, x + minOf(dx + 2, width), y + 1, color)
                graphics.fill(x + dx, y + height - 1, x + minOf(dx + 2, width), y + height, color)
            }
            for (dy in 0 until height step 4) {
                graphics.fill(x, y + dy, x + 1, y + minOf(dy + 2, height), color)
                graphics.fill(x + width - 1, y + dy, x + width, y + minOf(dy + 2, height), color)
            }
            val hoveredEdge = HudEdge.at(HudRect(x, y, width, height), mouseX, mouseY)
            for (edge in if (BottomHud.enabled) emptyList() else HudEdge.entries) {
                val handleX = x + (edge.horizontal + 1) * width / 2
                val handleY = y + (edge.vertical + 1) * height / 2
                val radius = if (hoveredEdge == edge || (dragging == part && resizing?.edge == edge)) 3 else 2
                graphics.fill(handleX - radius, handleY - radius, handleX + radius + 1, handleY + radius + 1, color)
            }
            val labelY = if (y >= 53) y - 10 else y + height + 2
            graphics.text(font, message, x.coerceAtMost(this@HudEditorScreen.width - font.width(message)), labelY, color)
        }
    }
}
