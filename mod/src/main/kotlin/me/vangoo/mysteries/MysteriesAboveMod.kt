package me.vangoo.mysteries

import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.error.abilities.ConceptualTheft
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.BeyonderStore
import me.vangoo.mysteries.pathways.fool.abilities.PaperDolls
import me.vangoo.mysteries.pathways.sun.Oaths
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.item.ModItems
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.pathways.death.Death
import me.vangoo.mysteries.pathways.fool.Marionettes
import me.vangoo.mysteries.pathways.fool.PaperThrowTrigger
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
        ModEntities.register()

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

        // Fool's "hold paper, right-click, throw" has no cast and no menu — same reasoning
        // as ConceptualTheft above, only the registry it defers to is the Beyonder's own list.
        PaperThrowTrigger.install()
        PaperDolls.register()

        // Fool's threads: orders and possession are not casts, so they have their own channel and
        // their own clock rather than a place in the ability pipeline.
        Marionettes.install()

        // A borrowed face is a lie told to each client separately, so a player who joins while
        // the mask is up has to be told it too — the one gap the vanilla join sequence leaves.
        Disguise.install()

        // The spirit world is data in itself, but a player who left the game inside it would
        // stay there forever: the session that could bring them back goes out together with
        // their connection.
        SpiritWorld.guardStranded()

        // Death's world-level memory: every death leaves a trace, every servant is a saved mob,
        // the Underworld gives back whoever it holds — none of that waits for a cast.
        Death.install()

        // Sun's Oaths are debts the world holds: paid by hand, broken on the wall clock.
        Oaths.install()

        // Last on purpose: the handlers above still change a Beyonder on the way out (a
        // Marionettist leaving their puppet), and the save must see the result.
        BeyonderStore.install()

        // Every player intent: the cast key, the wheel, Z/X and the group hotkeys. An absent
        // identity means "whatever is selected" — the server picks it, as it always has.
        ModChannels.ABILITY_ACTION.receiveOnServer { payload, context ->
            AbilityRequests.act(context.player(), payload)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> MysteriesCommand.register(dispatcher) }

        log.info(
            "Mysteries Above: спільна частина піднялась — каналів {}, шляхів {}",
            ModChannels.all.size,
            ModPathways.all.size,
        )
    }
}
