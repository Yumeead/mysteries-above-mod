package me.vangoo.mysteries.item

import com.mojang.serialization.JsonOps
import me.vangoo.mysteries.GameRegistries
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Формат вмісту пляшки. Гри не піднімає: кодек — чистий DFU, реєстри йому не потрібні.
 *
 * Перевіряється саме **збережений** вигляд, а не лише кільце туди-назад: цей JSON
 * лежатиме у світах гравців, і мовчазна зміна імен полів зіпсує кожну вже налиту пляшку.
 */
class PotionBrewTest {

    companion object {
        /** Лише останній тест: `resolvePathway` будує шляхи, а ті — здібності. */
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() {
            GameRegistries.bootstrap()
        }
    }

    @Test
    @DisplayName("Зілля зберігається іменем шляху й номером Послідовності")
    fun encodesPathwayByName() {
        val encoded = PotionBrew.CODEC
            .encodeStart(JsonOps.INSTANCE, PotionBrew("Tyrant", 9))
            .getOrThrow()

        assertEquals("""{"pathway":"Tyrant","sequence":9}""", encoded.toString())
    }

    @Test
    @DisplayName("Записане читається назад тим самим")
    fun roundTrips() {
        val brew = PotionBrew("Sun", 7)
        val encoded = PotionBrew.CODEC.encodeStart(JsonOps.INSTANCE, brew).getOrThrow()
        val decoded = PotionBrew.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()

        assertEquals(brew, decoded)
    }

    @Test
    @DisplayName("Послідовність поза 0..9 не приймається ні конструктором, ні кодеком")
    fun rejectsSequenceOutOfRange() {
        assertThrows<IllegalArgumentException> { PotionBrew("Tyrant", 10) }
        assertThrows<IllegalArgumentException> { PotionBrew("Tyrant", -1) }

        val stray = PotionBrew.CODEC.parse(
            JsonOps.INSTANCE,
            com.google.gson.JsonParser.parseString("""{"pathway":"Tyrant","sequence":42}"""),
        )
        assertTrue(stray.isError, "Посл. 42 мала б не пройти кодек")
    }

    @Test
    @DisplayName("Шлях, якого ще немає в моді, розпізнається як відсутній, а не падає")
    fun unknownPathwayResolvesToNull() {
        // Тринадцять шляхів-заглушок і решта фази 08: зілля може існувати раніше за шлях.
        assertNull(PotionBrew("Fool", 9).resolvePathway())
        assertNotNull(PotionBrew("Tyrant", 9).resolvePathway())
    }
}
