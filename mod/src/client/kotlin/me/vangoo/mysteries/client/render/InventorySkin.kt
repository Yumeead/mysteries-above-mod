package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.client.hud.BottomHud
import com.google.gson.JsonParser
import me.vangoo.mysteries.modId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

/** Exact vanilla classes only: unknown mod screens and their subclasses keep their resources. */
object InventorySkin {
    private val screens = setOf("InventoryScreen", "ContainerScreen", "CraftingScreen", "FurnaceScreen",
        "BlastFurnaceScreen", "SmokerScreen", "AnvilScreen", "EnchantmentScreen", "SmithingScreen", "GrindstoneScreen",
        "LoomScreen", "CartographyTableScreen", "StonecutterScreen", "BrewingStandScreen", "BeaconScreen",
        "HopperScreen", "DispenserScreen", "MerchantScreen", "HorseInventoryScreen", "ShulkerBoxScreen", "CrafterScreen")
        .mapTo(hashSetOf()) { "net.minecraft.client.gui.screens.inventory.$it" }
    private val textures = checkNotNull(javaClass.getResourceAsStream("/assets/mysteries_above/interface-textures.json"))
        .bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonObject.entrySet()
            .associate { (key, value) -> Identifier.withDefaultNamespace(key) to modId(value.asString) } }

    fun allows(screen: Screen?): Boolean = screen?.javaClass?.name in screens

    val enabled: Boolean get() = BottomHud.enabled && allows(Minecraft.getInstance().gui.screen())

    @JvmStatic fun texture(original: Identifier): Identifier {
        if (!enabled || original.namespace != "minecraft") return original
        return textures[original] ?: original
    }
}
