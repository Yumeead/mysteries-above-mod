package me.vangoo.mysteries.net

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * Пакет разом зі своїм напрямком.
 *
 * Напрямок тут — частина типу, а не домовленість у голові. Надіслати клієнтський
 * пакет із сервера неможливо: `sendTo(player)` є лише на [ClientboundChannel], а
 * `sendToServer()` — лише на [ServerboundChannel] і лише в клієнтському source set.
 * Це той самий прийом, що й межа `core`: інваріант стереже компілятор.
 *
 * Канали не створюються тут вручну — усі вони оголошені в [ModChannels].
 */
sealed class Channel<T : CustomPacketPayload>(
    val type: CustomPacketPayload.Type<T>,
    internal val codec: StreamCodec<RegistryFriendlyByteBuf, T>,
) {

    val id: Identifier get() = type.id()

    /** Реєструє тип у ванільному реєстрі. Викликає лише [ModChannels]. */
    internal abstract fun registerType()

    override fun toString(): String = "${this::class.simpleName}($id)"
}

/** Сервер → клієнт. */
class ClientboundChannel<T : CustomPacketPayload> internal constructor(
    type: CustomPacketPayload.Type<T>,
    codec: StreamCodec<RegistryFriendlyByteBuf, T>,
) : Channel<T>(type, codec) {

    override fun registerType() {
        PayloadTypeRegistry.clientboundPlay().register(type, codec)
    }

    fun sendTo(player: ServerPlayer, payload: T) {
        ServerPlayNetworking.send(player, payload)
    }

    /**
     * Мовчки пропускає тих, хто не вміє приймати цей канал: ванільний клієнт на
     * сервері — нормальна ситуація, а не помилка.
     */
    fun sendTo(players: Iterable<ServerPlayer>, payload: T) {
        players.forEach { if (ServerPlayNetworking.canSend(it, type)) sendTo(it, payload) }
    }
}

/** Клієнт → сервер. */
class ServerboundChannel<T : CustomPacketPayload> internal constructor(
    type: CustomPacketPayload.Type<T>,
    codec: StreamCodec<RegistryFriendlyByteBuf, T>,
) : Channel<T>(type, codec) {

    override fun registerType() {
        PayloadTypeRegistry.serverboundPlay().register(type, codec)
    }

    /**
     * Обробник виконується на головному потоці сервера — Fabric сам його туди
     * переносить, тож усередині можна чіпати світ і гравця напряму.
     */
    fun receiveOnServer(handler: (T, ServerPlayNetworking.Context) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context) }
    }
}
