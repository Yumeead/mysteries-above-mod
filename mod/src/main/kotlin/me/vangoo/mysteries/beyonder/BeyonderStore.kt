package me.vangoo.mysteries.beyonder

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.pathways.door.abilities.Record
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * A Beyonder's progress, kept on the player themselves.
 *
 * The plugin wrote `beyonders.json` — a second file beside the world that had to agree with it.
 * Here the record is a persistent attachment: vanilla writes it into the player's own save file
 * together with their inventory and position, so it travels with the world, survives a crash
 * exactly as far as the inventory does, and goes away with the player file. There is nothing to
 * keep consistent.
 *
 * Only what cannot be derived is stored: the ability list follows from the pathway and the
 * Sequence. Off-pathway powers are loans with clocks of their own and are not written here: a
 * theft lives in [TheftRegistry] (saved on the overworld) and is handed back on join; possession
 * and analysis end with the session. The one exception is a [Record] page, the player's until spent.
 *
 * The registry holds only those in the game: [load] on join, [save] and forget on leave.
 */
object BeyonderStore {

    private val log = LoggerFactory.getLogger(MOD_ID)

    data class Saved(
        val pathway: String,
        val sequence: Int,
        val mastery: Double,
        val spirituality: Int,
        val sanityLoss: Int,
        val pages: List<String>,
    )

    val CODEC: Codec<Saved> = RecordCodecBuilder.create { row ->
        row.group(
            Codec.STRING.fieldOf("pathway").forGetter(Saved::pathway),
            Codec.INT.fieldOf("sequence").forGetter(Saved::sequence),
            Codec.DOUBLE.fieldOf("mastery").forGetter(Saved::mastery),
            Codec.INT.fieldOf("spirituality").forGetter(Saved::spirituality),
            Codec.INT.fieldOf("sanity_loss").forGetter(Saved::sanityLoss),
            Codec.STRING.listOf().optionalFieldOf("pages", emptyList()).forGetter(Saved::pages),
        ).apply(row, ::Saved)
    }

    // Lazy: Fabric registers attachments through a mixin, and unit tests have none.
    private val TYPE: AttachmentType<Saved> by lazy {
        AttachmentRegistry.create(modId("beyonder")) { it.persistent(CODEC).copyOnDeath() }
    }

    /** Every theft in force, on the overworld: a victim's suppression holds while the thief is away. */
    private val THEFTS: AttachmentType<List<TheftRegistry.Theft>> by lazy {
        AttachmentRegistry.create(modId("thefts")) { it.persistent(TheftRegistry.CODEC) }
    }

    fun snapshot(beyonder: Beyonder) = Saved(
        pathway = beyonder.pathway.name,
        sequence = beyonder.sequenceLevel,
        mastery = beyonder.mastery.value,
        spirituality = beyonder.spirituality.current,
        sanityLoss = beyonder.sanityLoss.scale,
        pages = beyonder.abilities.filter(Record::isPage).map { it.identity.id },
    )

    fun restore(player: ServerPlayer, saved: Saved): Beyonder? {
        val pathway = ModPathways.byName(saved.pathway)
            ?: return null.also { log.warn("Unknown pathway '{}' saved on {}", saved.pathway, player.name.string) }
        return Beyonder.restore(
            playerId = player.uuid,
            pathway = pathway,
            sequence = Sequence.of(saved.sequence),
            mastery = Mastery.of(saved.mastery),
            currentSpirituality = saved.spirituality,
            sanityLoss = SanityLoss.of(saved.sanityLoss),
            offPathwayAbilities = saved.pages.mapNotNull { Record.page(AbilityIdentity.of(it)) },
        )
    }

    /** Puts a returning player back into the registry; must run before their passives activate. */
    fun load(player: ServerPlayer) {
        if (AbilityServices.beyonders.of(player.uuid) != null) return
        val saved = player.getAttached(TYPE) ?: return
        val beyonder = restore(player, saved) ?: return
        AbilityServices.beyonders.register(beyonder)
        // A stolen power is not saved with the thief: it is handed back while the record holds it.
        TheftRegistry.heldBy(player.uuid)?.takeUnless { it.revoked }
            ?.let { ModPathways.ability(it.identity) }
            ?.let(beyonder::grantOffPathway)
    }

    private fun saveThefts(server: MinecraftServer) {
        server.overworld().setAttached(THEFTS, TheftRegistry.snapshot())
    }

    fun save(player: ServerPlayer) {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return
        player.setAttached(TYPE, snapshot(beyonder))
    }

    /** The player is no longer a Beyonder — not even on their next join. */
    fun erase(player: ServerPlayer) {
        player.removeAttached(TYPE)
    }

    /**
     * Registered **last** in mod init: Fabric runs handlers in registration order, and those
     * before this one (a Marionettist leaving their puppet) still change the Beyonder on the way out.
     */
    fun install() {
        TYPE
        THEFTS

        // Before any player joins, so the thief's join already sees their record.
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            TheftRegistry.restore(server.overworld().getAttached(THEFTS).orEmpty())
        }

        // Fabric fires this before `PlayerList.remove`, i.e. before the player file is written.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            save(handler.player)
            AbilityServices.beyonders.forget(handler.player.uuid)
        }

        // Fired before the player list is saved on shutdown.
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            server.playerList.players.forEach(::save)
            saveThefts(server)
        }

        // Autosave writes whatever the attachment holds, so it is kept at most a second behind:
        // a crash then loses a second of regeneration rather than everything since joining.
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % TICKS_PER_SECOND == 0) {
                server.playerList.players.forEach(::save)
                saveThefts(server)
            }
        }
    }
}
