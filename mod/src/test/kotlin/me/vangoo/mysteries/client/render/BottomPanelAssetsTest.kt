package me.vangoo.mysteries.client.render

import com.google.gson.JsonParser
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.hud.LiquidPainting
import me.vangoo.domain.hud.LiquidPaintingBlend
import me.vangoo.domain.hud.FlaskLiquid
import me.vangoo.domain.hud.OrbMask
import me.vangoo.domain.hud.ParticleFlow
import me.vangoo.domain.hud.TagAttachment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO
import kotlin.math.abs

class BottomPanelAssetsTest {
    @Test fun glowPulsesSlowlyWithoutBlinkingAndStopsForReducedMotion() {
        val samples=(0..120).map { orbGlowPulse(it.toFloat(),false) }
        assertTrue(samples.all { it in .6f..1f })
        assertTrue(samples.max()-samples.min()>.35f,"Glow should visibly brighten and dim")
        assertTrue(samples.zipWithNext().all { (a,b) -> abs(a-b)<.02f },"Glow must change smoothly")
        for (time in listOf(0f,25f,90f,10000f)) assertEquals(1f,orbGlowPulse(time,true))
    }

    @Test fun orbFloatsGentlyAndStopsForReducedMotion() {
        val samples=(0..320).map { orbFloatOffset(it.toFloat(),false) }
        assertTrue(samples.all { abs(it)<=.6f })
        assertTrue(samples.max()-samples.min()>1.1f)
        assertTrue(samples.zipWithNext().all { (a,b) -> abs(a-b)<.03f })
        for (time in listOf(0f,25f,90f,10000f)) assertEquals(0f,orbFloatOffset(time,true))
    }

    @Test fun everyPathwayUsesTheFoolSizedWindowAndKeepsItsUnchangedArtworkAndLastDrop() {
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
            val mask=OrbMask
            val rows=List(full.pixels.size) { y ->
                val inside=(0 until full.pixels[y].size).filter { x -> mask.contains(x,y) }
                if (inside.isEmpty()) emptyList() else listOf(inside.first()..inside.last())
            }
            for (level in listOf(.005f,.1f,.33f,1f)) for (time in listOf(0f,13f,71f,200f)) {
                val painting=LiquidPaintingBlend.at(paintings,level,49)!!
                val points=FlaskLiquid.highlights(rows,painting,49,time,json.get("periodTicks").asInt,5,
                    ParticleFlow.valueOf(json.get("flow").asString))
                if (level==1f) assertFalse(points.isEmpty(),"$pathway has no liquid motion")
                for (point in points) {
                    assertTrue(mask.contains(point.x,point.y),"$pathway particle escaped the globe")
                    assertTrue(painting.color(point.x,point.y,49) ushr 24>0,"$pathway particle reached dry glass")
                }
            }
            assertEquals(49,mask.y+mask.size-1)
            assertEquals(17,mask.x);assertEquals(22,mask.y);assertEquals(28,mask.size)
            val pixels=rows.flatMapIndexed { y,spans -> spans.flatMap { span -> span.map { x -> x to y } } }
            assertEquals(616,pixels.size,"$pathway must use the entire Fool aperture")
            val transparent=pixels.count { (x,y) -> full.pixels[y][x] ushr 24==0 }
            assertEquals(if (pathway=="WhiteTower") 1 else 0,transparent,
                "$pathway must fill its base without shrinking the flask")
            val last=LiquidPaintingBlend.at(paintings,.000001f,49)!!
            assertTrue((mask.y until mask.y+mask.size).any { y -> (mask.x until mask.x+mask.size).any { x ->
                mask.contains(x,y) && last.color(x,y,49) ushr 24 > 0
            } },"$pathway lost its last drop")
            for (name in listOf("orb_frame_${mask.size}","orb_backing_${mask.size}","orb_glass_${mask.size}","orb_glow_${mask.size}"))
                assertNotNull(javaClass.getResource("/assets/mysteries_above/textures/gui/sprites/bottom_panel/$name.png"))
        }
    }

    @Test fun glowFadesOutsideTheRimAndLeavesTheLiquidClearAtEverySize() {
        val root="/assets/mysteries_above/textures/gui/sprites/bottom_panel/"
        for (size in 8..28 step 2) {
            val glow=ImageIO.read(javaClass.getResource(root+"orb_glow_$size.png"))
            assertEquals(size+20,glow.width);assertEquals(size+20,glow.height)
            val center=(glow.width-1)/2.0
            val alphas=mutableSetOf<Int>()
            for (y in 0 until glow.height) for (x in 0 until glow.width) {
                val alpha=glow.getRGB(x,y) ushr 24
                alphas.add(alpha)
                if (Math.hypot(x-center,y-center)<=size/2.0 || x==0 || y==0 || x==glow.width-1 || y==glow.height-1)
                    assertEquals(0,alpha,"$size glow covers liquid or reaches its rectangular edge at $x,$y")
            }
            assertTrue(alphas.size>8,"$size glow needs a soft falloff")
            assertTrue(alphas.max() in 160..225,"$size glow must be bright but translucent")
            val middle=glow.height/2
            assertTrue(glow.getRGB(3,middle) ushr 24 > glow.getRGB(1,middle) ushr 24)
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
