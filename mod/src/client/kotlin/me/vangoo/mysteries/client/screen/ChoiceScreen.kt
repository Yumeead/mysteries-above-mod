package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.ChoiceAnswerPayload
import me.vangoo.mysteries.net.payload.ChoiceOfferPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * A pick an ability is waiting on: the options the server sent, as buttons, and — for Thunder
 * Voice — a line of text above them.
 *
 * The screen knows nothing about what the options mean; it answers with an index and the server
 * checks it against its own list. Any way out without a pick (Esc, another screen taking over)
 * answers [ChoiceAnswerPayload.CANCEL], so the server forgets the offer and the cast stays free.
 * The game does not pause: the choice is made standing in the world.
 */
class ChoiceScreen(private val offer: ChoiceOfferPayload) : Screen(offer.title) {

    private var answered = false
    private var text: EditBox? = null

    override fun init() {
        // ponytail: columns of fixed-width buttons, a scrolling list once a server has more players than fit.
        val rowsFit = ((height - TOP - MARGIN) / STEP - (if (offer.withText) 1 else 0)).coerceAtLeast(1)
        val columns = ((offer.options.size + rowsFit - 1) / rowsFit).coerceAtLeast(1)
        val rows = (offer.options.size + columns - 1) / columns
        val left = (width - columns * (BUTTON_WIDTH + GAP) + GAP) / 2
        var top = TOP

        if (offer.withText) {
            val box = EditBox(font, (width - TEXT_WIDTH) / 2, top, TEXT_WIDTH, BUTTON, Component.translatable("choice.$MOD_ID.text"))
            box.setMaxLength(ChoiceAnswerPayload.MAX_TEXT)
            box.setHint(Component.translatable("choice.$MOD_ID.text_hint"))
            text?.let { box.value = it.value }
            addRenderableWidget(box)
            setInitialFocus(box)
            text = box
            top += STEP
        }

        offer.options.forEachIndexed { index, option ->
            val x = left + (index / rows) * (BUTTON_WIDTH + GAP)
            val y = top + (index % rows) * STEP
            addRenderableWidget(Button.builder(option) { pick(index) }.bounds(x, y, BUTTON_WIDTH, BUTTON).build())
        }
    }

    private fun pick(index: Int) {
        answer(index)
        onClose()
    }

    private fun answer(index: Int) {
        if (answered) return
        answered = true
        ModChannels.CHOICE_ANSWER.sendToServer(ChoiceAnswerPayload(offer.id, index, text?.value.orEmpty()))
    }

    override fun removed() {
        answer(ChoiceAnswerPayload.CANCEL)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, TOP - 16, UiTheme.argb(UiTheme.ACCENT, ClientOptions.opacity))
    }

    private companion object {
        const val TOP = 48
        const val MARGIN = 16
        const val BUTTON = 20
        const val STEP = 24
        const val GAP = 6
        const val BUTTON_WIDTH = 200
        const val TEXT_WIDTH = 260
    }
}
