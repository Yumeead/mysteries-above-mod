package me.vangoo.mysteries.net.payload

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server → client: the **frequent** snapshot — everything that changes in a fight.
 *
 * It flies no more than once a second (see `AbilityStatePump`), so it must stay small: no text
 * at all, only numbers and keys.
 *
 * Sent only to Beyonders. That a player stopped being one is said by an empty
 * [AbilityRosterPayload], not by a state with zero spirituality.
 */
data class AbilityStatePayload(
    /** The anchor: the server tick at the moment of the snapshot. The client counts cooldowns down from it. */
    val serverTick: Int,
    val spirituality: Spirituality,
    /**
     * Sanity loss, 0..100. The client needs it not for the number's sake: it decides what the
     * player **hears and sees** — the whisper and the screen chain are put up by the client
     * itself, because a frame is drawn 60+ times a second while a packet flies once.
     */
    val sanityLoss: SanityLoss,
    /** The identity of the selected ability; `null` — nothing is selected. */
    val selected: String?,
    /** Only the abilities with something to say: the rest are ready, switched off and not running. */
    val live: List<Live>,
) : CustomPacketPayload {

    /**
     * The state of one ability.
     *
     * [readyAtTick] is the **absolute tick** the cooldown ends at, not the remainder. The
     * remainder would have to be sent every second or it would freeze; an absolute tick is sent
     * once, when the cooldown started, and the client counts down itself from [serverTick].
     * `0` — the ability is ready.
     */
    data class Live(
        val identity: String,
        val readyAtTick: Int,
        val toggled: Boolean,
        val sustaining: Boolean,
        /** The picked mode's id; `null` — the ability has no modes. */
        val mode: String? = null,
    )

    override fun type(): CustomPacketPayload.Type<AbilityStatePayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<AbilityStatePayload> =
            CustomPacketPayload.Type(modId("ability_state"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AbilityStatePayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: AbilityStatePayload, buf: RegistryFriendlyByteBuf) {
            buf.writeVarInt(payload.serverTick)
            buf.writeVarInt(payload.spirituality.current)
            buf.writeVarInt(payload.spirituality.maximum)
            buf.writeVarInt(payload.sanityLoss.scale)

            buf.writeBoolean(payload.selected != null)
            payload.selected?.let(buf::writeUtf)

            buf.writeVarInt(payload.live.size)
            for (live in payload.live) {
                buf.writeUtf(live.identity)
                buf.writeVarInt(live.readyAtTick)
                buf.writeBoolean(live.toggled)
                buf.writeBoolean(live.sustaining)
                buf.writeBoolean(live.mode != null)
                live.mode?.let(buf::writeUtf)
            }
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityStatePayload {
            val serverTick = buf.readVarInt()
            val current = buf.readVarInt()
            val maximum = buf.readVarInt()
            // `SanityLoss` checks the 0..100 bounds itself — a trust boundary, not plain arithmetic.
            val sanityLoss = SanityLoss.of(buf.readVarInt())
            val selected = if (buf.readBoolean()) buf.readUtf() else null
            val live = List(buf.readVarInt()) {
                Live(
                    identity = buf.readUtf(),
                    readyAtTick = buf.readVarInt(),
                    toggled = buf.readBoolean(),
                    sustaining = buf.readBoolean(),
                    mode = if (buf.readBoolean()) buf.readUtf() else null,
                )
            }
            return AbilityStatePayload(serverTick, Spirituality(current, maximum), sanityLoss, selected, live)
        }
    }
}
