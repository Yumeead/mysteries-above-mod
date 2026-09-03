package me.vangoo.mysteries.net

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * A packet together with its direction.
 *
 * The direction here is part of the type, not an agreement in someone's head. Sending a
 * clientbound packet from the server is impossible: `sendTo(player)` exists only on
 * [ClientboundChannel], and `sendToServer()` only on [ServerboundChannel] and only in the
 * client source set. The same trick as the `core` boundary: the compiler guards the invariant.
 *
 * Channels are never created here by hand — they are all declared in [ModChannels].
 */
sealed class Channel<T : CustomPacketPayload>(
    val type: CustomPacketPayload.Type<T>,
    internal val codec: StreamCodec<RegistryFriendlyByteBuf, T>,
) {

    val id: Identifier get() = type.id()

    /** Registers the type in the vanilla registry. Only [ModChannels] calls this. */
    internal abstract fun registerType()

    override fun toString(): String = "${this::class.simpleName}($id)"
}

/** Server → client. */
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
     * Silently skips those who cannot receive this channel: a vanilla client on the server is a
     * normal situation, not an error.
     */
    fun sendTo(players: Iterable<ServerPlayer>, payload: T) {
        players.forEach { if (ServerPlayNetworking.canSend(it, type)) sendTo(it, payload) }
    }
}

/** Client → server. */
class ServerboundChannel<T : CustomPacketPayload> internal constructor(
    type: CustomPacketPayload.Type<T>,
    codec: StreamCodec<RegistryFriendlyByteBuf, T>,
) : Channel<T>(type, codec) {

    override fun registerType() {
        PayloadTypeRegistry.serverboundPlay().register(type, codec)
    }

    /**
     * The handler runs on the server's main thread — Fabric moves it there itself, so the world
     * and the player can be touched directly inside.
     */
    fun receiveOnServer(handler: (T, ServerPlayNetworking.Context) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context) }
    }
}
