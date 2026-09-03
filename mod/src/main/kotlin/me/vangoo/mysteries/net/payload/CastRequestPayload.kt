package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client → server: "cast what I am holding".
 *
 * Empty on purpose. The ability identity is **not trusted** to the client: it knows neither the
 * player's ability list nor whether they still have them, and a packet with a foreign key
 * inside would have to be checked on the server with exactly the same search as an empty one.
 * The selection lives on the server (`AbilitySelection`), the key only pulls the trigger.
 *
 * When phase 11 brings the ability wheel, the selection will travel the same way — as a
 * separate selection packet, not as a field inside this one.
 */
class CastRequestPayload private constructor() : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CastRequestPayload> = TYPE

    companion object {

        val INSTANCE: CastRequestPayload = CastRequestPayload()

        val TYPE: CustomPacketPayload.Type<CastRequestPayload> =
            CustomPacketPayload.Type(modId("cast_request"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CastRequestPayload> =
            StreamCodec.unit(INSTANCE)
    }
}
