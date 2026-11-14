package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.sun.abilities.NightVision
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.EyesLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * The two suns of the Sun's Night Vision, burning in a player's eyes — drawn the way a spider's
 * eyes are: the whole model again with an emissive texture that is empty except for four pixels,
 * so they glow in the dark and nothing else changes.
 *
 * The render state carries the entity's id, and by it the layer asks the entity itself for
 * [NightVision.SUN_EYES]: no packet of the mod's own and no mixin into the extraction.
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
        if (player.getAttached(NightVision.SUN_EYES) != true) return
        super.submit(pose, collector, light, state, yRot, xRot)
    }

    override fun renderType(): RenderType = EYES

    private companion object {
        val EYES: RenderType = RenderTypes.eyes(modId("textures/entity/sun_eyes.png"))
    }
}
