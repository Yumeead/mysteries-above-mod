package me.vangoo.mysteries

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.server.MysteriesCommand
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

        // Компонент — перед предметом: предмет його вже вимагає.
        ModComponents.register()
        ModItems.register()

        // Чуттєвий словник: частинки й звуки реєструються на обох сторонах — сервер
        // шле їх у пакетах, клієнт малює й програє.
        ModParticles.register()
        ModSounds.register()

        // Хребет здібностей: реєстр Потойбічних, кулдауни, повідомлення, виконавець
        // касту. Далі кожна фаза додає сюди свій сервіс.
        AbilityServices.bootstrap()

        ModChannels.STATUS_REQUEST.receiveOnServer { _, context ->
            val player = context.player()
            BeyonderStatus.push(player)
            BeyonderStatus.pushRoster(player)
            AbilityServices.statePump.pushNow(player.uuid)
        }

        // Клавіша касту. Здібність обирає сервер — див. `CastRequestPayload`.
        ModChannels.CAST_REQUEST.receiveOnServer { _, context ->
            AbilityRequests.castSelected(context.player())
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> MysteriesCommand.register(dispatcher) }

        log.info(
            "Mysteries Above: спільна частина піднялась — каналів {}, шляхів {}",
            ModChannels.all.size,
            ModPathways.all.size,
        )
    }
}
