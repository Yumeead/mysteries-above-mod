package me.vangoo.mysteries.mixin;

import me.vangoo.mysteries.ability.Restraints;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A bound mob does not think: no goals, no pathfinding, no blows, no techniques of the creature engine.
 *
 * <p>{@code serverAiStep} is where all of a mob's own will runs, every tick; the Fabric API has no hook into it. The
 * injection sits at {@code HEAD} and does one thing: ask {@link Restraints#holdsMind} and skip the step if it says
 * the mob is bound. Gravity and being pushed are outside it, so a bound mob still falls.
 */
@Mixin(Mob.class)
public abstract class MobRestraintMixin {

    @Inject(method = "serverAiStep()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void mysteries$restrained(CallbackInfo ci) {
        if (Restraints.holdsMind((Mob) (Object) this)) ci.cancel();
    }
}
