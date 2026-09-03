package me.vangoo.mysteries.client

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.beyonder.SanityAmbience
import me.vangoo.mysteries.client.render.SpiritGlyphParticle
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
 * The client entrypoint. Everything absent on a dedicated server is loaded from here — and
 * compiles only in the `client` source set.
 */
object MysteriesAboveClient : ClientModInitializer {

    private val log = LoggerFactory.getLogger("$MOD_ID/client")

    override fun onInitializeClient() {
        // The first thing done: after this both the HUD and the keys read the settings as
        // something that already exists.
        ClientOptions.load()

        ModChannels.BEYONDER_STATUS.receiveOnClient { payload, context ->
            // Fresh state updates an open screen in place; if there is no screen, it opens one.
            // That way a single response serves both `/mysteries status` and the "Оновити" button.
            // On 26.2 the current screen lives on Gui rather than on Minecraft:
            // Minecraft.screen is gone, only gui.screen()/gui.setScreen() remain.
            val gui = context.client().gui
            when (val screen = gui.screen()) {
                is BeyonderStatusScreen -> screen.update(payload)
                else -> gui.setScreen(BeyonderStatusScreen(payload))
            }
        }

        // Both snapshots land in the client store and nowhere else: the HUD draws from it, and
        // from M4 the wheel will too.
        ModChannels.ABILITY_ROSTER.receiveOnClient { payload, _ -> ClientAbilityState.accept(payload) }
        ModChannels.ABILITY_STATE.receiveOnClient { payload, _ -> ClientAbilityState.accept(payload) }

        // The cooldown clock. The client tick, not wall time: a paused single-player game stands
        // still — the server and the countdown along with it.
        ClientTickEvents.END_CLIENT_TICK.register {
            ClientAbilityState.tick()
            // The whisper and the screen chain are derived from the state just updated, so they
            // come right after it rather than as a separate subscription with an undefined order.
            SanityAmbience.tick()
        }

        // A mote's motion and fade are the client's: the server only sends "where and what colour".
        ParticleProviderRegistry.getInstance().register(ModParticles.SPIRIT_MOTE) { sprites ->
            // The set, not the sprite: the particle atlas has not been assembled at this moment,
            // and `first()` here fails with a `NullPointerException` on the game's splash screen.
            SpiritMoteParticle.Provider(sprites)
        }
        ParticleProviderRegistry.getInstance().register(ModParticles.SPIRIT_GLYPH) { sprites ->
            SpiritGlyphParticle.Provider(sprites)
        }

        // The next world starts with a clean slate: someone else's roster is neither drawable nor wanted.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientAbilityState.clear()
            SanityAmbience.clear()
        }

        SpiritualityHud.register()
        AbilityHud.register()
        ModKeys.register()

        log.info("Mysteries Above: client side is up")
    }
}
