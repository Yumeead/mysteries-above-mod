package me.vangoo.mysteries.client

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.beyonder.SanityAmbience
import me.vangoo.mysteries.client.render.SpiritMoteParticle
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.client.hud.AbilityHud
import me.vangoo.mysteries.client.hud.SpiritualityHud
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.net.receiveOnClient
import me.vangoo.mysteries.client.screen.BeyonderStatusScreen
import me.vangoo.mysteries.net.ModChannels
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry
import org.slf4j.LoggerFactory

/**
 * Клієнтський entrypoint. Усе, чого нема на виділеному сервері, вантажиться
 * звідси — і компілюється лише в source set `client`.
 */
object MysteriesAboveClient : ClientModInitializer {

    private val log = LoggerFactory.getLogger("$MOD_ID/client")

    override fun onInitializeClient() {
        // Перше, що робиться: далі і HUD, і клавіші читають налаштування як уже наявні.
        ClientOptions.load()

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

        // Обидва знімки лягають у клієнтське сховище й більше нікуди: малює з нього
        // HUD, а з M4 — і колесо.
        ModChannels.ABILITY_ROSTER.receiveOnClient { payload, _ -> ClientAbilityState.accept(payload) }
        ModChannels.ABILITY_STATE.receiveOnClient { payload, _ -> ClientAbilityState.accept(payload) }

        // Годинник кулдаунів. Тік клієнта, а не настінний час: одиночна гра на паузі
        // стоїть — і сервер, і відлік разом із нею.
        ClientTickEvents.END_CLIENT_TICK.register {
            ClientAbilityState.tick()
            // Шепіт і екранний ланцюг — похідні від щойно оновленого стану, тож ідуть
            // одразу за ним, а не окремою підпискою з невизначеним порядком.
            SanityAmbience.tick()
        }

        // Рух і згасання порошинки — клієнтські: сервер шле лише «де і якого кольору».
        ParticleProviderRegistry.getInstance().register(ModParticles.SPIRIT_MOTE) { sprites ->
            SpiritMoteParticle.Provider(sprites.first())
        }

        // Наступний світ починає з чистого аркуша: чужий ростер малювати нічим і нема чого.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientAbilityState.clear()
            SanityAmbience.clear()
        }

        SpiritualityHud.register()
        AbilityHud.register()
        ModKeys.register()

        log.info("Mysteries Above: клієнтська частина піднялась")
    }
}
