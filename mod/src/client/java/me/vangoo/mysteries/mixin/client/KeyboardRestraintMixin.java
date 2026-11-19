package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.input.RestraintInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A bound player's own client refuses to move them: right after the keyboard is read, {@link RestraintInput} sets what
 * it asked for to nothing. Moving on the client, not correcting on the server, is what keeps it from rubber-banding.
 *
 * <p>The Fabric API has no hook between reading the keys and moving the player.
 */
@Mixin(KeyboardInput.class)
abstract class KeyboardRestraintMixin {

    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void mysteries_above$restrain(CallbackInfo ci) {
        RestraintInput.apply((ClientInput) (Object) this);
    }
}
