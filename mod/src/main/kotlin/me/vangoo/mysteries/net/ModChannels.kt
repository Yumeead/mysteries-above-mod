package me.vangoo.mysteries.net

import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import me.vangoo.mysteries.net.payload.CastRequestPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Єдине місце, де живуть пакети мода.
 *
 * Найпоширеніший клас багів у модах — сервер знає те, чого не знає клієнт, — росте
 * з ad-hoc повідомлень, розкиданих по фічах. Тут оголошення каналу і є його
 * реєстрацією: [clientbound]/[serverbound] одразу кладуть канал у [declared], а
 * [registerTypes] проходить по списку. Забути зареєструвати оголошений тут канал
 * неможливо; канал, зібраний повз цей об'єкт, впаде на першій же відправці —
 * ванільний реєстр не знає його типу.
 *
 * [registerTypes] викликається з головного entrypoint'у, тобто на обох сторонах.
 */
object ModChannels {

    private val declared = mutableListOf<Channel<*>>()
    private var registered = false

    /** Сервер віддає клієнту стан Потойбічного. */
    val BEYONDER_STATUS: ClientboundChannel<BeyonderStatusPayload> =
        clientbound(BeyonderStatusPayload.TYPE, BeyonderStatusPayload.CODEC)

    /** Клієнт просить сервер перечитати стан. */
    val STATUS_REQUEST: ServerboundChannel<StatusRequestPayload> =
        serverbound(StatusRequestPayload.TYPE, StatusRequestPayload.CODEC)

    /** Клавіша касту: клієнт натиснув спуск, здібність обирає сервер. */
    val CAST_REQUEST: ServerboundChannel<CastRequestPayload> =
        serverbound(CastRequestPayload.TYPE, CastRequestPayload.CODEC)

    /**
     * Рідкісний знімок: список здібностей, їхні описи, ціни й іконки.
     * Летить лише на зміну самого списку — одиниці разів за сесію.
     */
    val ABILITY_ROSTER: ClientboundChannel<AbilityRosterPayload> =
        clientbound(AbilityRosterPayload.TYPE, AbilityRosterPayload.CODEC)

    /** Частий дрібний знімок: духовність, перепочинки, тоглі. Не частіше разу на секунду. */
    val ABILITY_STATE: ClientboundChannel<AbilityStatePayload> =
        clientbound(AbilityStatePayload.TYPE, AbilityStatePayload.CODEC)

    /** Один намір гравця: обрати, кастувати, обрати й кастувати, перемкнути. */
    val ABILITY_ACTION: ServerboundChannel<AbilityActionPayload> =
        serverbound(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC)

    /** Усі оголошені канали — для логів і тестів. */
    val all: List<Channel<*>> get() = declared.toList()

    fun registerTypes() {
        check(!registered) { "Канали вже зареєстровані" }
        registered = true
        declared.forEach { it.registerType() }
    }

    private fun <T : CustomPacketPayload> clientbound(
        type: CustomPacketPayload.Type<T>,
        codec: StreamCodec<RegistryFriendlyByteBuf, T>,
    ): ClientboundChannel<T> = declare(ClientboundChannel(type, codec))

    private fun <T : CustomPacketPayload> serverbound(
        type: CustomPacketPayload.Type<T>,
        codec: StreamCodec<RegistryFriendlyByteBuf, T>,
    ): ServerboundChannel<T> = declare(ServerboundChannel(type, codec))

    /**
     * Однакові id ловляться тут, під час ініціалізації класу, а не пізніше в
     * ванільному реєстрі: копіпаст каналу — рівно та помилка, заради якої цей
     * об'єкт існує.
     */
    private fun <C : Channel<*>> declare(channel: C): C {
        require(declared.none { it.id == channel.id }) { "Канал ${channel.id} уже оголошено" }
        declared += channel
        return channel
    }
}
