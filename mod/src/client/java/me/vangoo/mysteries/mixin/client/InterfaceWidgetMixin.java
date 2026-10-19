package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.InventorySkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
abstract class InterfaceWidgetMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void mysteries_above$beginWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventorySkin.beginWidget((AbstractWidget)(Object)this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private void mysteries_above$endWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventorySkin.endWidget((AbstractWidget)(Object)this, graphics);
    }
}
