package me.vangoo.mysteries.client.render

import com.google.gson.JsonParser
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.hud.LiquidPainting
import me.vangoo.domain.hud.LiquidPaintingBlend
import me.vangoo.domain.hud.OrbMask
import me.vangoo.domain.hud.TagAttachment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

class BottomPanelAssetsTest {
    @Test fun everyPathwayFitsItsOwnUnchangedArtworkAndKeepsTheLastDrop() {
        for (pathway in PathwayBranding.names) {
            val json = javaClass.getResourceAsStream("/assets/mysteries_above/hud/spirituality/${pathway.lowercase()}.json")!!
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val paintings = json.getAsJsonArray("liquids").map { value ->
                val entry=value.asJsonObject
                val id=entry.get("texture").asString.split(':',limit=2)
                val image=ImageIO.read(javaClass.getResource("/assets/${id[0]}/${id[1]}"))
                LiquidPainting(entry.get("level").asFloat,entry.get("surface").asInt,
                    List(image.height) { y -> IntArray(image.width) { x -> image.getRGB(x,y) } })
            }
            val full=paintings.maxBy { it.level }
            val tagId=json.get("tag").asString.split(':',limit=2)
            val tag=ImageIO.read(javaClass.getResource("/assets/${tagId[0]}/textures/gui/sprites/${tagId[1]}.png"))
            val attachment=TagAttachment.find(tag.width,tag.height) { x,y -> tag.getRGB(x,y) ushr 24>0 }
            assertTrue(attachment.y<17,"$pathway pivots inside the ornament")
            val mask=OrbMask.fit(full.pixels)
            assertEquals(49,mask.y+mask.size-1)
            if (pathway in listOf("Fool","Mother","Justiciar")) assertEquals(OrbMask(17,22,28),mask)
            for (y in mask.y until mask.y+mask.size) for (x in mask.x until mask.x+mask.size)
                if (mask.contains(x,y)) assertEquals(255,full.pixels[y][x] ushr 24,"$pathway gap at $x,$y")
            val last=LiquidPaintingBlend.at(paintings,.000001f,49)!!
            assertTrue((mask.y until mask.y+mask.size).any { y -> (mask.x until mask.x+mask.size).any { x ->
                mask.contains(x,y) && last.color(x,y,49) ushr 24 > 0
            } },"$pathway lost its last drop")
            for (name in listOf("orb_frame_${mask.size}","orb_backing_${mask.size}","orb_glass_${mask.size}"))
                assertNotNull(javaClass.getResource("/assets/mysteries_above/textures/gui/sprites/bottom_panel/$name.png"))
        }
    }

    @Test fun glassHasNoWhiteTopLipAndEveryResourceRemainsAtItsNativeSize() {
        val root="/assets/mysteries_above/textures/gui/sprites/bottom_panel/"
        for (name in listOf("heart_empty","heart_full","heart_half_height","heart_half_left","food_empty","food_full","food_half")) {
            val image=ImageIO.read(javaClass.getResource(root+name+".png"))
            assertEquals(9,image.width);assertEquals(9,image.height)
        }
        val glass=ImageIO.read(javaClass.getResource(root+"orb_glass_28.png"))
        for (x in 0 until 28) for (y in 0..1) assertEquals(0,glass.getRGB(x,y) ushr 24)
        val highlight=(0 until glass.height).flatMap { y -> (0 until glass.width).map { x -> glass.getRGB(x,y) ushr 24 } }
        assertTrue(highlight.count { it>0 } in 3..8)
        assertTrue(highlight.max()<128,"Empty glass must not have an opaque white glare")
        val frame=ImageIO.read(javaClass.getResource(root+"orb_frame_28.png"))
        assertEquals(36,frame.width);assertEquals(36,frame.height)
        assertEquals(0,frame.getRGB(18,18) ushr 24)
        val hotbar=ImageIO.read(javaClass.getResource(root+"hotbar.png"))
        assertEquals(182,hotbar.width);assertEquals(22,hotbar.height)
    }
}
