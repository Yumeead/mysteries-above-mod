package me.vangoo.mysteries.ability.context

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

/**
 * Picking targets around the caster. Bound to one cast — radii and the ray are measured from
 * it, so the caster's identity is already inside and is not passed as a second argument.
 *
 * The caster is always excluded from the results: "nearby" means "except me".
 */
interface Targeting {

    fun livingNear(radius: Double): List<LivingEntity>

    fun playersNear(radius: Double): List<ServerPlayer>

    /** The creature on the line of sight; `null` if the ray hits a block or empty space. */
    fun lookingAt(maxRange: Double): LivingEntity?

    fun playerLookingAt(maxRange: Double): ServerPlayer? = lookingAt(maxRange) as? ServerPlayer
}
