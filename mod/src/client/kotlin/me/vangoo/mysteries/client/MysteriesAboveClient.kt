package me.vangoo.mysteries.client

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.net.receiveOnClient
import me.vangoo.mysteries.client.screen.BeyonderStatusScreen
import me.vangoo.mysteries.net.ModChannels
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

/**
 * Клієнтський entrypoint. Усе, чого нема на виділеному сервері, вантажиться
 * звідси — і компілюється лише в source set `client`.
 */
object MysteriesAboveClient : ClientModInitializer {

    private val log = LoggerFactory.getLogger("$MOD_ID/client")

    override fun onInitializeClient() {
        ModChannels.BEYONDER_STATUS.receiveOnClient { payload, context ->
            // Свіжий стан оновлює відкритий екран на місці; якщо екрана нема —
            // відкриває його. Так одна відповідь обслуговує і `/mysteries status`,
            // і кнопку «Оновити».
            // На 26.2 поточний екран живе на Gui, а не на Minecraft:
            // Minecraft.screen прибрано, лишились gui.screen()/gui.setScreen().
            val gui = context.client().gui
            when (val screen = gui.screen()) {
                is BeyonderStatusScreen -> screen.update(payload)
                else -> gui.setScreen(BeyonderStatusScreen(payload))
            }
        }

        ModKeys.register()

        log.info("Mysteries Above: клієнтська частина піднялась")
    }
}
