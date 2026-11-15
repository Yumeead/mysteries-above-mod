package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.PuppetSkins
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.ChoiceAnswerPayload
import me.vangoo.mysteries.net.payload.FaceGalleryPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.components.PlayerSkinWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * The Faceless's gallery: every face they have seen, newest first — heads with names, a search, and
 * the whole figure, three-quarters on, of the one under the cursor. A click wears it; Escape leaves
 * the cast unpaid. The game does not pause.
 *
 * The answer goes back as an ordinary choice ([ChoiceAnswerPayload]) under the gallery's own id.
 */
class FaceGalleryScreen(private val gallery: FaceGalleryPayload) : Screen(Component.translatable("gallery.$MOD_ID.title")) {

    private var answered = false
    private var search: EditBox? = null
    private var shown: List<Int> = gallery.faces.indices.toList()
    private var hovered = -1
    private var scroll = 0
    private var figure: PlayerSkinWidget? = null

    override fun init() {
        val box = EditBox(font, left(), TOP, gridWidth(), SEARCH_HEIGHT, Component.translatable("gallery.$MOD_ID.search"))
        box.setHint(Component.translatable("gallery.$MOD_ID.search"))
        search?.let { box.value = it.value }
        box.setResponder { filter(it) }
        addRenderableWidget(box)
        setInitialFocus(box)
        search = box

        // The figure turns to the face under the cursor; with none, it is not there.
        val widget = PlayerSkinWidget(FIGURE_WIDTH, FIGURE_HEIGHT, minecraft.entityModels) {
            PuppetSkins.of(gallery.faces[shown.getOrElse(hovered) { 0 }].face.partialProfile())
        }
        widget.setPosition(left() + gridWidth() + FIGURE_GAP, TOP)
        widget.visible = false
        addRenderableWidget(widget)
        figure = widget
        filter(box.value)
    }

    private fun filter(text: String) {
        val needle = text.trim().lowercase()
        shown = gallery.faces.indices.filter { needle.isEmpty() || gallery.faces[it].face.partialProfile().name.lowercase().contains(needle) }
        scroll = 0
        hovered = -1
    }

    private fun columns(): Int = ((width - FIGURE_WIDTH - FIGURE_GAP - MARGIN * 2) / CELL_WIDTH).coerceIn(1, MAX_COLUMNS)

    private fun gridWidth(): Int = columns() * CELL_WIDTH

    private fun left(): Int = (width - gridWidth() - FIGURE_WIDTH - FIGURE_GAP) / 2

    private fun rowsFit(): Int = ((height - GRID_TOP - MARGIN) / CELL_HEIGHT).coerceAtLeast(1)

    /** The index into [shown] under ([x], [y]), or `-1`. */
    private fun cellAt(x: Double, y: Double): Int {
        val column = ((x - left()) / CELL_WIDTH).toInt()
        val row = ((y - GRID_TOP) / CELL_HEIGHT).toInt()
        if (x < left() || y < GRID_TOP || column !in 0 until columns() || row !in 0 until rowsFit()) return -1
        return ((row + scroll) * columns() + column).takeIf { it < shown.size } ?: -1
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        val cell = cellAt(mouseX, mouseY)
        if (cell >= 0) hovered = cell
        figure?.visible = hovered >= 0
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val rows = (shown.size + columns() - 1) / columns()
        scroll = (scroll - scrollY.toInt()).coerceIn(0, (rows - rowsFit()).coerceAtLeast(0))
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val cell = if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) cellAt(event.x(), event.y()) else -1
        if (cell < 0) return super.mouseClicked(event, doubleClick)
        answer(shown[cell])
        onClose()
        return true
    }

    private fun answer(index: Int) {
        if (answered) return
        answered = true
        ModChannels.CHOICE_ANSWER.sendToServer(ChoiceAnswerPayload(gallery.id, index, ""))
    }

    override fun removed() {
        answer(ChoiceAnswerPayload.CANCEL)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val alpha = ClientOptions.opacity
        graphics.centeredText(font, title, width / 2, TOP - 14, UiTheme.argb(UiTheme.ACCENT, alpha))
        if (shown.isEmpty()) {
            graphics.centeredText(font, Component.translatable("gallery.$MOD_ID.nobody"), left() + gridWidth() / 2, GRID_TOP + 8, UiTheme.argb(UiTheme.MUTED, alpha))
            return
        }
        val columns = columns()
        val first = scroll * columns
        for (cell in first until minOf(shown.size, first + rowsFit() * columns)) {
            val entry = gallery.faces[shown[cell]]
            val x = left() + (cell % columns) * CELL_WIDTH
            val y = GRID_TOP + (cell / columns - scroll) * CELL_HEIGHT
            val head = x + (CELL_WIDTH - HEAD) / 2
            if (cell == hovered) graphics.outline(head - 2, y - 2, HEAD + 4, HEAD + 4, UiTheme.argb(UiTheme.ACCENT, alpha))
            PlayerFaceExtractor.extractRenderState(graphics, entry.face, head, y, HEAD)
            val name = entry.face.partialProfile().name
            graphics.centeredText(font, font.plainSubstrByWidth(name, CELL_WIDTH - 4), x + CELL_WIDTH / 2, y + HEAD + 3,
                UiTheme.argb(if (cell == hovered) UiTheme.TEXT else UiTheme.MUTED, alpha))
        }
        gallery.faces.getOrNull(shown.getOrElse(hovered) { -1 })?.let { entry ->
            val x = left() + gridWidth() + FIGURE_GAP + FIGURE_WIDTH / 2
            graphics.centeredText(font, entry.face.partialProfile().name, x, TOP + FIGURE_HEIGHT + 4, UiTheme.argb(UiTheme.TEXT, alpha))
            graphics.centeredText(font, ago(entry.secondsAgo), x, TOP + FIGURE_HEIGHT + 15, UiTheme.argb(UiTheme.MUTED, alpha))
        }
    }

    /** "Seen just now / 5 min / 3 h / 2 days ago". */
    private fun ago(seconds: Long): Component = when {
        seconds < 60 -> Component.translatable("gallery.$MOD_ID.ago.now")
        seconds < 3600 -> Component.translatable("gallery.$MOD_ID.ago.minutes", seconds / 60)
        seconds < 86400 -> Component.translatable("gallery.$MOD_ID.ago.hours", seconds / 3600)
        else -> Component.translatable("gallery.$MOD_ID.ago.days", seconds / 86400)
    }

    private companion object {
        const val TOP = 30
        const val MARGIN = 12
        const val SEARCH_HEIGHT = 18
        const val GRID_TOP = TOP + SEARCH_HEIGHT + 10
        const val HEAD = 24
        const val CELL_WIDTH = 56
        const val CELL_HEIGHT = HEAD + 18
        const val MAX_COLUMNS = 8
        const val FIGURE_WIDTH = 70
        const val FIGURE_HEIGHT = 110
        const val FIGURE_GAP = 16
    }
}
