package me.vangoo.mysteries.net.payload

import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клієнт: **частий** знімок — усе, що змінюється в бою.
 *
 * Летить не частіше разу на секунду (див. `AbilityStatePump`), тож мусить лишатись
 * дрібним: жодних текстів, лише числа й ключі.
 *
 * Надсилається тільки Потойбічним. Що гравець перестав ним бути, каже порожній
 * [AbilityRosterPayload], а не стан із нульовою духовністю.
 */
data class AbilityStatePayload(
    /** Якір: тік сервера на момент знімка. Від нього клієнт відлічує кулдауни сам. */
    val serverTick: Int,
    val spirituality: Spirituality,
    /** Ідентичність обраної здібності; `null` — не обрано нічого. */
    val selected: String?,
    /** Лише ті здібності, у кого є що сказати: решта готова, вимкнена й не триває. */
    val live: List<Live>,
) : CustomPacketPayload {

    /**
     * Стан однієї здібності.
     *
     * [readyAtTick] — **абсолютний тік** кінця перепочинку, а не залишок. Залишок
     * довелося б надсилати щосекунди, інакше він застигав би; абсолютний тік надсилається
     * один раз, коли перепочинок почався, і клієнт відлічує сам від [serverTick].
     * `0` — здібність готова.
     */
    data class Live(
        val identity: String,
        val readyAtTick: Int,
        val toggled: Boolean,
        val sustaining: Boolean,
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

            buf.writeBoolean(payload.selected != null)
            payload.selected?.let(buf::writeUtf)

            buf.writeVarInt(payload.live.size)
            for (live in payload.live) {
                buf.writeUtf(live.identity)
                buf.writeVarInt(live.readyAtTick)
                buf.writeBoolean(live.toggled)
                buf.writeBoolean(live.sustaining)
            }
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityStatePayload {
            val serverTick = buf.readVarInt()
            val current = buf.readVarInt()
            val maximum = buf.readVarInt()
            val selected = if (buf.readBoolean()) buf.readUtf() else null
            val live = List(buf.readVarInt()) {
                Live(
                    identity = buf.readUtf(),
                    readyAtTick = buf.readVarInt(),
                    toggled = buf.readBoolean(),
                    sustaining = buf.readBoolean(),
                )
            }
            return AbilityStatePayload(serverTick, Spirituality(current, maximum), selected, live)
        }
    }
}
