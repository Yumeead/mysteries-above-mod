package me.vangoo.mysteries.ability.context

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

/**
 * Вибір цілей навколо кастера. Прив'язаний до одного касту — радіуси й промінь
 * рахуються від нього, тож ідентичність кастера всередині вже є і другим аргументом
 * не передається.
 *
 * Сам кастер із результатів завжди виключений: «поблизу» означає «крім мене».
 */
interface Targeting {

    fun livingNear(radius: Double): List<LivingEntity>

    fun playersNear(radius: Double): List<ServerPlayer>

    /** Істота на промені погляду; `null`, якщо промінь упреться в блок або в порожнечу. */
    fun lookingAt(maxRange: Double): LivingEntity?

    fun playerLookingAt(maxRange: Double): ServerPlayer? = lookingAt(maxRange) as? ServerPlayer
}
