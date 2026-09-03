package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.ability.context.Targeting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.EntityHitResult

/** Picking targets around a specific caster. */
internal class LiveTargeting(private val caster: ServerPlayer) : Targeting {

    override fun livingNear(radius: Double): List<LivingEntity> =
        caster.level()
            .getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(radius), ::isViableTarget)
            .sortedBy { it.distanceToSqr(caster) }

    override fun playersNear(radius: Double): List<ServerPlayer> =
        livingNear(radius).filterIsInstance<ServerPlayer>()

    override fun lookingAt(maxRange: Double): LivingEntity? {
        val hit = ProjectileUtil.getHitResultOnViewVector(caster, ::isViableTarget, maxRange)
        return (hit as? EntityHitResult)?.entity as? LivingEntity
    }

    /** The caster is never their own target; spectators and corpses are not either. */
    private fun isViableTarget(entity: Entity): Boolean =
        entity !== caster &&
            entity is LivingEntity &&
            entity.isAlive &&
            !(entity is ServerPlayer && entity.isSpectator)
}
