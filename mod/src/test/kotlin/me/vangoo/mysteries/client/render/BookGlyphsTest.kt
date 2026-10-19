package me.vangoo.mysteries.client.render

import com.google.gson.JsonParser
import me.vangoo.domain.PathwayBranding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookGlyphsTest {
    @Test
    fun bookTranslationsCoverEveryPathwayWithoutMiddleDots() {
        val translations = listOf("uk_ua", "en_us").map { language ->
            val stream = javaClass.getResourceAsStream("/assets/mysteries_above/lang/$language.json")!!
            stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        }
        val bookKeys = translations.map { json -> json.keySet().filter { it.startsWith("book.") }.toSet() }
        assertEquals(bookKeys[0], bookKeys[1])
        for (json in translations) {
            for (pathway in PathwayBranding.names) {
                assertTrue(json.has("book.mysteries_above.pathway.${pathway.lowercase()}"), pathway)
            }
            for (key in bookKeys[0]) assertTrue('·' !in json[key].asString, key)
        }
    }

    @Test
    fun symbolsShareACenteredNinePixelGrid() {
        for ((name, rows) in BookGlyphs.patterns) {
            assertEquals(9, rows.size, name)
            assertTrue(rows.all { it.length == 9 && it.all { c -> c == '.' || c == '#' } }, name)
            val pixels = rows.flatMapIndexed { y, row -> row.indices.filter { row[it] == '#' }.map { x -> x to y } }
            assertTrue(pixels.isNotEmpty(), name)
            assertTrue(kotlin.math.abs(pixels.minOf { it.first } + pixels.maxOf { it.first } - 8) <= 1, name)
            assertTrue(kotlin.math.abs(pixels.minOf { it.second } + pixels.maxOf { it.second } - 8) <= 1, name)
        }
        assertEquals(BookGlyphs.patterns.size, BookGlyphs.patterns.values.distinct().size)
    }
}
