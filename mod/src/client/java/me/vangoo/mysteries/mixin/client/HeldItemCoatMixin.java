package me.vangoo.mysteries.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.vangoo.mysteries.client.render.Lashes;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link Lashes} whose item, in which hand, the first-person hand renderer is about to draw, so that
 * {@link HeldItemLayerMixin} can paint Psychic Lashing's lightning onto that very item, in its own pose.
 *
 * <p>Guessing where the weapon is on screen does not work: the item in hand moves with the equip animation,
 * the swing and the view bobbing, so lightning placed by any fixed offset floats beside it.
 */
@Mixin(ItemInHandRenderer.class)
abstract class HeldItemCoatMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), require = 1)
    private void mysteries_above$holding(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                         SubmitNodeCollector collector, int light, CallbackInfo ci) {
        Lashes.INSTANCE.holding(entity, stack, context);
    }

    @Inject(method = "renderItem", at = @At("RETURN"), require = 1)
    private void mysteries_above$released(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                          SubmitNodeCollector collector, int light, CallbackInfo ci) {
        Lashes.INSTANCE.released();
    }
}
