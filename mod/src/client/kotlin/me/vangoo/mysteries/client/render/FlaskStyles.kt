package me.vangoo.mysteries.client.render

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import me.vangoo.domain.hud.ParticleFlow
import me.vangoo.domain.hud.LiquidPainting
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.slf4j.LoggerFactory

data class FlaskStyle(
    val frame: Identifier,
    val glass: Identifier,
    val backing: Identifier,
    val neck: Identifier,
    val tag: Identifier,
    val engraving: Identifier,
    val width: Int,
    val height: Int,
    val rows: List<List<IntRange>>,
    val tones: List<IntArray>,
    val palette: List<Int>,
    val particle: List<Pair<Int, Int>>,
    val particleCount: Int,
    val flow: ParticleFlow,
    val periodTicks: Int,
    val tagSwing: Float,
    val paintings: List<LiquidPainting> = emptyList(),
) {
    val top = rows.indexOfFirst { it.isNotEmpty() }
    val bottom = rows.indexOfLast { it.isNotEmpty() }
    fun contains(x: Int, y: Int) = rows.getOrNull(y)?.any { x in it } == true
}

object FlaskStyles {
    private val log = LoggerFactory.getLogger("mysteries_above/flasks")
    private var styles: Map<String, FlaskStyle> = emptyMap()

    fun register() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(modId("spirituality_styles"),
            ResourceManagerReloadListener(::reload))
    }

    fun of(pathway: String?): FlaskStyle? = styles[pathway] ?: styles["default"]

    private fun reload(manager: ResourceManager) {
        styles = buildMap {
            for ((id, resource) in manager.listResources("hud/spirituality") { it.path.endsWith(".json") }) {
                try {
                    val json = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
                    val frame = Identifier.parse(json.get("frame").asString)
                    val glass = Identifier.parse(json.get("glass").asString)
                    val backing = Identifier.parse(json.get("backing")?.asString ?: "mysteries_above:spirituality/common/backing")
                    val neck = Identifier.parse(json.get("neck").asString)
                    val tag = Identifier.parse(json.get("tag").asString)
                    val engraving = Identifier.parse(json.get("engraving").asString)
                    val palette = json.getAsJsonArray("palette").map { it.asString.removePrefix("#").toInt(16) }
                    require(palette.size in 4..6 && palette.all { it in 0..0xFFFFFF })
                    val flow = ParticleFlow.valueOf(json.get("flow").asString)
                    val periodTicks = json.get("periodTicks").asInt
                    val tagSwing = json.get("tagSwing").asFloat
                    require(periodTicks in 20..1200 && tagSwing.isFinite() && tagSwing in 0f..0.1f)
                    for (sprite in listOf(frame, glass, backing, neck, tag, engraving)) {
                        require(manager.getResource(Identifier.fromNamespaceAndPath(sprite.namespace,
                            "textures/gui/sprites/${sprite.path}.png")).isPresent) { "Missing sprite $sprite" }
                    }
                    val particle = json.getAsJsonArray("particle").map {
                        val pair = it.asJsonArray
                        require(pair.size() == 2)
                        pair[0].asInt to pair[1].asInt
                    }
                    require(particle.size in 1..16 && particle.all { it.first in -3..3 && it.second in -3..3 })
                    val count = json.get("particleCount").asInt
                    require(count in 0..16)
                    val mask = Identifier.parse(json.get("mask").asString)
                    manager.open(mask).use { stream -> NativeImage.read(stream).use { image ->
                        require(image.width in 8..128 && image.height in 16..192)
                        val rows = (0 until image.height).map { y ->
                            buildList {
                                var left = -1
                                for (x in 0..image.width) {
                                    val inside = x < image.width && image.getPixel(x, y) ushr 24 > 0
                                    if (inside && left == -1) left = x
                                    if (!inside && left != -1) { add(left until x); left = -1 }
                                }
                            }
                        }
                        require(rows.any { it.isNotEmpty() })
                        val tonesId = Identifier.parse(json.get("tones").asString)
                        val tones = manager.open(tonesId).use { pixels -> NativeImage.read(pixels).use { texture ->
                            require(texture.width == image.width && texture.height == image.height)
                            (0 until image.height).map { y -> IntArray(image.width) { x ->
                                (texture.getPixel(x, y) and 255).also { require(it in palette.indices) }
                            } }
                        } }
                        val paintings = json.getAsJsonArray("liquids")?.map { entry ->
                            val painting = entry.asJsonObject
                            val level = painting.get("level").asFloat
                            val surface = painting.get("surface").asInt
                            require(level.isFinite() && level > 0f && level <= 1f && surface in rows.indices)
                            val textureId = Identifier.parse(painting.get("texture").asString)
                            val pixels = manager.open(textureId).use { stream -> NativeImage.read(stream).use { texture ->
                                require(texture.width == image.width && texture.height == image.height)
                                (0 until texture.height).map { y -> IntArray(texture.width) { x -> texture.getPixel(x, y) } }
                            } }
                            require(pixels.any { row -> row.any { it ushr 24 > 0 } })
                            LiquidPainting(level, surface, pixels)
                        }.orEmpty()
                        require(paintings.zipWithNext().all { (a, b) -> a.level < b.level && a.surface >= b.surface })
                        put(json.get("pathway").asString, FlaskStyle(frame, glass, backing, neck, tag, engraving,
                            image.width, image.height, rows, tones, palette, particle, count, flow, periodTicks, tagSwing, paintings))
                    } }
                } catch (failure: Exception) {
                    log.warn("Ignoring invalid flask style {}: {}", id, failure.message)
                }
            }
        }
        if (styles["default"] == null) log.error("Default spirituality flask style is missing")
    }
}
