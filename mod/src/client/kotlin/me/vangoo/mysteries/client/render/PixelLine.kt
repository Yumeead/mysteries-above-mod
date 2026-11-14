package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3

/**
 * A hairline from [from] to [to], [width] blocks across (a pixel of a block by default), turned to the
 * eye — for [net.minecraft.client.renderer.rendertype.RenderTypes.lightning], whose vertices are a
 * position and a colour. Vertices are relative to [camera], the way a level submit wants them.
 *
 * Lightning culls its back faces, so the quad is wound towards the eye, which is the origin here.
 *
 * A [light] is for a type that wants a lightmap too — [net.minecraft.client.renderer.rendertype.RenderTypes.textBackground],
 * whose plain translucency draws a dark line dark, where lightning only ever adds light.
 */
internal fun pixelLine(
    pose: PoseStack.Pose,
    buffer: VertexConsumer,
    camera: Vec3,
    from: Vec3,
    to: Vec3,
    argb: Int,
    width: Double = PIXEL,
    light: Int? = null,
) {
    val a = from.subtract(camera)
    val b = to.subtract(camera)
    if (a.distanceToSqr(b) < 1e-8) return
    val side = b.subtract(a).cross(a.add(b)).let { if (it.lengthSqr() < 1e-12) Vec3(0.0, width / 2, 0.0) else it.normalize().scale(width / 2) }
    val p0 = a.subtract(side)
    val p1 = b.subtract(side)
    val p2 = b.add(side)
    val p3 = a.add(side)
    val facing = p1.subtract(p0).cross(p3.subtract(p0)).dot(p0) < 0
    vertex(pose, buffer, p0, argb, light)
    if (facing) {
        vertex(pose, buffer, p1, argb, light); vertex(pose, buffer, p2, argb, light); vertex(pose, buffer, p3, argb, light)
    } else {
        vertex(pose, buffer, p3, argb, light); vertex(pose, buffer, p2, argb, light); vertex(pose, buffer, p1, argb, light)
    }
}

private fun vertex(pose: PoseStack.Pose, buffer: VertexConsumer, at: Vec3, argb: Int, light: Int?) {
    val vertex = buffer.addVertex(pose, at.x.toFloat(), at.y.toFloat(), at.z.toFloat()).setColor(argb)
    if (light != null) vertex.setLight(light)
}

/** An ARGB of [rgb] at [alpha] (0 → 1). */
internal fun argbOf(rgb: Int, alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24 or (rgb and 0xFFFFFF)

internal const val PIXEL = 1.0 / 16
