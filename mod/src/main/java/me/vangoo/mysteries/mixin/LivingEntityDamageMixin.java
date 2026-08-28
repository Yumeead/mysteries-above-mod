package me.vangoo.mysteries.mixin;

import me.vangoo.mysteries.event.GameEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Перший міксин мода: дозволяє здібності <b>змінити</b> вхідну шкоду.
 *
 * Скасування сюди не входить — його вміє готовий {@code ALLOW_DAMAGE} з Fabric API,
 * і саме там воно й лишилось. Тут рівно те, чого готовий гачок дати не може: число.
 *
 * <h2>Дисципліна міксинів</h2>
 *
 * <ul>
 *   <li><b>Тільки межа методу.</b> {@code HEAD} і {@code RETURN}; ніяких
 *       {@code @Redirect}, {@code ordinal=}, {@code slice}. Гачок на межу переживає
 *       перепис тіла методу, гачок усередину — ні. {@code @ModifyVariable} з
 *       {@code argsOnly = true} — теж межа: він править <b>аргумент</b>, а не локальну
 *       змінну, і в {@code hurtServer} аргумент типу {@code float} рівно один, тож
 *       {@code ordinal} не потрібен.</li>
 *   <li><b>Обов'язковий {@code require = 1}.</b> Без нього промах ін'єкції тихий, і
 *       механіка просто мовчки не працює замість того, щоб не запуститись.</li>
 *   <li><b>Нуль логіки в тілі.</b> Один виклик у свою шину — див.
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
