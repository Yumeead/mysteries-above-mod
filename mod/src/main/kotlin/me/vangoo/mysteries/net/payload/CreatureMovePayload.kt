package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server → client: a creature began a technique that has an animation of its own.
 *
 * Only the name travels; the client starts the clock itself, on the entity's own ticks. Sent to whoever
 * tracks the caster, and for any creature — the client keeps it only for a body with a model.
 *
 * @property entity the caster's entity id
 * @property move the animation's name in the model, e.g. `cast_bolt`
 */
data class CreatureMovePayload(val entity: Int, val move: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CreatureMovePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<CreatureMovePayload> = CustomPacketPayload.Type(modId("creature_move"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CreatureMovePayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreatureMovePayload::entity,
            ByteBufCodecs.STRING_UTF8, CreatureMovePayload::move,
            ::CreatureMovePayload,
        )
    }
}
