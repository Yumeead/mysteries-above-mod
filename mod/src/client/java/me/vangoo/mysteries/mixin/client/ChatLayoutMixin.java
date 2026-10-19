package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.hud.BottomHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** This shared geometry feeds both drawing and retained clickable text; hit targets move with the chat. */
@Mixin(ChatComponent.class)
abstract class ChatLayoutMixin {
    @ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int mysteries_above$chatBottom(int screenHeight) {
        return screenHeight - BottomHud.chatRise(screenHeight);
    }

    @Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true, require = 1)
    private void mysteries_above$chatViewport(CallbackInfoReturnable<Integer> cir) {
        Minecraft client = Minecraft.getInstance();
        int rise = BottomHud.chatRise(client.getWindow().getGuiScaledHeight());
        if (rise == 0) return;
        double scale = Math.max(.1, client.options.chatScale().get());
        int available = (int) ((client.getWindow().getGuiScaledHeight() - 44 - rise) / scale);
        cir.setReturnValue(Math.max(9, Math.min(cir.getReturnValue() - (int) Math.ceil(rise / scale), available)));
    }
}
