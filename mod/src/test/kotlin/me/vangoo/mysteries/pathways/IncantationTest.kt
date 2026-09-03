package me.vangoo.mysteries.pathways

import me.vangoo.mysteries.pathways.whitetower.abilities.Spellcasting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Parsing an incantation — the one place in phase 08 where the logic is not just a vanilla call.
 * What is checked is exactly what the stems were taken for: grammatical case.
 */
class IncantationTest {

    @Test
    fun `слова сили читаються у відмінках і серед іншого тексту`() {
        val spell = Spellcasting.Incantation.read("Хай промінь вогню вдарить далеко і сильно")

        // 60 (beam) + 30 (element) + 25 (strong) + 25 (far)
        assertEquals("Промінь · вогонь · сильно ×1 · далеко ×1 · 140 духовності", spell?.summary())
    }

    @Test
    fun `повторення підсилює, але не нескінченно`() {
        val spell = Spellcasting.Incantation.read("вибух сильно сильно сильно сильно сильно")

        // The cost grows up to three repetitions: 90 + 3 × 25.
        assertEquals("Вибух · сильно ×3 · 165 духовності", spell?.summary())
    }

    @Test
    fun `текст без форми заклинанням не є`() {
        assertNull(Spellcasting.Incantation.read("сильно, далеко й надовго, але без форми"))
    }
}
