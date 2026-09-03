package me.vangoo.mysteries

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.error.abilities.ConceptualTheft
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.server.MysteriesCommand
import me.vangoo.mysteries.world.SpiritWorld
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

/**
 * The shared entrypoint: everything the client and a dedicated server need alike.
 * The client half is [me.vangoo.mysteries.client.MysteriesAboveClient].
 */
object MysteriesAboveMod : ModInitializer {

    private val log = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        // Packet types are registered on both sides and from exactly here: a client that
        // registered a different set from the server is "sometimes broken", not an error.
        ModChannels.registerTypes()

        // The component before the item: the item already requires it.
        ModComponents.register()
        ModItems.register()

        // The sensory vocabulary: particles and sounds are registered on both sides — the
        // server sends them in packets, the client draws and plays them.
        ModParticles.register()
        ModSounds.register()

        // The ability backbone: the Beyonder registry, cooldowns, messaging, the cast executor.
        // Each further phase adds its own service here.
        AbilityServices.bootstrap()

        // Error's stolen dream refuses its owner a bed. A Fabric event cannot be unregistered,
        // so the hook is hung once here and the theft registry decides whether it bites.
        ConceptualTheft.install()

        // The spirit world is data in itself, but a player who left the game inside it would
        // stay there forever: the session that could bring them back goes out together with
        // their connection.
        SpiritWorld.guardStranded()

        ModChannels.STATUS_REQUEST.receiveOnServer { _, context ->
            val player = context.player()
            BeyonderStatus.push(player)
            BeyonderStatus.pushRoster(player)
            AbilityServices.statePump.pushNow(player.uuid)
        }

        // The cast key. The server picks the ability — see `CastRequestPayload`.
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
