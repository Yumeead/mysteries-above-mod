package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client → server: the Marionettist's hands on the threads.
 *
 * Deliberately **not** an `AbilityActionPayload`. An order has no cost, no cooldown, no identity
 * and never passes through `AbilityExecutor`: pushing it down that channel would mean inventing an
 * ability that does not exist. Possession is the opposite case and rightly went the other way —
 * it is a real ability with a real price, and it casts like any other.
 *
 * ## Why the server does the aiming
 *
 * [Action.ORDER] carries no target at all. While the Marionettist is standing in the world, their
 * own look vector and their own sneak are already server-side truth, so the server raycasts and
 * decides which of the four verbs this is: a block under the crosshair is *go there*, a creature
 * is *attack*, empty air is *follow me*, and sneaking is *stand still*. A client that named the
 * target would have to be checked against exactly that ray anyway.
 *
 * Possession sends nothing here: the driver walks as an ordinary player (see `Marionettes.possess`).
 */
data class MarionetteOrderPayload(
    val action: Action,
    val delta: Int = 0,
) : CustomPacketPayload {

    enum class Action {
        /** Step [delta] places along the row of threads — the mouse wheel. */
        CYCLE,

        /** Give the selected puppet an order; the server reads the crosshair and the sneak. */
        ORDER,

        /** Cut the selected thread. The puppet falls where it stands. */
        CUT,
    }

    override fun type(): CustomPacketPayload.Type<MarionetteOrderPayload> = TYPE

    companion object {

        fun cycle(delta: Int) = MarionetteOrderPayload(Action.CYCLE, delta = delta)

        val TYPE: CustomPacketPayload.Type<MarionetteOrderPayload> =
            CustomPacketPayload.Type(modId("marionette_order"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MarionetteOrderPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: MarionetteOrderPayload, buf: RegistryFriendlyByteBuf) {
            buf.writeVarInt(payload.action.ordinal)
            buf.writeVarInt(payload.delta)
        }

        private fun read(buf: RegistryFriendlyByteBuf): MarionetteOrderPayload =
            MarionetteOrderPayload(action(buf.readVarInt()), buf.readVarInt())

        private fun action(ordinal: Int): Action {
            val values = Action.entries
            require(ordinal in values.indices) { "Невідома дія з нитками: $ordinal" }
            return values[ordinal]
        }
    }
}
