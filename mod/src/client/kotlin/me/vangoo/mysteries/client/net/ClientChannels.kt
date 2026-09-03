package me.vangoo.mysteries.client.net

import me.vangoo.mysteries.net.ClientboundChannel
import me.vangoo.mysteries.net.ServerboundChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * The client half of the packet layer.
 *
 * It lives in `src/client` on purpose: `ClientPlayNetworking` exists only on the client, and an
 * attempt to send something "from the server to the server" simply will not compile here in
 * shared code — the import will not be there.
 */

/** Client → server. Silently does nothing outside a game: the game may already have disconnected. */
fun <T : CustomPacketPayload> ServerboundChannel<T>.sendToServer(payload: T) {
    if (!ClientPlayNetworking.canSend(type)) return
    ClientPlayNetworking.send(payload)
}

/**
 * The handler runs on the client's main thread — Fabric moves it there itself, so screens and
 * the world can be touched directly inside.
 */
fun <T : CustomPacketPayload> ClientboundChannel<T>.receiveOnClient(
    handler: (T, ClientPlayNetworking.Context) -> Unit,
) {
    ClientPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context) }
}
