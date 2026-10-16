package me.vangoo.mysteries.ability

import me.vangoo.mysteries.ability.runtime.PendingChoices
import me.vangoo.mysteries.net.payload.ChoiceAnswerPayload
import me.vangoo.mysteries.net.payload.ChoiceOfferPayload
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class PendingChoicesTest {

    private val player = UUID.randomUUID()
    private val sent = mutableListOf<ChoiceOfferPayload>()
    private val choices = PendingChoices { _, offer -> sent += offer }
    private val picks = mutableListOf<Pair<Int, String>>()

    private fun offer() = choices.offer(player, Component.literal("?"), listOf(Component.literal("a"), Component.literal("b")), withText = true) { i, text ->
        picks += i to text
    }

    @Test
    @DisplayName("Відповідь на чинну пропозицію спрацьовує рівно раз")
    fun answersOnce() {
        offer()
        val id = sent.single().id
        choices.answer(player, ChoiceAnswerPayload(id, 1, "  гей  "))
        choices.answer(player, ChoiceAnswerPayload(id, 0))
        assertEquals(listOf(1 to "гей"), picks)
    }

    @Test
    @DisplayName("Запізніла відповідь на замінену пропозицію не з'їдає нову")
    fun staleIdIsIgnored() {
        offer()
        offer()
        choices.answer(player, ChoiceAnswerPayload(sent[0].id, ChoiceAnswerPayload.CANCEL))
        choices.answer(player, ChoiceAnswerPayload(sent[1].id, 0))
        assertEquals(listOf(0 to ""), picks)
    }

    @Test
    @DisplayName("Скасування й індекс поза межами знімають пропозицію без вибору")
    fun cancelAndOutOfRangeDrop() {
        offer()
        choices.answer(player, ChoiceAnswerPayload(sent.last().id, ChoiceAnswerPayload.CANCEL))
        choices.answer(player, ChoiceAnswerPayload(sent.last().id, 0))
        offer()
        choices.answer(player, ChoiceAnswerPayload(sent.last().id, 7))
        choices.answer(player, ChoiceAnswerPayload(sent.last().id, 0))
        assertTrue(picks.isEmpty())
    }
}
