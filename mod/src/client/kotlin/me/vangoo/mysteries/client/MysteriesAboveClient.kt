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

        // Читальна модель уже їде по дроту, але малювати її ще нема чим: сховище
        // клієнта — віха M2, HUD — M3. Лог тут не тимчасовий милиць, а перевірка віхи:
        // він доводить, що обидва знімки доходять цілими, і зникне разом із приймачем,
        // коли пакет буде куди покласти.
        ModChannels.ABILITY_ROSTER.receiveOnClient { payload, _ ->
            val roster = payload.roster
            if (roster == null) {
                log.info("Ростер: гравець не Потойбічний")
            } else {
                log.info(
                    "Ростер: {} Посл. {} ({}) — здібностей {}: {}",
                    roster.pathwayId,
                    roster.sequence.level,
                    roster.sequenceName.string,
                    roster.entries.size,
                    roster.entries.joinToString { "${it.identity}/${it.type}/${it.cost}" },
                )
            }
        }

        ModChannels.ABILITY_STATE.receiveOnClient { payload, _ ->
            log.info(
                "Стан на тіку {}: духовність {}, обрано {}, живих {}: {}",
                payload.serverTick,
                payload.spirituality,
                payload.selected ?: "—",
                payload.live.size,
                payload.live.joinToString {
                    "${it.identity}@${it.readyAtTick}${if (it.toggled) "●" else ""}${if (it.sustaining) "◐" else ""}"
                },
            )
        }

        ModKeys.register()

        log.info("Mysteries Above: клієнтська частина піднялась")
    }
}
