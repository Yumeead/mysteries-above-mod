package me.vangoo.mysteries.mixin;

import me.vangoo.mysteries.event.GameEventBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mod's third mixin: a creature acquiring a target.
 *
 * Same discipline as {@link LivingEntityDamageMixin} — a method boundary, {@code require = 1},
 * and zero logic in the body: one call into our own bus, so a break on a new game version costs
 * five lines of Java rather than a rewrite of the handlers.
 *
 * The Fabric API has no aggro hook at all, which is why this is here and not one step earlier
 * in the search order.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {

    @Inject(
            method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void mysteries$beforeTarget(LivingEntity target, CallbackInfo ci) {
        if (GameEventBridge.beforeTarget((Mob) (Object) this, target)) {
            ci.cancel();
        }
    }
}
