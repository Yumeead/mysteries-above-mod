package me.vangoo.mysteries.client.net

import me.vangoo.mysteries.net.ClientboundChannel
import me.vangoo.mysteries.net.ServerboundChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Клієнтська половина пакетного шару.
 *
 * Живе в `src/client` навмисно: `ClientPlayNetworking` існує лише на клієнті, і
 * спроба надіслати щось «із сервера на сервер» тут просто не скомпілюється у
 * спільному коді — імпорту не буде.
 */

/** Клієнт → сервер. Мовчки нічого не робить поза грою: гра могла вже від'єднатись. */
fun <T : CustomPacketPayload> ServerboundChannel<T>.sendToServer(payload: T) {
    if (!ClientPlayNetworking.canSend(type)) return
    ClientPlayNetworking.send(payload)
}

/**
 * Обробник виконується на головному потоці клієнта — Fabric сам його туди
 * переносить, тож усередині можна чіпати екрани й світ напряму.
 */
fun <T : CustomPacketPayload> ClientboundChannel<T>.receiveOnClient(
    handler: (T, ClientPlayNetworking.Context) -> Unit,
) {
    ClientPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context) }
}
