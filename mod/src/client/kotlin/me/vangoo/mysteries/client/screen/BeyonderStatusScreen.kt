package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Тестовий екран фази 02: доводить, що клієнтський каркас справді працює.
 *
 * Він одноразовий за задумом — справжні екрани приїдуть у фазі 11. Перевіряє тут
 * три речі, і саме ті, що ламаються найчастіше: клієнтський source set бачить
 * `net.minecraft.client`, сервер уміє штовхнути стан у відкритий екран, а екран
 * уміє попросити стан назад і оновитись на місці.
 */
class BeyonderStatusScreen(private var status: BeyonderStatusPayload) :
    Screen(Component.literal("Стан Потойбічного")) {

    private var responses = 1

    override fun init() {
        val buttonWidth = 120
        val centerX = width / 2 - buttonWidth / 2

        addRenderableWidget(
            Button.Builder(Component.literal("Оновити")) {
                ModChannels.STATUS_REQUEST.sendToServer(StatusRequestPayload)
            }.bounds(centerX, height / 2 + 24, buttonWidth, 20).build(),
        )

        addRenderableWidget(
            Button.Builder(Component.literal("Закрити")) { onClose() }
                .bounds(centerX, height / 2 + 48, buttonWidth, 20)
                .build(),
        )
    }

    /** Викликається з клієнтського приймача, коли сервер надіслав свіжий стан. */
    fun update(fresh: BeyonderStatusPayload) {
        status = fresh
        responses++
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        var y = height / 2 - 48
        graphics.centeredText(font, title, width / 2, y, TITLE_COLOR)
        y += LINE

        for (line in lines()) {
            y += LINE
            graphics.centeredText(font, Component.literal(line), width / 2, y, TEXT_COLOR)
        }
    }

    private fun lines(): List<String> {
        val beyonder = status.beyonder
            ?: return listOf("Ще не Потойбічний", "відповідей від сервера: $responses")

        return listOf(
            // Шлях поки що показується сирим ідентифікатором: людські назви й кольори
            // приїдуть із PathwayBranding у фазі 03.
            "Шлях: ${beyonder.pathwayId}",
            "Послідовність: ${beyonder.sequence.level}",
            "Дух: ${beyonder.spirituality}",
            "відповідей від сервера: $responses",
        )
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val LINE = 12
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_COLOR = 0xFFA0A0A0.toInt()
    }
}
