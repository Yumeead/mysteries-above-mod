package me.vangoo.mysteries.net.payload

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack

/**
 * Сервер → клієнт: **рідкісний** знімок — те, що змінюється лише разом зі списком
 * здібностей.
 *
 * Летить на вхід у світ, зілля, піднесення, демоцію й `/mysteries forget` — тобто
 * одиниці разів за сесію. Саме тому описи, ціни й іконки їдуть тут, а не в
 * [AbilityStatePayload]: той ходить раз на секунду, і злиття двох означало б слати
 * повний текст опису кожної здібності щосекунди.
 *
 * `roster == null` — гравець не Потойбічний. Шлях, Послідовність і список або є разом,
 * або їх нема разом; формат не вміє виразити «список без шляху» — той самий прийом, що
 * вже в [BeyonderStatusPayload].
 */
data class AbilityRosterPayload(val roster: Roster?) : CustomPacketPayload {

    data class Roster(
        /** Ім'я шляху — ключ до `PathwayBranding` і заголовок меню. */
        val pathwayId: String,
        val sequence: Sequence,
        /** Канонічна назва Послідовності, напр. «Sailor». */
        val sequenceName: Component,
        val entries: List<Entry>,
    )

    /**
     * Здібність такою, якою її малює меню.
     *
     * Числа вже пораховані **під Послідовність власника**: клієнт не має ні
     * `SequenceScaler`, ні причин його мати.
     *
     * Порівнювати `Entry` рівністю не варто: [ItemStack] не має структурного `equals`,
     * тож згенерований тут `equals` порівняє іконки за посиланням. Тести звіряють поля
     * окремо, а іконку — через `ItemStack.matches`.
     */
    data class Entry(
        val identity: String,
        val name: Component,
        val description: Component,
        val type: AbilityType,
        /** Разова ціна касту. */
        val cost: Int,
        /** Ціна утримання за секунду; 0 — не утримується. */
        val upkeep: Int,
        /** Повна тривалість перепочинку — з неї клієнт рахує пропорцію заливки. */
        val cooldownSeconds: Int,
        val icon: ItemStack,
    )

    override fun type(): CustomPacketPayload.Type<AbilityRosterPayload> = TYPE

    companion object {

        /** Порожній ростер: гравець більше не Потойбічний. */
        val NONE: AbilityRosterPayload = AbilityRosterPayload(null)

        val TYPE: CustomPacketPayload.Type<AbilityRosterPayload> =
            CustomPacketPayload.Type(modId("ability_roster"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AbilityRosterPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: AbilityRosterPayload, buf: RegistryFriendlyByteBuf) {
            val roster = payload.roster
            buf.writeBoolean(roster != null)
            if (roster == null) return

            buf.writeUtf(roster.pathwayId)
            buf.writeVarInt(roster.sequence.level)
            ComponentSerialization.STREAM_CODEC.encode(buf, roster.sequenceName)

            buf.writeVarInt(roster.entries.size)
            for (entry in roster.entries) {
                buf.writeUtf(entry.identity)
                ComponentSerialization.STREAM_CODEC.encode(buf, entry.name)
                ComponentSerialization.STREAM_CODEC.encode(buf, entry.description)
                buf.writeVarInt(entry.type.ordinal)
                buf.writeVarInt(entry.cost)
                buf.writeVarInt(entry.upkeep)
                buf.writeVarInt(entry.cooldownSeconds)
                ItemStack.STREAM_CODEC.encode(buf, entry.icon)
            }
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityRosterPayload {
            if (!buf.readBoolean()) return NONE

            val pathwayId = buf.readUtf()
            val sequence = Sequence(buf.readVarInt())
            val sequenceName = ComponentSerialization.STREAM_CODEC.decode(buf)

            val entries = List(buf.readVarInt()) {
                Entry(
                    identity = buf.readUtf(),
                    name = ComponentSerialization.STREAM_CODEC.decode(buf),
                    description = ComponentSerialization.STREAM_CODEC.decode(buf),
                    type = abilityType(buf.readVarInt()),
                    cost = buf.readVarInt(),
                    upkeep = buf.readVarInt(),
                    cooldownSeconds = buf.readVarInt(),
                    icon = ItemStack.STREAM_CODEC.decode(buf),
                )
            }
            return AbilityRosterPayload(Roster(pathwayId, sequence, sequenceName, entries))
        }

        /** Тип їде порядковим номером — обидві сторони з одного джара; чуже число ловиться тут. */
        private fun abilityType(ordinal: Int): AbilityType {
            val values = AbilityType.entries
            require(ordinal in values.indices) { "Невідомий тип здібності: $ordinal" }
            return values[ordinal]
        }
    }
}
