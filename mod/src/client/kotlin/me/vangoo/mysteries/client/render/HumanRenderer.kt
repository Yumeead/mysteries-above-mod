package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.vangoo.mysteries.entity.HumanEntity
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.ArmorModelSet
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType

/**
 * Draws a person of the creature engine with the player model and a face picked by their id.
 *
 * `HumanoidMobRenderer` brings the held items, the head slot and the arm poses a mob with a weapon
 * needs; the armour layer is added the way vanilla adds it to a player. Vanilla's own avatar
 * renderer is not reusable here for the reason [MarionetteRenderer] spells out — its bound is a
 * client-only interface a shared-side entity cannot implement.
 *
 * ## Two arm widths, one renderer
 *
 * Vanilla bakes a player renderer per arm width and picks it per skin. Here the skin is a function
 * of the entity's id and half the default skins are slim, so the model is swapped right before each
 * body is submitted — the layers ask the parent for its model at that same moment, and get the
 * matching one.
 */
class HumanRenderer(context: EntityRendererProvider.Context) :
    HumanoidMobRenderer<HumanEntity, AvatarRenderState, PlayerModel>(
        context,
        PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false),
        PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false),
        SHADOW,
    ) {

    private val wide: PlayerModel = model
    private val slim = PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true)

    init {
        addLayer(
            HumanoidArmorLayer(
                this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.modelSet) { HumanoidModel<AvatarRenderState>(it) },
                context.equipmentRenderer,
            ),
        )
    }

    override fun createRenderState(): AvatarRenderState = AvatarRenderState()

    override fun getTextureLocation(state: AvatarRenderState): Identifier = state.skin.body().texturePath()

    override fun extractRenderState(entity: HumanEntity, state: AvatarRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        state.id = entity.id
        state.skin = DefaultPlayerSkin.get(entity.uuid)
        state.isSpectator = false
        state.showHat = true
        state.showJacket = true
        state.showLeftPants = true
        state.showRightPants = true
        state.showLeftSleeve = true
        state.showRightSleeve = true
        state.showCape = false
    }

    override fun submit(state: AvatarRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        model = if (state.skin.model() == PlayerModelType.SLIM) slim else wide
        super.submit(state, poseStack, collector, camera)
    }

    private companion object {
        const val SHADOW = 0.5f
    }
}
