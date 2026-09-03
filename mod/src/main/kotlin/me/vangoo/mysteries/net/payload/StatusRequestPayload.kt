package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client → server: "re-read my state". There is no body — the server takes the player from the
 * connection context, not from the packet; otherwise anyone could ask for someone else's state.
 */
data object StatusRequestPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<StatusRequestPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<StatusRequestPayload> =
        CustomPacketPayload.Type(modId("status_request"))

    val CODEC: StreamCodec<RegistryFriendlyByteBuf, StatusRequestPayload> =
        StreamCodec.unit(StatusRequestPayload)
}
