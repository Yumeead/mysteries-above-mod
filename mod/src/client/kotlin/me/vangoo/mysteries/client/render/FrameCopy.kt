package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier

/**
 * A copy of one of the frame's own textures, taken mid-frame, that a shader samples by [id].
 *
 * A pass may not read the target it draws into, so a shader that wants the frame behind it — the
 * light shaft its depth, the air bullet its colour — reads a copy. The copy is resized with the
 * window; until the first [capture] it has no view, and binding it then would throw ([ready]).
 */
internal class FrameCopy(private val id: Identifier, private val label: String, private val source: () -> GpuTexture?) : AbstractTexture() {

    private var registered = false

    val ready: Boolean get() = textureView != null

    fun capture() {
        val from = source() ?: return
        val width = from.getWidth(0)
        val height = from.getHeight(0)
        val device = RenderSystem.getDevice()
        val copy = texture?.takeIf { !it.isClosed && it.getWidth(0) == width && it.getHeight(0) == height }
            ?: run {
                releaseTextures()
                device.createTexture(label, COPY_USAGE, from.format, width, height, 1, 1).also {
                    texture = it
                    textureView = device.createTextureView(it)
                }
            }
        device.createCommandEncoder().copyTextureToTexture(from, copy, 0, 0, 0, 0, 0, width, height)
        if (!registered) {
            Minecraft.getInstance().textureManager.register(id, this)
            registered = true
        }
    }

    private companion object {
        /** What vanilla gives its own targets: a copy is written by blitting into it, so it must be attachable. */
        const val COPY_USAGE = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
    }
}
