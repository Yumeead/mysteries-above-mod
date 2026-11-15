package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.MarionetteOrderPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * A line for a puppet to say: one text box, Enter speaks, Escape keeps quiet. The puppet says it in
 * chat under its own name — the wheel's "Speak as it". The game does not pause.
 */
class PuppetSpeechScreen(private val puppet: UUID, name: String) :
    Screen(Component.translatable("wheel.$MOD_ID.speech.title", name)) {

    private var line: EditBox? = null

    override fun init() {
        val box = EditBox(font, (width - WIDTH) / 2, height / 2 - HEIGHT / 2, WIDTH, HEIGHT, title)
        box.setMaxLength(MarionetteOrderPayload.MAX_SAY)
        line?.let { box.value = it.value }
        addRenderableWidget(box)
        setInitialFocus(box)
        line = box
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isConfirmation) {
            val said = line?.value?.trim().orEmpty()
            if (said.isNotEmpty()) {
                ModChannels.MARIONETTE_ORDER.sendToServer(MarionetteOrderPayload(MarionetteOrderPayload.Action.SPEAK, puppet = puppet, say = said))
            }
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, height / 2 - HEIGHT / 2 - 14, UiTheme.argb(UiTheme.ACCENT, ClientOptions.opacity))
    }

    private companion object {
        const val WIDTH = 260
        const val HEIGHT = 20
    }
}
