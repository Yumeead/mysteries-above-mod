package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.PuppetSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A possessing Marionettist wears the puppet's skin on their own screen — in F5 and on the
 * first-person arm alike.
 *
 * <p>Every player on the client draws through {@code getSkin()}, and there is no Fabric hook for
 * it. The injection sits at the method's {@code HEAD} and does one thing: ask
 * {@link PuppetSkins#worn} and return its answer if there is one. Nothing else changes, and for
 * every player who is not the local one inside a puppet it returns straight away.
 */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true, require = 1)
    private void mysteries_above$wornSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin worn = PuppetSkins.worn((AbstractClientPlayer) (Object) this);
        if (worn != null) cir.setReturnValue(worn);
    }
}
