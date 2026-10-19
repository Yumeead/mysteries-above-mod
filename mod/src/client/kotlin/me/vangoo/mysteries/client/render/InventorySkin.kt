package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.client.hud.BottomHud
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.resources.Identifier

/** Етап 2: лише штатний E та контейнер 9×3; сторонні підкласи зберігають свій рендер. */
object InventorySkin {
    private val inventory = modId("textures/gui/interface/inventory.png")
    private val chest = modId("textures/gui/interface/generic_54.png")
    private val sprites = listOf("container/inventory/effect_background", "container/inventory/effect_background_ambient",
        "recipe_book/button", "recipe_book/button_highlighted").associateWith { modId("interface/$it") }

    @JvmStatic fun texture(original: Identifier): Identifier {
        if (!BottomHud.enabled || original.namespace != "minecraft") return original
        val screen = Minecraft.getInstance().gui.screen() ?: return original
        return when (screen.javaClass) {
            InventoryScreen::class.java -> if (original.path == "textures/gui/container/inventory.png") inventory
                else sprites[original.path] ?: original
            ContainerScreen::class.java -> if ((screen as ContainerScreen).menu.rowCount == 3 &&
                original.path == "textures/gui/container/generic_54.png") chest else original
            else -> original
        }
    }
}
