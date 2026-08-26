package me.vangoo.mysteries

import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.server.BeyonderStatusSource
import me.vangoo.mysteries.server.StatusCommand
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

/**
 * Спільний entrypoint: усе, що однаково потрібне клієнту й виділеному серверу.
 * Клієнтська половина — [me.vangoo.mysteries.client.MysteriesAboveClient].
 */
object MysteriesAboveMod : ModInitializer {

    private val log = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        // Типи пакетів реєструються на обох сторонах і саме звідси: клієнт, що
        // зареєстрував інший набір, ніж сервер, — це «іноді не працює», а не помилка.
        ModChannels.registerTypes()

        ModChannels.STATUS_REQUEST.receiveOnServer { _, context ->
            val player = context.player()
            ModChannels.BEYONDER_STATUS.sendTo(player, BeyonderStatusSource.snapshot(player))
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> StatusCommand.register(dispatcher) }

        log.info("Mysteries Above: спільна частина піднялась, каналів — {}", ModChannels.all.size)
    }
}
