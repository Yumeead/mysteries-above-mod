package me.vangoo.mysteries.mixin.client;

import me.vangoo.mysteries.client.render.PrivateSight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An outline only this client draws: what a Seer's sight picks out is theirs to see.
 *
 * <p>Vanilla {@code GLOWING} is a flag on the entity, synced to everyone who tracks it, so a power
 * that only its caster should see would outline its targets for the whole server. Every outline the
 * client draws asks {@code shouldEntityAppearGlowing} first, and Fabric has no hook for it. The
 * injection sits at {@code HEAD} and does one thing: ask {@link PrivateSight#glows} and answer yes if
 * it says so; otherwise vanilla decides as always.
 */
@Mixin(Minecraft.class)
abstract class PrivateGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 1)
    private void mysteries_above$privateGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PrivateSight.glows(entity)) cir.setReturnValue(true);
    }
}
