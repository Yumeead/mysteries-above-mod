package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.client.hud.BottomHud
import com.google.gson.JsonParser
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.renderer.RenderPipelines
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.resources.Identifier

/** Exact vanilla classes only: unknown mod screens and their subclasses keep their resources. */
object InventorySkin {
    private val screens = setOf("InventoryScreen", "ContainerScreen", "CraftingScreen", "FurnaceScreen",
        "BlastFurnaceScreen", "SmokerScreen", "AnvilScreen", "EnchantmentScreen", "SmithingScreen", "GrindstoneScreen",
        "LoomScreen", "CartographyTableScreen", "StonecutterScreen", "BrewingStandScreen", "BeaconScreen",
        "HopperScreen", "DispenserScreen", "MerchantScreen", "HorseInventoryScreen", "ShulkerBoxScreen", "CrafterScreen",
        "CreativeModeInventoryScreen", "NautilusInventoryScreen")
        .mapTo(hashSetOf()) { "net.minecraft.client.gui.screens.inventory.$it" }
    private val textures = checkNotNull(javaClass.getResourceAsStream("/assets/mysteries_above/interface-textures.json"))
        .bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonObject.entrySet()
            .associate { (key, value) -> Identifier.withDefaultNamespace(key) to modId(value.asString) } }

    private val widgets = java.util.ArrayDeque<AbstractWidget>()
    private val gold = checkNotNull(javaClass.getResourceAsStream("/assets/mysteries_above/interface-palette.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.get("goldLight").asInt }

    fun allows(screen: Screen?): Boolean {
        val name = screen?.javaClass?.name ?: return false
        return name in screens || name.startsWith("net.minecraft.client.gui.screens.options.") ||
            name == "net.minecraft.client.gui.screens.packs.PackSelectionScreen" || screen.javaClass == PauseScreen::class.java
    }

    val enabled: Boolean get() = BottomHud.enabled && allows(Minecraft.getInstance().gui.screen())

    @JvmStatic fun texture(original: Identifier): Identifier {
        if (!enabled || original.namespace != "minecraft") return original
        val name = original.path
        if (Minecraft.getInstance().gui.screen()?.javaClass?.name !in screens &&
            !name.startsWith("widget/") && !name.startsWith("tooltip/") && !name.startsWith("pause_menu/") &&
            !name.endsWith("_separator.png") && name != "friends/friends") return original
        val widget = widgets.peekLast()
        if (widget != null && !widget.javaClass.name.startsWith("net.minecraft.client.")) return original
        if (name.startsWith("widget/button") && widget is AbstractButton && widget.active && widget.isHovered &&
            Minecraft.getInstance().mouseHandler.isLeftPressed) return modId("interface/button_pressed")
        return textures[original] ?: original
    }

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (!allows(screen)) return@register
            val title = screen.children().filterIsInstance<StringWidget>().firstOrNull { it.message.string == screen.title.string }
                ?: return@register
            val originalY = title.y
            val pauseY = if (screen.javaClass == PauseScreen::class.java)
                screen.children().filterIsInstance<Button>().minOfOrNull { it.y }?.minus(22) else null
            ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ ->
                widgets.clear()
                title.y = if (enabled && pauseY != null) pauseY else originalY
            }
            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ ->
                if (enabled) {
                    val width = if (pauseY != null) 204 else minOf(Minecraft.getInstance().font.width(title.message) + 16, screen.width - 16)
                    val top = maxOf(0, title.y - 6)
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, modId("interface/panel_dark"),
                        (screen.width - width) / 2, top, width, minOf(20, title.y + 14 - top))
                }
            }
        }
    }

    @JvmStatic fun beginWidget(widget: AbstractWidget) {
        if (enabled) widgets.addLast(widget)
    }

    @JvmStatic fun endWidget(widget: AbstractWidget, graphics: GuiGraphicsExtractor) {
        if (widgets.peekLast() !== widget) return
        widgets.removeLast()
        if (!enabled || !Minecraft.getInstance().lastInputType.isKeyboard ||
            !widget.active || !widget.isFocused || widget.width < 12 || widget.height < 12 ||
            !widget.javaClass.name.startsWith("net.minecraft.client.") ||
            widget !is AbstractButton && widget !is AbstractSliderButton && widget !is EditBox) return
        val x = widget.x + 3
        val y = widget.y + 3
        for ((sx, dx) in listOf(x to 1, widget.x + widget.width - 4 to -1)) {
            for ((sy, dy) in listOf(y to 1, widget.y + widget.height - 4 to -1)) {
                for (n in 0..2) {
                    graphics.fill(sx + dx * n, sy, sx + dx * n + 1, sy + 1, gold)
                    graphics.fill(sx, sy + dy * n, sx + 1, sy + dy * n + 1, gold)
                }
            }
        }
    }

    @JvmStatic fun selectionColor(original: Int): Int = if (enabled) (original and -0x1000000) or (gold and 0xFFFFFF) else original
}
