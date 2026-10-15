package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client → server: one player intent for every way of expressing it.
 *
 * `identity == null` means "whatever is already selected": the tapped cast key sends no identity,
 * and the server picks, as it did with the empty cast packet this one absorbed.
 *
 * **The identity here is a request, not a right.** The client may send any string; the server
 * looks it up in `Beyonder.abilities` with the same search it would use for the selected one.
 * A slot on the client is a shortcut to an identity, and it is verified like any other.
 */
data class AbilityActionPayload(
    val action: Action,
    val identity: String?,
    /** The mode to use (or, with [Action.MODE], only to pick); `null` — whatever is picked already. */
    val mode: String? = null,
) : CustomPacketPayload {

    enum class Action {
        /** Put the ability "in hand". */
        SELECT,

        /** Cast what is selected. */
        CAST,

        /** Select and cast at once — releasing the wheel. */
        SELECT_AND_CAST,

        /** Flip a toggle. */
        TOGGLE,

        /** Only pick [mode] for the ability — the book's mode rows. */
        MODE,
    }

    override fun type(): CustomPacketPayload.Type<AbilityActionPayload> = TYPE

    companion object {

        /** The cast key with no selection: "cast what I am holding". */
        fun castSelected(): AbilityActionPayload = AbilityActionPayload(Action.CAST, null)

        val TYPE: CustomPacketPayload.Type<AbilityActionPayload> =
            CustomPacketPayload.Type(modId("ability_action"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AbilityActionPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: AbilityActionPayload, buf: RegistryFriendlyByteBuf) {
            buf.writeVarInt(payload.action.ordinal)
            buf.writeBoolean(payload.identity != null)
            payload.identity?.let(buf::writeUtf)
            buf.writeBoolean(payload.mode != null)
            payload.mode?.let(buf::writeUtf)
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityActionPayload {
            val action = action(buf.readVarInt())
            val identity = if (buf.readBoolean()) buf.readUtf() else null
            val mode = if (buf.readBoolean()) buf.readUtf() else null
            return AbilityActionPayload(action, identity, mode)
        }

        /** A number off the network is foreign: the bounds are checked here, not by an `entries[]` crash. */
        private fun action(ordinal: Int): Action {
            val values = Action.entries
            require(ordinal in values.indices) { "Невідома дія: $ordinal" }
            return values[ordinal]
        }
    }
}
