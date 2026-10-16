package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server → client: an ability paused mid-cast and asks the caster to pick one of [options].
 *
 * The client opens a screen and nothing else — what the options mean stays on the server, which
 * resolves [ChoiceAnswerPayload.index] against its own list. [id] tells a late answer to an
 * offer that has since been replaced apart from an answer to the current one.
 *
 * [withText] adds a line of free text to the screen (Thunder Voice's message), sent back with
 * whichever option is clicked.
 */
data class ChoiceOfferPayload(
    val id: Int,
    val title: Component,
    val options: List<Component>,
    val withText: Boolean,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ChoiceOfferPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ChoiceOfferPayload> = CustomPacketPayload.Type(modId("choice_offer"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ChoiceOfferPayload> = CustomPacketPayload.codec(
            StreamMemberEncoder { payload, buf ->
                buf.writeVarInt(payload.id)
                ComponentSerialization.STREAM_CODEC.encode(buf, payload.title)
                buf.writeVarInt(payload.options.size)
                payload.options.forEach { ComponentSerialization.STREAM_CODEC.encode(buf, it) }
                buf.writeBoolean(payload.withText)
            },
            StreamDecoder { buf ->
                ChoiceOfferPayload(
                    id = buf.readVarInt(),
                    title = ComponentSerialization.STREAM_CODEC.decode(buf),
                    options = List(buf.readVarInt()) { ComponentSerialization.STREAM_CODEC.decode(buf) },
                    withText = buf.readBoolean(),
                )
            },
        )
    }
}

/**
 * Client → server: the answer to [ChoiceOfferPayload] number [id]. [index] is foreign input and
 * is checked against the server's own list; [CANCEL] means the screen was closed without a pick.
 */
data class ChoiceAnswerPayload(val id: Int, val index: Int, val text: String = "") : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ChoiceAnswerPayload> = TYPE

    companion object {
        const val CANCEL = -1

        /** The cap on [text], checked on read: the server never holds more than this from a client. */
        const val MAX_TEXT = 256

        val TYPE: CustomPacketPayload.Type<ChoiceAnswerPayload> = CustomPacketPayload.Type(modId("choice_answer"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ChoiceAnswerPayload> = CustomPacketPayload.codec(
            StreamMemberEncoder { payload, buf ->
                buf.writeVarInt(payload.id)
                buf.writeVarInt(payload.index)
                buf.writeUtf(payload.text, MAX_TEXT)
            },
            StreamDecoder { buf -> ChoiceAnswerPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_TEXT)) },
        )
    }
}
