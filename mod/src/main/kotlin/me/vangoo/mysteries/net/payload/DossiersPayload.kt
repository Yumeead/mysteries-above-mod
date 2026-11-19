package me.vangoo.mysteries.net.payload

import io.netty.handler.codec.DecoderException
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.justiciar.Dossiers.Dossier
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.component.ResolvableProfile

/**
 * Server → client: the Sheriff's files (Recognition), whole, newest first — sent on joining and whenever one is added,
 * dies or goes out of reach. The client draws the remembered ones' footprints and the book's «Справи» from it.
 */
data class DossiersPayload(val entries: List<Entry>) : CustomPacketPayload {

    /** One file, and for a player the face they truly have. */
    data class Entry(val file: Dossier, val face: ResolvableProfile?)

    override fun type(): CustomPacketPayload.Type<DossiersPayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<DossiersPayload> = CustomPacketPayload.Type(modId("dossiers"))

        /** More than any Sequence keeps in mind; past it the list is foreign input. */
        private const val MAX_FILES = 64
        private const val MAX_TEXT = 256

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DossiersPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    buf.writeVarInt(payload.entries.size)
                    for ((file, face) in payload.entries) {
                        UUIDUtil.STREAM_CODEC.encode(buf, file.id)
                        buf.writeUtf(file.name, MAX_TEXT)
                        buf.writeUtf(file.kind, MAX_TEXT)
                        buf.writeBoolean(file.player)
                        buf.writeBoolean(file.beyonder)
                        buf.writeLong(file.rememberedAt)
                        BlockPos.STREAM_CODEC.encode(buf, file.rememberedPos)
                        buf.writeUtf(file.rememberedIn, MAX_TEXT)
                        buf.writeLong(file.seenAt)
                        BlockPos.STREAM_CODEC.encode(buf, file.seenPos)
                        buf.writeUtf(file.seenIn, MAX_TEXT)
                        buf.writeBoolean(file.dead)
                        FACE.encode(buf, java.util.Optional.ofNullable(face))
                    }
                },
                StreamDecoder { buf ->
                    val count = buf.readVarInt()
                    if (count !in 0..MAX_FILES) throw DecoderException("Забагато справ: $count")
                    DossiersPayload(List(count) {
                        val file = Dossier(
                            UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readBoolean(),
                            buf.readLong(), BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(MAX_TEXT),
                            buf.readLong(), BlockPos.STREAM_CODEC.decode(buf), buf.readUtf(MAX_TEXT), buf.readBoolean(),
                        )
                        Entry(file, FACE.decode(buf).orElse(null))
                    })
                },
            )

        private val FACE = ByteBufCodecs.optional(ResolvableProfile.STREAM_CODEC)
    }
}
