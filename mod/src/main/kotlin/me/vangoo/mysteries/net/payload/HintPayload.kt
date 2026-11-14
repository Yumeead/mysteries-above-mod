package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server → client: a power did not come, and why.
 *
 * The [fizzle] is always played; the [message] is drawn only if the player turned hints on. Both
 * travel together because they are one moment: a fizzle with no words is the default, words with
 * no fizzle would be the old action bar again.
 */
data class HintPayload(val fizzle: Fizzle, val message: Component) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<HintPayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<HintPayload> = CustomPacketPayload.Type(modId("hint"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HintPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    buf.writeEnum(payload.fizzle)
                    ComponentSerialization.STREAM_CODEC.encode(buf, payload.message)
                },
                StreamDecoder { buf ->
                    HintPayload(buf.readEnum(Fizzle::class.java), ComponentSerialization.STREAM_CODEC.decode(buf))
                },
            )
    }
}
