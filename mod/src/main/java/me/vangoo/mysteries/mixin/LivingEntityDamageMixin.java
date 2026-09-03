package me.vangoo.mysteries.mixin;

import me.vangoo.mysteries.event.GameEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The mod's first mixin: it lets an ability <b>change</b> incoming damage.
 *
 * Cancellation is not part of this — the ready {@code ALLOW_DAMAGE} from the Fabric API can do
 * that, and that is exactly where it stayed. Here is only what a ready hook cannot give: the
 * number.
 *
 * <h2>Mixin discipline</h2>
 *
 * <ul>
 *   <li><b>Method boundaries only.</b> {@code HEAD} and {@code RETURN}; no
 *       {@code @Redirect}, {@code ordinal=}, {@code slice}. A hook on a boundary survives a
 *       rewrite of the method body, a hook inside it does not. {@code @ModifyVariable} with
 *       {@code argsOnly = true} is a boundary too: it edits an <b>argument</b>, not a local
 *       variable, and {@code hurtServer} has exactly one {@code float} argument, so
 *       {@code ordinal} is not needed.</li>
 *   <li><b>{@code require = 1} is mandatory.</b> Without it a missed injection is silent, and
 *       the mechanic simply does not work instead of refusing to start.</li>
 *   <li><b>Zero logic in the body.</b> One call into our own bus — see
 *       {@link GameEventBridge#beforeDamage}.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, require = 1)
    private float mysteries_above$beforeDamage(float amount, ServerLevel level, DamageSource source) {
        return GameEventBridge.beforeDamage((LivingEntity) (Object) this, level, source, amount);
    }
}
