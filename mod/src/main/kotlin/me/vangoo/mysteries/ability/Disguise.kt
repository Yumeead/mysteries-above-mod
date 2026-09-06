package me.vangoo.mysteries.ability

import com.google.common.collect.ArrayListMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.PlayerModelPart
import org.slf4j.LoggerFactory
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Someone else's face: a player is shown to every other client under a borrowed name and skin.
 *
 * ## Why the profile is faked in the packet and not on the player
 *
 * The plugin swapped the caster's own `PlayerProfile` — Paper let it. Here the equivalent field
 * (`Player.gameProfile`) is what the server itself answers with for permissions, chat signing and
 * name lookups, so rewriting it would disguise the Beyonder from the *server* as well. Only the
 * packet is faked: server-side the player stays exactly who they are, and nothing but the other
 * clients' eyes is lied to.
 *
 * `ClientboundPlayerInfoUpdatePacket` builds its entries from real `ServerPlayer`s and has no
 * constructor that takes them ready-made, hence the one widened field — see
 * `mysteries_above.accesswidener`.
 *
 * ## The wearer is left out on purpose
 *
 * The mask is never sent to the caster's own client. Removing your own player-info entry and
 * building it back is the one case with nothing to gain (you would see a different skin in F5 and
 * a different name in your own tab row) and a real client to desync. The Beyonder is told whose
 * face they wear on the action bar instead.
 *
 * ## Who has to be told again
 *
 * The client keeps a player-info entry until the server removes it, so vanilla's own periodic
 * updates (latency, game mode) carry no profile and leave the mask alone. The one gap is a player
 * who **joins while the mask is up** — they get the real entry from the vanilla join sequence, so
 * [install] hangs a hook that re-sends every live mask to them.
 */
object Disguise {

    private const val TEXTURES = "textures"

    private val LOGGER = LoggerFactory.getLogger("mysteries_above")

    private val worn = ConcurrentHashMap<UUID, Face>()

    /**
     * The borrowed appearance — the target's whole identity, not just their name.
     *
     * [textures] is `null` whenever the server holds no skin for that player, which on an
     * **offline-mode** server is always: an offline profile carries no properties at all. That is
     * exactly why [id] is carried too. With no textures the client falls back to the default skin
     * *for the profile's own id*, so passing the target's id along means the face changes even
     * where there is no skin in the world to copy.
     */
    class Face(val id: UUID, val name: String, val textures: Property?)

    /** What [target] looks like, ready to be worn by someone else. */
    fun faceOf(target: ServerPlayer): Face =
        Face(target.uuid, target.gameProfile.name, target.gameProfile.properties()[TEXTURES].firstOrNull())

    fun isWorn(id: UUID): Boolean = worn.containsKey(id)

    /** The face [caster] is wearing right now, or `null`. */
    fun wornBy(id: UUID): Face? = worn[id]

    fun wear(caster: ServerPlayer, face: Face) {
        worn[caster.uuid] = face
        show(caster, viewersOf(caster), profile(face))
    }

    /** Back to their own face, for everyone still watching. */
    fun remove(caster: ServerPlayer) {
        if (worn.remove(caster.uuid) == null) return
        show(caster, viewersOf(caster), caster.gameProfile)
    }

    /** The wearer is gone: their entry left with them, so only the bookkeeping is left to drop. */
    fun forget(id: UUID) {
        worn.remove(id)
    }

    fun install() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val newcomer = handler.player
            for ((id, face) in worn) {
                val masked = server.playerList.getPlayer(id) ?: continue
                if (masked.uuid == newcomer.uuid) continue
                show(masked, listOf(newcomer), profile(face))
            }
        }

        // A chat line carries the speaker's name resolved on the **server**, from the real profile
        // the mask deliberately never touches — so the tab row lies while the chat tells the truth,
        // and the disguise fails the moment its wearer speaks. The signed message is refused and
        // said again under the borrowed name instead. `ALLOW_CHAT_MESSAGE` is a ready Fabric hook,
        // i.e. step one of the search order; the cost is that the line arrives unsigned, which for
        // words spoken through someone else's face is the honest outcome anyway.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, _ ->
            val face = worn[sender.uuid] ?: return@register true
            sender.level().server.playerList.broadcastSystemMessage(
                Component.translatable("chat.type.text", Component.literal(face.name), message.decoratedContent()),
                false,
            )
            false
        }
    }

    private fun viewersOf(caster: ServerPlayer): List<ServerPlayer> =
        caster.level().server.playerList.players.filter { it.uuid != caster.uuid }

    /**
     * The profile the other clients are handed. Its **id is the target's**, not the wearer's: the
     * entry is still filed under the wearer's UUID ([ClientboundPlayerInfoUpdatePacket.Entry]'s
     * own `profileId`, which is what binds it to the body on screen), while the profile inside is
     * the borrowed one whole. Nothing looks the wearer up through it, and the client's default
     * skin is picked from exactly this id — which is the only thing that changes at all when the
     * server has no textures to copy.
     */
    private fun profile(face: Face): GameProfile {
        val properties = PropertyMap(ArrayListMultimap.create<String, Property>())
        face.textures?.let { properties.put(TEXTURES, it) }
        return GameProfile(face.id, face.name, properties)
    }

    /**
     * Rebuilds [caster]'s identity on each viewer's client: drop the player-info entry, put back a
     * fabricated one, then respawn the entity so the name above the head and the skin are read
     * again. The entity half is [SelectiveVisibility]'s existing pair — a player-info entry alone
     * changes the tab row, but the body on screen was already built from the old one.
     */
    private fun show(caster: ServerPlayer, viewers: List<ServerPlayer>, profile: GameProfile) {
        if (viewers.isEmpty()) return

        val entry = ClientboundPlayerInfoUpdatePacket.Entry(
            caster.uuid,
            profile,
            true,
            caster.connection.latency(),
            caster.gameMode(),
            caster.tabListDisplayName,
            caster.isModelPartShown(PlayerModelPart.HAT),
            caster.tabListOrder,
            caster.chatSession?.asData(),
        )
        val update = ClientboundPlayerInfoUpdatePacket(FULL_ENTRY, listOf(caster))
        update.entries = listOf(entry)

        val forget = ClientboundPlayerInfoRemovePacket(listOf(caster.uuid))
        var told = 0
        for (viewer in viewers) {
            if (!viewer.connection.isAcceptingMessages) continue
            viewer.connection.send(forget)
            viewer.connection.send(update)
            SelectiveVisibility.hide(caster, viewer)
            SelectiveVisibility.show(caster, viewer)
            told++
        }
        LOGGER.info(
            "Disguise: {} shown as {} (profile id {}, textures {}) to {} viewer(s)",
            caster.gameProfile.name,
            profile.name,
            profile.id,
            if (profile.properties()[TEXTURES].isEmpty()) "absent" else "copied",
            told,
        )
    }

    /**
     * Everything vanilla writes when a player first appears. The chat session is carried over as
     * it is — it is keyed by UUID and says nothing about the name, so keeping it spares every
     * onlooker an "unsigned chat" warning the moment the Beyonder speaks.
     */
    private val FULL_ENTRY: EnumSet<ClientboundPlayerInfoUpdatePacket.Action> = EnumSet.of(
        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
        ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
    )
}
