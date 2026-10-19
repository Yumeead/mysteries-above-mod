package me.vangoo.mysteries.client.screen

import me.vangoo.domain.hud.*
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.hud.BottomHud
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class HudSettingsScreen(private val parent: Screen) : Screen(tr("settings")) {
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var tab = 0
    private var scroll = 0
    private var contentHeight = 0
    private val labels = mutableListOf<Pair<Component, Int>>()
    private val contentTop get() = top + 64
    private val contentBottom get() = top + panelHeight - 34
    private val viewport get() = (contentBottom - contentTop).coerceAtLeast(1)

    override fun init() {
        parent.resize(width, height)
        labels.clear()
        panelWidth = minOf(360, width - 16)
        panelHeight = minOf(312, height - 12)
        left = if (width >= 760) width / 2 + 4 else (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        contentHeight = when (tab) { 0 -> 280; 1 -> 160; else -> 130 }
        scroll = scroll.coerceIn(0,(contentHeight-viewport).coerceAtLeast(0))
        val tabWidth = (panelWidth - 32) / 3
        listOf("look", "number_tab", "symbol_tab").forEachIndexed { index, key ->
            addRenderableWidget(GrimoireButton(left + 12 + index * (tabWidth + 4), top + 34,
                tabWidth, 20, tr(key), selected = { tab == index }, goldSelection = true) {
                tab = index; scroll = 0; rebuildWidgets()
            })
        }
        var row = 0
        fun options(title: String, values: List<String>, selected: (Int) -> Boolean, choose: (Int) -> Unit) {
            val y = contentTop + row * 40 - scroll
            labels += tr(title) to y
            val w = (panelWidth - 24 - (values.size - 1) * 4) / values.size
            values.forEachIndexed { index, key ->
                addRenderableWidget(GrimoireButton(left + 12 + index * (w + 4), y + 12,
                    w, 20, tr(key), selected = { selected(index) }, goldSelection = true) { choose(index); rebuildWidgets() })
                    .visible = y + 12 >= contentTop && y + 32 <= contentBottom
            }
            row++
        }
        when (tab) {
            0 -> {
                options("interface", listOf("interface.vanilla", "interface.mod"),
                    { ClientOptions.hudSettings.interfaceStyle.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(interfaceStyle = InterfaceStyle.entries[it])
                }
                options("spirituality", listOf(if (BottomHud.enabled) "mode.flask" else "mode.old_flask", "mode.bar", "mode.off"),
                    { ClientOptions.hudSettings.spirituality.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(spirituality = SpiritualityMode.entries[it])
                }
                options("health", listOf("health.hearts", "health.bar"), { ClientOptions.hudSettings.health.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(health = HealthStyle.entries[it])
                }
                options("hunger", listOf("hunger.icons", "hunger.bar"), { ClientOptions.hudSettings.hunger.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(hunger = HungerStyle.entries[it])
                }
                options("half_heart", listOf("half.height", "half.left"), { ClientOptions.hudSettings.halfHeart.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(halfHeart = HalfHeartStyle.entries[it])
                }
                options("auto_health", listOf("disabled", "enabled"), { ClientOptions.hudSettings.compactHealthAbove40 == (it == 1) }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(compactHealthAbove40 = it == 1)
                }
                options("reduced_motion", listOf("disabled", "enabled"), { ClientOptions.reducedMotion == (it == 1) }) {
                    ClientOptions.reducedMotion = it == 1
                }
            }
            1 -> {
                val names = SpiritualityNumbers.entries.map { "numbers.${it.name.lowercase()}" }
                options("spirituality", names, { ClientOptions.hudSettings.numbers.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(numbers = SpiritualityNumbers.entries[it])
                }
                options("health", names, { ClientOptions.hudSettings.healthNumbers.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(healthNumbers = SpiritualityNumbers.entries[it])
                }
                options("hunger", names, { ClientOptions.hudSettings.hungerNumbers.ordinal == it }) {
                    ClientOptions.hudSettings = ClientOptions.hudSettings.copy(hungerNumbers = SpiritualityNumbers.entries[it])
                }
                labels += tr("low_numbers") to (contentTop + row * 40 - scroll)
                row++
            }
            2 -> {
                for (index in 0..2) { labels += tr("aux.$index") to (contentTop + row * 30 - scroll); row++ }
                labels += tr("aux_hidden") to (contentTop + 96 - scroll)
                labels += tr("aux_recent") to (contentTop + 112 - scroll)
            }
        }
        contentHeight = if (tab == 2) 130 else row * 40
        addRenderableWidget(GrimoireButton(left + 12, top + panelHeight - 27, 66, 19, tr("back"), goldSelection = true) { onClose() })
        addRenderableWidget(GrimoireButton(left + 86, top + panelHeight - 27, panelWidth - 98, 19, tr("move"), goldSelection = true) {
            minecraft.gui.setScreen(HudEditorScreen(this))
        })
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX >= left && mouseX < left + panelWidth && mouseY >= contentTop && mouseY < contentBottom) {
            val fresh = (scroll - (scrollY * 20).toInt()).coerceIn(0, (contentHeight - viewport).coerceAtLeast(0))
            if (fresh != scroll) { scroll = fresh; rebuildWidgets() }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun isPauseScreen() = false
    override fun tick() = parent.tick()
    override fun onClose() = minecraft.gui.setScreen(parent)
    override fun keyPressed(event: KeyEvent): Boolean {
        if (ModKeys.isBookKey(event)) { onClose(); return true }
        val target = when (event.key()) {
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll + viewport - 20
            GLFW.GLFW_KEY_PAGE_UP -> scroll - viewport + 20
            GLFW.GLFW_KEY_HOME -> 0
            GLFW.GLFW_KEY_END -> (contentHeight-viewport).coerceAtLeast(0)
            else -> null
        }
        if (target != null) {
            scroll = target.coerceIn(0,(contentHeight-viewport).coerceAtLeast(0))
            rebuildWidgets()
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        parent.extractBackground(graphics, -1, -1, partialTick)
        parent.extractRenderState(graphics, -1, -1, partialTick)
        graphics.nextStratum()
        graphics.fill(0, 0, width, height, UiTheme.argb(0x000000, .25f))
        BottomHud.frame(graphics, left, top, panelWidth, panelHeight,.97f)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.text(font, title, left + 12, top + 12, UiTheme.argb(0xDFC28B, 1f))
        if (contentHeight > viewport) graphics.text(font,tr("scroll_hint"),left+12,top+23,UiTheme.argb(UiTheme.MUTED,1f))
        graphics.enableScissor(left + 8, contentTop, left + panelWidth - 8, contentBottom)
        for ((label, y) in labels) graphics.text(font, label, left + 12, y, UiTheme.argb(UiTheme.TEXT, 1f))
        graphics.disableScissor()
        if (contentHeight > viewport) {
            val thumb = (viewport * viewport / contentHeight).coerceAtLeast(12)
            val y = contentTop + (viewport - thumb) * scroll / (contentHeight - viewport)
            graphics.fill(left + panelWidth - 6, y, left + panelWidth - 4, y + thumb, UiTheme.argb(0xB99A61, 1f))
        }
    }

    companion object {
        fun tr(key: String, vararg args: Any): Component = Component.translatable("hud.mysteries_above.$key", *args)
    }
}
