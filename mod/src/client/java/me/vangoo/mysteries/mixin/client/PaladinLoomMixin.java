package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.Marks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Punishment: the Paladin grows taller in the marked one's eyes. The render state's scale is what the renderer scales
 * the body by, set from the entity during extraction; at {@code RETURN} it is set, and {@link Marks#loom} may enlarge
 * it on this one client. Fabric has no hook into the extraction; the body is not touched on the server or for anyone
 * else.
 */
@Mixin(LivingEntityRenderer.class)
abstract class PaladinLoomMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("RETURN"),
        require = 1
    )
    private void mysteries_above$loom(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        Marks.INSTANCE.loom(entity, state);
    }
}
