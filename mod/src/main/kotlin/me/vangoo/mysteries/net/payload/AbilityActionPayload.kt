package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Клієнт → сервер: один намір гравця на всі способи його висловити.
 *
 * Узагальнення порожнього [CastRequestPayload], а не другий шлях поруч із ним:
 * `identity == null` означає «те, що вже обрано» — рівно сьогоднішній порожній пакет.
 * Коли новий шлях буде прогнаний руками, старий піде (віха M8).
 *
 * **Ідентичність тут — прохання, а не право.** Клієнт може надіслати будь-який рядок;
 * сервер однаково шукає його в `Beyonder.abilities` тим самим пошуком, яким шукав би
 * обране. Слот на клієнті — ярлик до ідентичності, і перевіряється вона як будь-яка інша.
 */
data class AbilityActionPayload(val action: Action, val identity: String?) : CustomPacketPayload {

    enum class Action {
        /** Покласти здібність «на руку». */
        SELECT,

        /** Кастувати обране. */
        CAST,

        /** Обрати й одразу кастувати — відпускання колеса. */
        SELECT_AND_CAST,

        /** Перемкнути тогл. */
        TOGGLE,
    }

    override fun type(): CustomPacketPayload.Type<AbilityActionPayload> = TYPE

    companion object {

        /** Клавіша касту без вибору: «кастуй те, що я тримаю». */
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
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityActionPayload {
            val action = action(buf.readVarInt())
            val identity = if (buf.readBoolean()) buf.readUtf() else null
            return AbilityActionPayload(action, identity)
        }

        /** Число з мережі — чуже: межі перевіряються тут, а не `entries[]`-падінням. */
        private fun action(ordinal: Int): Action {
            val values = Action.entries
            require(ordinal in values.indices) { "Невідома дія: $ordinal" }
            return values[ordinal]
        }
    }
}
