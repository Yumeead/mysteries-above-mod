package me.vangoo.mysteries.client.render

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.Vec3

/**
 * The Justiciar's lines of one frame: hairlines that are hot (light, [RenderTypes.lightning]) or cold (iron,
 * [RenderTypes.textBackground] lit by the place, so cold iron goes dark at night like any block).
 *
 * Collected first and drawn in two batches: the frame's geometry is written later, when the frame draws its
 * translucent parts, so each frame builds its own lists.
 */
internal class Strokes {

    private class Line(val a: Vec3, val b: Vec3, val argb: Int, val width: Double, val light: Int)

    private val hot = ArrayList<Line>()
    private val cold = ArrayList<Line>()

    fun hot(a: Vec3, b: Vec3, argb: Int, width: Double = PIXEL) {
        hot += Line(a, b, argb, width, 0)
    }

    fun cold(a: Vec3, b: Vec3, argb: Int, light: Int, width: Double = PIXEL) {
        cold += Line(a, b, argb, width, light)
    }

    val isEmpty: Boolean get() = hot.isEmpty() && cold.isEmpty()

    fun submit(context: LevelRenderContext) {
        val camera = context.levelState().cameraRenderState.pos
        if (hot.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning()) { pose, buffer ->
                for (line in hot) pixelLine(pose, buffer, camera, line.a, line.b, line.argb, line.width)
            }
        }
        if (cold.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
                for (line in cold) pixelLine(pose, buffer, camera, line.a, line.b, line.argb, line.width, line.light)
            }
        }
    }
}
