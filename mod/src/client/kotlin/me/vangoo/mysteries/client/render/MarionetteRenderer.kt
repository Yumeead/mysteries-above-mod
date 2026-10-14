package me.vangoo.mysteries.client.render

import com.mojang.authlib.GameProfile
import me.vangoo.mysteries.entity.MarionetteEntity
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin

/**
 * Draws a marionette with the player model and the face it stole.
 *
 * ## Why this is not `AvatarRenderer`
 *
 * Vanilla's own avatar renderer is generic over `<E extends Avatar & ClientAvatarEntity>`, and
 * the second half of that bound is a **client-only** interface. [MarionetteEntity] lives in the
 * shared source set — it has to, the server creates and steers it — so it cannot implement a
 * type that does not exist on a dedicated server. Extending `LivingEntityRenderer` with the same
 * model and the same render state costs one `extractRenderState` and keeps the client/server
 * boundary the mod is built on.
 *
 * ## The skin lookup is made once per face
 *
 * `SkinManager.createLookup` starts an async fetch and caches the result inside the supplier it
 * returns; building a new one every frame would fire a request per frame and still draw the
 * default skin. Vanilla's `PlayerInfo` makes exactly one and holds it, and this is the same thing
 * — keyed by profile id, because a marionette's face never changes but the entity may be redrawn
 * from scratch.
 *
 * ponytail: the wide arm model only. Vanilla bakes the renderer twice and picks by
 * `PlayerSkin.model()`; a slim-skinned victim's puppet is drawn with wide arms until that is worth
 * a second baked layer.
 */
class MarionetteRenderer(context: EntityRendererProvider.Context) :
    LivingEntityRenderer<MarionetteEntity, AvatarRenderState, PlayerModel>(
        context,
        PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false),
        SHADOW,
    ) {

    override fun createRenderState(): AvatarRenderState = AvatarRenderState()

    override fun getTextureLocation(state: AvatarRenderState): Identifier = state.skin.body().texturePath()

    override fun extractRenderState(entity: MarionetteEntity, state: AvatarRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)

        state.id = entity.id
        state.skin = skinOf(entity.profile.partialProfile())
        state.isSpectator = false
        // A borrowed body wears everything the original wore: a puppet missing its owner's jacket
        // would read as a different person at a glance, which is the one thing it must not do.
        state.showHat = true
        state.showJacket = true
        state.showLeftPants = true
        state.showRightPants = true
        state.showLeftSleeve = true
        state.showRightSleeve = true
        state.showCape = false
    }

    private fun skinOf(profile: GameProfile): PlayerSkin = PuppetSkins.of(profile)

    /** The puppet its own driver is standing inside is not drawn around their camera. */
    override fun shouldRender(entity: MarionetteEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean =
        !entity.hiddenForDriver && super.shouldRender(entity, frustum, x, y, z)

    private companion object {
        const val SHADOW = 0.5f
    }
}
