package me.vangoo.mysteries.pathways.fool

import com.mojang.authlib.properties.Property
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.UUIDUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import java.util.Optional
import java.util.UUID

/**
 * Every face the world has seen, and who has seen whose.
 *
 * The plugin's Faceless picked from a menu of every player who had ever joined. Here a face is
 * known only to those who **saw** it: a player in the field of view, within 32 blocks, with nothing
 * in between, checked once a second. Everyone keeps the list from the day this shipped — not only
 * the Faceless — so a player who drinks the Seq. 6 potion already carries the faces they met before.
 * The Seer's spirit pendulum reads the same list: it seeks only those you have laid eyes on.
 *
 * Two records, both saved with the world, as oaths and thefts are:
 * - **known** — the last name and skin of everyone who joined, refreshed on every join (overworld);
 * - **seen** — on each player, whom they have seen and when last (wall-clock millis).
 *
 * A masked player is seen as the face they wear: whoever met the Faceless in someone else's face
 * remembers that face, not the Faceless's own.
 */
object Faces {

    private val PROPERTY: Codec<Property> = RecordCodecBuilder.create { row ->
        row.group(
            Codec.STRING.fieldOf("name").forGetter(Property::name),
            Codec.STRING.fieldOf("value").forGetter(Property::value),
            Codec.STRING.optionalFieldOf("signature").forGetter { Optional.ofNullable(it.signature) },
        ).apply(row) { name, value, signature -> Property(name, value, signature.orElse(null)) }
    }

    private val FACE: Codec<Disguise.Face> = RecordCodecBuilder.create { row ->
        row.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Disguise.Face::id),
            Codec.STRING.fieldOf("name").forGetter(Disguise.Face::name),
            PROPERTY.optionalFieldOf("textures").forGetter { Optional.ofNullable(it.textures) },
        ).apply(row) { id, name, textures -> Disguise.Face(id, name, textures.orElse(null)) }
    }

    /** Lazy, and touched in [install]: Fabric registers attachments through a mixin, unit tests have none. */
    private val KNOWN: AttachmentType<Map<UUID, Disguise.Face>> by lazy {
        AttachmentRegistry.create(modId("known_faces")) { it.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, FACE)) }
    }

    private val SEEN: AttachmentType<Map<UUID, Long>> by lazy {
        AttachmentRegistry.create(modId("seen_faces")) { it.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)) }
    }

    private val SHAPESHIFTING = AbilityIdentity.of("fool_shapeshifting")

    fun install() {
        KNOWN
        SEEN
        ServerPlayConnectionEvents.JOIN.register { handler, _, server -> remember(server, handler.player) }
        ServerTickEvents.END_SERVER_TICK.register { server -> if (server.tickCount % TICKS_PER_SECOND == 0) look(server) }
    }

    /** The face [id] had the last time it joined, or `null` — it never did. */
    fun known(server: MinecraftServer, id: UUID): Disguise.Face? = server.overworld().getAttached(KNOWN)?.get(id)

    /** The faces [viewer] has seen, with when, newest first — their own left out. */
    fun seenBy(viewer: ServerPlayer): List<Pair<Disguise.Face, Long>> {
        val known = viewer.level().server.overworld().getAttached(KNOWN).orEmpty()
        return viewer.getAttached(SEEN).orEmpty().entries
            .filter { it.key != viewer.uuid }
            .mapNotNull { (id, at) -> known[id]?.let { it to at } }
            .sortedByDescending { it.second }
    }

    private fun remember(server: MinecraftServer, player: ServerPlayer) {
        val overworld = server.overworld()
        overworld.setAttached(KNOWN, overworld.getAttached(KNOWN).orEmpty() + (player.uuid to Disguise.faceOf(player)))
    }

    /** Once a second: who stands in whose field of view, near enough and in the open. */
    private fun look(server: MinecraftServer) {
        val now = System.currentTimeMillis()
        val players = server.playerList.players
        for (viewer in players) {
            if (viewer.isSpectator) continue
            val look = viewer.getViewVector(1f)
            var seen: MutableMap<UUID, Long>? = null
            for (other in players) {
                if (other === viewer || other.level() !== viewer.level() || other.isSpectator || other.isInvisible) continue
                val to = other.eyePosition.subtract(viewer.eyePosition)
                if (to.lengthSqr() > SIGHT * SIGHT || look.dot(to.normalize()) < FIELD) continue
                if (!viewer.hasLineOfSight(other)) continue
                val face = Disguise.wornBy(other.uuid)?.id ?: other.uuid
                val record = seen ?: viewer.getAttached(SEEN).orEmpty().toMutableMap().also { seen = it }
                if (record.put(face, now) == null) sketch(viewer, other)
            }
            seen?.let { viewer.setAttached(SEEN, it.toMap()) }
        }
    }

    /**
     * The Faceless's own way of remembering, the first time and only for them: a scrap of paper with
     * the face sketched on it comes off the head of the one seen, flies to the Faceless's hand and is
     * gone, with the hush of a page. Everyone else records a face silently.
     */
    private fun sketch(viewer: ServerPlayer, seen: ServerPlayer) {
        val beyonder = AbilityServices.beyonders.of(viewer.uuid) ?: return
        if (beyonder.abilityBy(SHAPESHIFTING) == null) return
        ModChannels.WORLD_EFFECT.sendTo(viewer, WorldEffectPayload.Face(WorldEffectPayload.Face.Kind.SKETCH, seen.id, viewer.id))
        privateSound(viewer, viewer.position(), SoundEvents.BOOK_PAGE_TURN, 0.5f, 1.5f)
    }

    /** How far a face is made out, and how wide the eye takes it in (cosine of half the field: ~120°). */
    private const val SIGHT = 32.0
    private const val FIELD = 0.5
}
