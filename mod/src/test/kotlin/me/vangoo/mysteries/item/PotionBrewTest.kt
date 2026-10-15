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
 * The format of a bottle's contents. No game is brought up: the codec is pure DFU and needs no
 * registries.
 *
 * What is checked is the **saved** shape, not merely a round trip: this JSON will lie in
 * players' worlds, and a silent change of field names would spoil every bottle already poured.
 */
class PotionBrewTest {

    companion object {
        /** Only the last test: `resolvePathway` builds pathways, and those build abilities. */
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
        // Thirteen stub pathways and the rest of phase 08: a potion may exist before its pathway.
        assertNull(PotionBrew("Darkness", 9).resolvePathway())
        assertNotNull(PotionBrew("Tyrant", 9).resolvePathway())
    }
}
