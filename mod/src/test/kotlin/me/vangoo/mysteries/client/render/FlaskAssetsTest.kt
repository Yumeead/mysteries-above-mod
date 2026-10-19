package me.vangoo.mysteries.client.render

import com.google.gson.JsonParser
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.hud.LiquidPainting
import me.vangoo.domain.hud.LiquidPaintingBlend
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

class FlaskAssetsTest {
    @Test fun sharedGlassHasAnUnbrokenLightRimInsideTheDarkOutline() {
        val root = "/assets/mysteries_above/textures/gui/sprites/spirituality/common/"
        val frame = ImageIO.read(javaClass.getResource(root + "frame.png"))
        val mask = ImageIO.read(javaClass.getResource(root + "mask.png"))
        for (y in 18..49) {
            val inside = (0 until mask.width).filter { x -> mask.getRGB(x, y) ushr 24 > 0 }
            for (x in listOf(inside.first() - 1, inside.last() + 1)) {
                val pixel = frame.getRGB(x, y)
                assertEquals(255, pixel ushr 24, "Rim gap at $x,$y")
                assertTrue((pixel shr 16 and 255) >= 150 && (pixel and 255) >= 225, "Dark artifact at $x,$y")
            }
        }
    }

    @Test fun everyPathwayAndFallbackHaveReplaceableValidSprites() {
        for (pathway in PathwayBranding.names + "default") {
            val jsonUrl = javaClass.getResource("/assets/mysteries_above/hud/spirituality/${pathway.lowercase()}.json")
            assertNotNull(jsonUrl, pathway)
            val json = jsonUrl!!.openStream().bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            assertEquals(pathway, json.get("pathway").asString)
            assertEquals(6, json.getAsJsonArray("palette").size())
            assertEquals("mysteries_above:spirituality/common/frame", json.get("frame").asString)
            assertEquals("mysteries_above:spirituality/common/glass", json.get("glass").asString)
            assertTrue(json.get("periodTicks").asInt in 20..1200)
            assertTrue(json.get("tagSwing").asFloat in 0f..0.1f)
            for (layer in listOf("frame", "glass", "backing", "mask", "neck", "tag", "engraving", "tones")) {
                val id = json.get(layer).asString.split(':', limit = 2)
                val path = if (layer in listOf("mask", "tones")) id[1] else "textures/gui/sprites/${id[1]}.png"
                val url = javaClass.getResource("/assets/${id[0]}/$path")
                assertNotNull(url, "$pathway $layer")
                val image = ImageIO.read(url)
                assertEquals(64, image.width)
                assertEquals(56, image.height)
                assertTrue((0 until image.height).any { y -> (0 until image.width).any { x -> image.getRGB(x, y) ushr 24 > 0 } })
                if (layer != "backing")
                    assertTrue((52 until image.height).all { y -> (0 until image.width).all { x -> image.getRGB(x, y) ushr 24 == 0 } }, "$pathway $layer has pixels below the globe")
                if (layer == "backing") {
                    val alphas = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) ushr 24 } }.toSet()
                    assertEquals(setOf(0, 102, 128, 153), alphas)
                    for (x in listOf(0, image.width - 1)) for (y in 0 until image.height)
                        assertEquals(0, image.getRGB(x, y) ushr 24, "Shadow reaches rectangular sprite boundary")
                    assertEquals(0, image.getRGB(7, 13) ushr 24)
                    assertTrue(image.getRGB(31, 33) ushr 24 > 0)
                }
                if (layer == "mask") {
                    assertTrue((0 until image.height).all { y -> (0 until image.width).all { x -> image.getRGB(x, y) ushr 24 in listOf(0, 255) } })
                }
                if (layer == "tones") {
                    assertTrue((0 until image.height).all { y -> (0 until image.width).all { x ->
                        image.getRGB(x, y) and 255 in 0..5
                    } })
                }
            }
            assertTrue(json.getAsJsonArray("particle").size() in 1..16)
            if (pathway != "default") {
                val paintings = json.getAsJsonArray("liquids").map { entry ->
                    val painting = entry.asJsonObject
                    val id = painting.get("texture").asString.split(':', limit = 2)
                    val image = ImageIO.read(javaClass.getResource("/assets/${id[0]}/${id[1]}"))
                    assertEquals(64, image.width)
                    assertEquals(56, image.height)
                    val pixels = (0 until image.height).map { y -> IntArray(image.width) { x -> image.getRGB(x, y) } }
                    if (painting.get("level").asFloat == 1f)
                        assertTrue(pixels.flatMap { it.toList() }.filter { it ushr 24 > 0 }.toSet().size > 100,
                            "$pathway lost the original full-colour artwork")
                    LiquidPainting(painting.get("level").asFloat, painting.get("surface").asInt, pixels)
                }
                assertEquals(listOf(.005f, .1f, .33f, 1f), paintings.map { it.level })
                for (original in paintings) {
                    val shown = LiquidPaintingBlend.at(paintings, original.level, 49)!!
                    for (y in 0..55) for (x in 0..63)
                        assertEquals(original.pixels[y][x], shown.color(x, y, 49), "$pathway ${original.level} changed pixel $x,$y")
                }
                val minimum = LiquidPaintingBlend.at(paintings, .000001f, 49)!!
                assertTrue((13..48).any { minimum.color(it, 49, 49) ushr 24 > 0 }, "$pathway lost the final nonzero drop")
            }
        }
    }
}
