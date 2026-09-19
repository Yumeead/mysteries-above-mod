package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * Server → client: the row of threads, everything the Marionettist's HUD draws.
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
     * One puppet on the row.
     *
     * @property order what it is doing — the ordinal of [Order]
     * @property strandSeconds how long until the thread snaps; `0` — it is in reach
     */
    data class Entry(
        val id: UUID,
        val name: String,
        val health: Float,
        val maxHealth: Float,
        val order: Order,
        val strandSeconds: Int,
        val distance: Int,
    )

    /** The four verbs, as the HUD says them back. */
    enum class Order(val caption: String) {
        STAND("чекає"),
        GOING("іде"),
        FOLLOWING("за вами"),
        ATTACKING("б'ється"),
    }

    override fun type(): CustomPacketPayload.Type<MarionetteStatePayload> = TYPE

    companion object {

        val EMPTY = MarionetteStatePayload(emptyList(), null, null, 0)

        val TYPE: CustomPacketPayload.Type<MarionetteStatePayload> =
            CustomPacketPayload.Type(modId("marionette_state"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MarionetteStatePayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

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
