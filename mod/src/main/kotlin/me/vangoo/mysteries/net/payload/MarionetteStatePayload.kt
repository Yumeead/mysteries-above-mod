package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Server → client: the row of threads, everything the Marionettist's HUD and the wheel's threads
 * tab draw.
 *
 * A third snapshot beside the roster and the ability state, and separate for the same reason they
 * are separate from each other: its rhythm is its own. The roster flies a handful of times a
 * session, the ability state at most once a second, and this one only while somebody actually
 * holds threads — which for every player but one is never.
 *
 * The strand deadline travels as **seconds left** rather than an absolute stamp, unlike the
 * cooldowns in `AbilityStatePayload`. Those tick on the mod's clock, which the client also keeps;
 * this one runs on the wall clock (see `Marionettes`), and the client has no wall clock of the
 * server's. Ten minutes counted down once a second is close enough for a number that only ever
 * gets read in whole minutes.
 */
data class MarionetteStatePayload(
    val entries: List<Entry>,
    val selected: UUID?,
    val possessing: UUID?,
    val limit: Int,
) : CustomPacketPayload {

    /**
     * One puppet on the row — and enough of its body for the wheel to draw it even when it stands
     * too far away for this client to have it: what it is ([type]), whose face it wears ([face]),
     * what it holds and wears ([equipment]: main hand, head, chest, legs, feet) and where ([at]).
     *
     * @property entity its network id, to draw the live body when this client has it
     * @property order what it is doing
     * @property strandSeconds how long until the thread snaps; `0` — it is in reach
     * @property distance blocks to the Marionettist; `-1` — another world
     * @property pathway what it was made of, if it was a Beyonder; [sequence] `-1` otherwise
     */
    data class Entry(
        val id: UUID,
        val name: String,
        val health: Float,
        val maxHealth: Float,
        val order: Order,
        val strandSeconds: Int,
        val distance: Int,
        val entity: Int,
        val type: String,
        val face: ResolvableProfile?,
        val pathway: String?,
        val sequence: Int,
        val equipment: List<ItemStack>,
        val at: Vec3,
    )

    /** The verbs, as the HUD and the wheel say them back. */
    enum class Order(val caption: String) {
        STAND("чекає"),
        GOING("іде"),
        FOLLOWING("за вами"),
        ATTACKING("б'ється"),
        GUARDING("охороняє"),
        GATHERING("збирає"),
        USING("іде натиснути"),
    }

    override fun type(): CustomPacketPayload.Type<MarionetteStatePayload> = TYPE

    companion object {

        val EMPTY = MarionetteStatePayload(emptyList(), null, null, 0)

        /** Main hand, head, chest, legs, feet — the order [Entry.equipment] travels in. */
        const val EQUIPMENT_SLOTS = 5

        val TYPE: CustomPacketPayload.Type<MarionetteStatePayload> =
            CustomPacketPayload.Type(modId("marionette_state"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MarionetteStatePayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private val FACE: StreamCodec<io.netty.buffer.ByteBuf, java.util.Optional<ResolvableProfile>> =
            ByteBufCodecs.optional(ResolvableProfile.STREAM_CODEC)

        private fun write(payload: MarionetteStatePayload, buf: RegistryFriendlyByteBuf) {
            buf.writeVarInt(payload.limit)
            buf.writeVarInt(payload.entries.size)
            payload.entries.forEach { entry ->
                buf.writeUUID(entry.id)
                buf.writeUtf(entry.name)
                buf.writeFloat(entry.health)
                buf.writeFloat(entry.maxHealth)
                buf.writeVarInt(entry.order.ordinal)
                buf.writeVarInt(entry.strandSeconds)
                buf.writeVarInt(entry.distance)
                buf.writeVarInt(entry.entity)
                buf.writeUtf(entry.type)
                FACE.encode(buf, java.util.Optional.ofNullable(entry.face))
                buf.writeNullable(entry.pathway) { b, name -> b.writeUtf(name) }
                buf.writeVarInt(entry.sequence)
                repeat(EQUIPMENT_SLOTS) { ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.equipment.getOrElse(it) { ItemStack.EMPTY }) }
                Vec3.STREAM_CODEC.encode(buf, entry.at)
            }
            buf.writeBoolean(payload.selected != null)
            payload.selected?.let(buf::writeUUID)
            buf.writeBoolean(payload.possessing != null)
            payload.possessing?.let(buf::writeUUID)
        }

        private fun read(buf: RegistryFriendlyByteBuf): MarionetteStatePayload {
            val limit = buf.readVarInt()
            val entries = List(buf.readVarInt()) {
                Entry(
                    id = buf.readUUID(),
                    name = buf.readUtf(),
                    health = buf.readFloat(),
                    maxHealth = buf.readFloat(),
                    order = order(buf.readVarInt()),
                    strandSeconds = buf.readVarInt(),
                    distance = buf.readVarInt(),
                    entity = buf.readVarInt(),
                    type = buf.readUtf(),
                    face = FACE.decode(buf).orElse(null),
                    pathway = buf.readNullable { it.readUtf() },
                    sequence = buf.readVarInt(),
                    equipment = List(EQUIPMENT_SLOTS) { ItemStack.OPTIONAL_STREAM_CODEC.decode(buf) },
                    at = Vec3.STREAM_CODEC.decode(buf),
                )
            }
            val selected = if (buf.readBoolean()) buf.readUUID() else null
            val possessing = if (buf.readBoolean()) buf.readUUID() else null
            return MarionetteStatePayload(entries, selected, possessing, limit)
        }

        /** A number off the network is foreign: its bounds are checked here, not by a crash. */
        private fun order(ordinal: Int): Order {
            val values = Order.entries
            require(ordinal in values.indices) { "Невідомий наказ: $ordinal" }
            return values[ordinal]
        }
    }
}
