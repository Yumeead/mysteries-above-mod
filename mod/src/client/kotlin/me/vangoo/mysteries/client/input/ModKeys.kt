package me.vangoo.mysteries.client.input

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CastRequestPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

/**
 * Клавіші мода.
 *
 * Тимчасова половина фази 05: колесо здібностей приїде у фазі 11 і забере вибір собі.
 * Клавіша ж лишиться — вона тут і тепер доводить, що серверний напрямок пакетного шару
 * працює не лише з екрана статусу, а й у бою, з руками на WASD.
 *
 * На 26.2 категорія клавіш — не рядок, а `KeyMapping.Category` зі своїм `Identifier`;
 * її підпис береться з ключа `key.category.<namespace>.<path>`.
 */
object ModKeys {

    private val CATEGORY = KeyMapping.Category.register(modId("abilities"))

    private val CAST: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY),
    )

    fun register() {
        // `consumeClick` віддає натискання по одному, тож утримана клавіша не
        // перетворюється на двадцять кастів за секунду.
        ClientTickEvents.END_CLIENT_TICK.register {
            while (CAST.consumeClick()) {
                ModChannels.CAST_REQUEST.sendToServer(CastRequestPayload.INSTANCE)
            }
        }
    }
}
