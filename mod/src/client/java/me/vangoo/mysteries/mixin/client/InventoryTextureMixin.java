package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.InventorySkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Only identifiers change; native UV coordinates, text, items and input stay intact. */
@Mixin(GuiGraphicsExtractor.class)
abstract class InventoryTextureMixin {
    @ModifyVariable(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Identifier mysteries_above$inventoryTexture(Identifier texture) {
        return InventorySkin.texture(texture);
    }

    @ModifyVariable(method = {
        "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
        "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 2)
    private Identifier mysteries_above$inventorySprite(Identifier sprite) {
        return InventorySkin.texture(sprite);
    }
}
