package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * Client → server: the Marionettist's hands on the threads.
 *
 * Deliberately **not** an `AbilityActionPayload`. An order has no cost, no cooldown, no identity
 * and never passes through `AbilityExecutor`: pushing it down that channel would mean inventing an
 * ability that does not exist. Possession is the opposite case and rightly went the other way —
 * it is a real ability with a real price, and it casts like any other.
 *
 * Two ways in. The threads key ([Action.CYCLE], [Action.ORDER]) is the quick one: the server reads
 * the Marionettist's own crosshair and sneak and picks the verb, so no target travels. The wheel's
 * threads tab names the [puppet] and the verb outright (the rest of the actions) and, for "use", the
 * [block] the crosshair held the moment the wheel opened. The server checks it against its own
 * world; a line to [say] is filtered there.
 */
data class MarionetteOrderPayload(
    val action: Action,
    val delta: Int = 0,
    val puppet: UUID? = null,
    val block: BlockPos? = null,
    val say: String = "",
) : CustomPacketPayload {

    enum class Action {
        /** Step [delta] places along the row of threads — the mouse wheel. */
        CYCLE,

        /** Give the selected puppet an order; the server reads the crosshair and the sneak. */
        ORDER,

        /** Cut the thread of [puppet] (or the selected one). The puppet falls where it stands. */
        CUT,

        /** The wheel's verbs, each for [puppet]. */
        COME,
        FOLLOW,
        STAND,
        GUARD,
        POSSESS,
        SWAP,

        /** Strike whoever the Marionettist strikes, from now on. */
        ATTACK,
        SPEAK,
        USE,

        /** Make [puppet] the one the threads key and Possession act on. */
        SELECT,
    }

    override fun type(): CustomPacketPayload.Type<MarionetteOrderPayload> = TYPE

    companion object {

        fun cycle(delta: Int) = MarionetteOrderPayload(Action.CYCLE, delta = delta)

        /** The longest line a puppet will say: vanilla's chat limit. */
        const val MAX_SAY = 256

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
            buf.writeNullable(payload.puppet) { b, id -> b.writeUUID(id) }
            buf.writeNullable(payload.block) { b, pos -> b.writeBlockPos(pos) }
            buf.writeUtf(payload.say, MAX_SAY)
        }

        private fun read(buf: RegistryFriendlyByteBuf): MarionetteOrderPayload =
            MarionetteOrderPayload(
                action = action(buf.readVarInt()),
                delta = buf.readVarInt(),
                puppet = buf.readNullable { it.readUUID() },
                block = buf.readNullable { it.readBlockPos() },
                say = buf.readUtf(MAX_SAY),
            )

        private fun action(ordinal: Int): Action {
            val values = Action.entries
            require(ordinal in values.indices) { "Невідома дія з нитками: $ordinal" }
            return values[ordinal]
        }
    }
}
