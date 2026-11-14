package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.phys.Vec3
import io.netty.handler.codec.DecoderException

/**
 * Server → client: what the Seer's Danger Intuition feels coming — one threat, for the arrows around
 * the crosshair. The client turns each arrow towards it every frame, so the server says only who, where
 * it was, and when the blow lands.
 *
 * @property entity the threat's id, so the arrow follows it as it moves; where the client has no such
 *   entity, the arrow points at [at]
 * @property ticks ticks until the blow, or how long the threat is felt if it has no blow to time —
 *   under a second, the arrow turns red and pulses
 * @property urgent whether [ticks] times a blow (an arrow in flight, a fuse) rather than a presence
 */
data class DangerPayload(val entity: Int, val at: Vec3, val ticks: Int, val urgent: Boolean) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DangerPayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<DangerPayload> = CustomPacketPayload.Type(modId("danger"))

        private const val MAX_TICKS = 200

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DangerPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    buf.writeVarInt(payload.entity)
                    Vec3.STREAM_CODEC.encode(buf, payload.at)
                    buf.writeVarInt(payload.ticks)
                    buf.writeBoolean(payload.urgent)
                },
                StreamDecoder { buf ->
                    val entity = buf.readVarInt()
                    val at = Vec3.STREAM_CODEC.decode(buf)
                    if (!at.x.isFinite() || !at.y.isFinite() || !at.z.isFinite()) throw DecoderException("Загроза ніде")
                    DangerPayload(entity, at, buf.readVarInt().coerceIn(0, MAX_TICKS), buf.readBoolean())
                },
            )
    }
}
