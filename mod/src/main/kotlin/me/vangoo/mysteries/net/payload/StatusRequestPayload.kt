package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Клієнт → сервер: «перечитай мій стан». Тіла нема — гравця сервер бере з
 * контексту з'єднання, а не з пакета; інакше будь-хто просив би чужий стан.
 */
data object StatusRequestPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<StatusRequestPayload> = TYPE

    val TYPE: CustomPacketPayload.Type<StatusRequestPayload> =
        CustomPacketPayload.Type(modId("status_request"))

    val CODEC: StreamCodec<RegistryFriendlyByteBuf, StatusRequestPayload> =
        StreamCodec.unit(StatusRequestPayload)
}
