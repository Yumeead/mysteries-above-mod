package me.vangoo.mysteries.net.payload

import io.netty.handler.codec.DecoderException
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.component.ResolvableProfile

/**
 * Server → client: the Faceless's gallery — every face they have seen, newest first, each with how
 * long ago. The pick goes back as an ordinary `CHOICE_ANSWER` with [id]: the gallery is a choice like
 * any other, only its screen is its own.
 */
data class FaceGalleryPayload(val id: Int, val faces: List<Entry>) : CustomPacketPayload {

    /** One face: whose, as they looked, and how many seconds since it was last seen. */
    data class Entry(val face: ResolvableProfile, val secondsAgo: Long)

    override fun type(): CustomPacketPayload.Type<FaceGalleryPayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<FaceGalleryPayload> = CustomPacketPayload.Type(modId("face_gallery"))

        /** More than any server has players; past it the list is foreign input. */
        private const val MAX_FACES = 4096

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, FaceGalleryPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    buf.writeVarInt(payload.id)
                    buf.writeVarInt(payload.faces.size)
                    payload.faces.forEach { entry ->
                        ResolvableProfile.STREAM_CODEC.encode(buf, entry.face)
                        buf.writeVarLong(entry.secondsAgo)
                    }
                },
                StreamDecoder { buf ->
                    val id = buf.readVarInt()
                    val count = buf.readVarInt()
                    if (count !in 0..MAX_FACES) throw DecoderException("Забагато облич: $count")
                    FaceGalleryPayload(id, List(count) { Entry(ResolvableProfile.STREAM_CODEC.decode(buf), buf.readVarLong()) })
                },
            )
    }
}
