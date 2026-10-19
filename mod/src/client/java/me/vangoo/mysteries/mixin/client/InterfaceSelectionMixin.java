package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.InventorySkin;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractSelectionList.class)
abstract class InterfaceSelectionMixin {
    @ModifyVariable(method = "extractSelection", at = @At("HEAD"), argsOnly = true, require = 1)
    private int mysteries_above$selectionColor(int color) {
        return InventorySkin.selectionColor(color);
    }
}
