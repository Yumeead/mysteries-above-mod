package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.EyesLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * The eyes of the Sun's Night Vision, and of the Justiciar's stare, burning in a player's face — drawn the way a spider's
 * eyes are: the whole model again with an emissive texture that is empty except for four pixels,
 * so they glow in the dark and nothing else changes.
 *
 * The render state carries the entity's id, and by it the layer asks the entity itself for
 * [Eyes.GLOW]: no packet of the mod's own and no mixin into the extraction.
 *
 * ponytail: the pixels sit where Steve's and Alex's eyes are (row 12, columns 9–10 and 13–14 of
 * the skin); a skin with its eyes elsewhere gets suns beside them. Reading the eyes off the skin
 * itself is a texture of our own per skin — not worth it until someone notices.
 */
class SunEyesLayer(parent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    EyesLayer<AvatarRenderState, PlayerModel>(parent) {

    override fun submit(
        pose: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (state.isInvisible) return
        val player = Minecraft.getInstance().level?.getEntity(state.id) ?: return
        glowing = player.getAttached(Eyes.GLOW) ?: return
        super.submit(pose, collector, light, state, yRot, xRot)
    }

    // The layer is drawn on the render thread, one body at a time: what it was told in `submit` is what it draws.
    private var glowing = EyeGlow.SUN

    override fun renderType(): RenderType = EYES.getValue(glowing)

    private companion object {
        val EYES: Map<EyeGlow, RenderType> = EyeGlow.entries.associateWith { RenderTypes.eyes(modId("textures/entity/${it.texture}.png")) }
    }
}
