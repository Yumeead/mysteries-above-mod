package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.hud.BottomHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fabric keeps the vanilla HUD logic; this only substitutes sprites within its scoped draw. */
@Mixin(GuiGraphicsExtractor.class)
abstract class HudSpriteMixin {
    @ModifyVariable(method = {
        "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
        "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 2)
    private Identifier mysteries_above$hudSprite(Identifier sprite) {
        return BottomHud.sprite(sprite);
    }
}
