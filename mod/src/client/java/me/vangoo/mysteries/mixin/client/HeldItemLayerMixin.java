package me.vangoo.mysteries.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.vangoo.mysteries.client.render.Lashes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints Psychic Lashing's lightning onto a charged weapon in hand, in the item's own coordinates, so it rides
 * the blade through every swing and bob. {@link HeldItemCoatMixin} says whether the layer about to be submitted
 * is such a weapon.
 *
 * <p>At {@code HEAD}, not inside the body: the layer's own pose is rebuilt here with the same private
 * {@code applyTransform} the body is about to call, drawn into, and popped again. Everything goes to the
 * collector either way, so being first changes nothing about what is drawn over what.
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
abstract class HeldItemLayerMixin {

    @Shadow
    private void applyTransform(PoseStack.Pose pose) {
        throw new AssertionError();
    }

    @Inject(method = "submit", at = @At("HEAD"), require = 1)
    private void mysteries_above$coat(PoseStack poses, SubmitNodeCollector collector, int light, int overlay, int outline,
                                      CallbackInfo ci) {
        if (!Lashes.INSTANCE.coating()) return;
        poses.pushPose();
        applyTransform(poses.last());
        Lashes.INSTANCE.coatHeld(poses, collector);
        poses.popPose();
    }
}
