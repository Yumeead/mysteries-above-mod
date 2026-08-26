package me.vangoo.mysteries.net.payload

import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клієнт: стан Потойбічного, яким його бачить власник.
 *
 * `beyonder == null` означає звичайного гравця. Шлях і послідовність або є разом,
 * або їх нема разом — тому вони живуть у вкладеному типі, а не двома нульованими
 * полями: формат просто не вміє виразити «шлях без послідовності».
 */
data class BeyonderStatusPayload(val beyonder: Beyonder?) : CustomPacketPayload {

    /** VO їдуть із `core` як є — по дроту летять лише їхні числа. */
    data class Beyonder(
        val pathwayId: String,
        val sequence: Sequence,
        val spirituality: Spirituality,
    )

    override fun type(): CustomPacketPayload.Type<BeyonderStatusPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BeyonderStatusPayload> =
            CustomPacketPayload.Type(modId("beyonder_status"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BeyonderStatusPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: BeyonderStatusPayload, buf: RegistryFriendlyByteBuf) {
            val beyonder = payload.beyonder
            buf.writeBoolean(beyonder != null)
            if (beyonder == null) return
            buf.writeUtf(beyonder.pathwayId)
            buf.writeVarInt(beyonder.sequence.level)
            buf.writeVarInt(beyonder.spirituality.current)
            buf.writeVarInt(beyonder.spirituality.maximum)
        }

        private fun read(buf: RegistryFriendlyByteBuf): BeyonderStatusPayload {
            if (!buf.readBoolean()) return BeyonderStatusPayload(null)
            val pathwayId = buf.readUtf()
            val sequence = Sequence(buf.readVarInt())
            val current = buf.readVarInt()
            val maximum = buf.readVarInt()
            return BeyonderStatusPayload(
                Beyonder(pathwayId, sequence, Spirituality(current, maximum)),
            )
        }
    }
}
