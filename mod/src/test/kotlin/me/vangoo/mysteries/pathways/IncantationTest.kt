package me.vangoo.mysteries.pathways

import me.vangoo.mysteries.pathways.whitetower.abilities.Spellcasting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Розбір замовляння — єдине місце фази 08, де логіка не зводиться до виклику ванільки.
 * Перевіряється саме те, заради чого корені й узято: відмінок.
 */
class IncantationTest {

    @Test
    fun `слова сили читаються у відмінках і серед іншого тексту`() {
        val spell = Spellcasting.Incantation.read("Хай промінь вогню вдарить далеко і сильно")

        // 60 (промінь) + 30 (стихія) + 25 (сильно) + 25 (далеко)
        assertEquals("Промінь · вогонь · сильно ×1 · далеко ×1 · 140 духовності", spell?.summary())
    }

    @Test
    fun `повторення підсилює, але не нескінченно`() {
        val spell = Spellcasting.Incantation.read("вибух сильно сильно сильно сильно сильно")

        // Ціна росте до трьох повторів: 90 + 3 × 25.
        assertEquals("Вибух · сильно ×3 · 165 духовності", spell?.summary())
    }

    @Test
    fun `текст без форми заклинанням не є`() {
        assertNull(Spellcasting.Incantation.read("сильно, далеко й надовго, але без форми"))
    }
}
