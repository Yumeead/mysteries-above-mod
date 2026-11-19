package me.vangoo.mysteries.pathways.justiciar

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.DossiersPayload
import me.vangoo.mysteries.pathways.fool.Faces
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.util.UUID

/**
 * The Sheriff's memory of those they have recognised (Recognition, Seq. 8): "as long as they see a person in the flesh
 * they firmly remember the target's appearance, and obtain an additional sense on an extraordinary level".
 *
 * Kept on the Sheriff themselves (an attachment), so it outlives leaving the game. Who is remembered is the person, not
 * the face: a remembered player under someone else's face (the Fool's Shapeshifting) is still themselves to the
 * Sheriff. While one of them is within [SENSE_RANGE] of the Sheriff, the Sheriff knows where they are; the client draws
 * their footprints and the book (B, «Справи») shows each file.
 */
object Dossiers {

    /** One file: who, what kind, whether a Beyonder, when and where first seen, last sensed, and if dead. */
    data class Dossier(
        val id: UUID,
        val name: String,
        val kind: String,
        val player: Boolean,
        val beyonder: Boolean,
        val rememberedAt: Long,
        val rememberedPos: BlockPos,
        val rememberedIn: String,
        val seenAt: Long,
        val seenPos: BlockPos,
        val seenIn: String,
        val dead: Boolean,
    )

    private val DOSSIER: Codec<Dossier> = RecordCodecBuilder.create { row ->
        row.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Dossier::id),
            Codec.STRING.fieldOf("name").forGetter(Dossier::name),
            Codec.STRING.fieldOf("kind").forGetter(Dossier::kind),
            Codec.BOOL.fieldOf("player").forGetter(Dossier::player),
            Codec.BOOL.fieldOf("beyonder").forGetter(Dossier::beyonder),
            Codec.LONG.fieldOf("remembered_at").forGetter(Dossier::rememberedAt),
            BlockPos.CODEC.fieldOf("remembered_pos").forGetter(Dossier::rememberedPos),
            Codec.STRING.fieldOf("remembered_in").forGetter(Dossier::rememberedIn),
            Codec.LONG.fieldOf("seen_at").forGetter(Dossier::seenAt),
            BlockPos.CODEC.fieldOf("seen_pos").forGetter(Dossier::seenPos),
            Codec.STRING.fieldOf("seen_in").forGetter(Dossier::seenIn),
            Codec.BOOL.fieldOf("dead").forGetter(Dossier::dead),
        ).apply(row, ::Dossier)
    }

    /** Lazy, and touched in [install]: Fabric registers attachments through a mixin, unit tests have none. */
    private val FILES: AttachmentType<List<Dossier>> by lazy {
        AttachmentRegistry.create(modId("dossiers")) { it.persistent(DOSSIER.listOf()) }
    }

    /** Per Sheriff, the remembered ones within reach at the last look: leaving it is when the file is brought up to date. */
    private val near = HashMap<UUID, Set<UUID>>()

    fun install() {
        FILES
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> sync(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> near.remove(handler.player.uuid) }
        ServerTickEvents.END_SERVER_TICK.register { server -> if (server.tickCount % TICKS_PER_SECOND == 0) sense(server) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> if (entity !is Player) died(entity) }
    }

    fun of(sheriff: ServerPlayer): List<Dossier> = sheriff.getAttached(FILES).orEmpty()

    /**
     * Remembers [target] for [sheriff], first among their files; the oldest is forgotten past [slots]. Someone already
     * remembered is only brought up to date and moved first. Returns whether they were new.
     */
    fun remember(sheriff: ServerPlayer, target: LivingEntity, slots: Int): Boolean {
        val files = of(sheriff)
        val old = files.firstOrNull { it.id == target.uuid }
        val now = System.currentTimeMillis()
        val here = target.blockPosition()
        val dimension = target.level().dimension().identifier().toString()
        val file = Dossier(
            id = target.uuid,
            name = nameOf(target),
            kind = BuiltInRegistries.ENTITY_TYPE.getKey(target.type).toString(),
            player = target is Player,
            beyonder = AbilityServices.beyonders.of(target.uuid) != null,
            rememberedAt = old?.rememberedAt ?: now,
            rememberedPos = old?.rememberedPos ?: here,
            rememberedIn = old?.rememberedIn ?: dimension,
            seenAt = now,
            seenPos = here,
            seenIn = dimension,
            dead = false,
        )
        sheriff.setAttached(FILES, (listOf(file) + files.filter { it.id != target.uuid }).take(slots))
        near[sheriff.uuid] = near[sheriff.uuid].orEmpty() + target.uuid
        sync(sheriff)
        return old == null
    }

    /** A player's own name, never the face they wear; a creature's custom name or its kind. */
    private fun nameOf(target: Entity): String =
        if (target is ServerPlayer) target.gameProfile.name else target.name.string

    /** Once a second: each remembered one near a Sheriff is sensed where they stand. */
    private fun sense(server: MinecraftServer) {
        for (sheriff in server.playerList.players) {
            val files = sheriff.getAttached(FILES) ?: continue
            if (files.isEmpty()) continue
            val now = System.currentTimeMillis()
            val within = HashSet<UUID>()
            var changed = false
            val updated = files.map { file ->
                val body = sheriff.level().getEntityInAnyDimension(file.id)
                if (body == null || body.level() !== sheriff.level() || body.distanceTo(sheriff) > SENSE_RANGE) return@map file
                within += file.id
                changed = true
                file.copy(seenAt = now, seenPos = body.blockPosition(), seenIn = body.level().dimension().identifier().toString())
            }
            if (changed) sheriff.setAttached(FILES, updated)
            // Only leaving reach changes what the book says: while near, the client sees for itself.
            val before = near[sheriff.uuid].orEmpty()
            near[sheriff.uuid] = within
            if (before.any { it !in within }) sync(sheriff)
        }
    }

    private fun died(entity: LivingEntity) {
        val server = entity.level().server ?: return
        for (sheriff in server.playerList.players) {
            val files = sheriff.getAttached(FILES) ?: continue
            if (files.none { it.id == entity.uuid }) continue
            sheriff.setAttached(FILES, files.map { if (it.id == entity.uuid) it.copy(dead = true, seenPos = entity.blockPosition()) else it })
            sync(sheriff)
        }
    }

    /** The Sheriff's files, to their own client; players carry the face they truly have. */
    fun sync(sheriff: ServerPlayer) {
        val server = sheriff.level().server
        val entries = of(sheriff).map { file ->
            DossiersPayload.Entry(
                file, if (file.player) Faces.known(server, file.id)?.let(Disguise::profileOf) else null,
            )
        }
        ModChannels.DOSSIERS.sendTo(sheriff, DossiersPayload(entries))
    }

    /** How far a Sheriff senses the one they remember: about as far as a client keeps others loaded. */
    const val SENSE_RANGE = 80.0
}
