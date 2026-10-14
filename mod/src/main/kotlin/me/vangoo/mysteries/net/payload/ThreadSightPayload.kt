package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server → client: what a Marionettist's Spirit Vision needs that the client cannot see itself.
 *
 * Every free thread is drawn from what the client already has — the living things around it,
 * their health, whether they are dying — so none of that travels. What does travel is the one
 * thing no entity carries: **who holds whom**, and how far the grip has gone.
 *
 * Sent with Thread Sight's own beat (twice a second) and only to those who have it. The client
 * stops drawing when the packets stop, so losing the ability needs no farewell packet.
 *
 * @property range how far the free threads are seen, in blocks
 */
data class ThreadSightPayload(
    val range: Int,
    val bonds: List<Bond>,
) : CustomPacketPayload {

    /**
     * One body on someone's threads.
     *
     * Entity ids rather than UUIDs: the client looks entities up by id, and both ends are in the
     * same level by construction.
     *
     * @property held the body the threads come out of
     * @property holder the one pulling them, or [NO_HOLDER] — not in this level
     * @property grip `0..1` while the grip is being taken, exactly `1` for a finished puppet
     */
    data class Bond(val held: Int, val holder: Int, val grip: Float)

    override fun type(): CustomPacketPayload.Type<ThreadSightPayload> = TYPE

    companion object {

        const val NO_HOLDER = -1

        val TYPE: CustomPacketPayload.Type<ThreadSightPayload> = CustomPacketPayload.Type(modId("thread_sight"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ThreadSightPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    buf.writeVarInt(payload.range)
                    buf.writeVarInt(payload.bonds.size)
                    payload.bonds.forEach { bond ->
                        buf.writeVarInt(bond.held)
                        buf.writeVarInt(bond.holder)
                        buf.writeFloat(bond.grip)
                    }
                },
                StreamDecoder { buf ->
                    val range = buf.readVarInt()
                    val bonds = List(buf.readVarInt()) {
                        Bond(buf.readVarInt(), buf.readVarInt(), buf.readFloat().coerceIn(0f, 1f))
                    }
                    ThreadSightPayload(range, bonds)
                },
            )
    }
}
