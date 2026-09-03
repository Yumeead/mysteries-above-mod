package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Tyrant, Seq. 7 (Seafarer): **Azure Wave**.
 *
 * The Seafarer flashes with azure light and throws a wall of water forward, sweeping the ground
 * ahead of them. Everyone the rolling front reaches is struck and shoved along with it.
 */
class AzureWave : ActiveAbility(AbilityIdentity.of("tyrant_azure_wave")) {

    override val displayName: Component = Component.literal("Лазурна Хвиля")

    override val spiritualityCost: Int = 70

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Лазурний спалах жбурляє вперед водяний вал: усе на шляху відкидає, поки хвиля котиться.\n" +
            "▪ Шкода: ${damage(sequence)}\n" +
            "▪ Довжина ${LENGTH.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val origin = caster.position()
        val forward = Vec3(caster.lookAngle.x, 0.0, caster.lookAngle.z).normalize()
        val damage = damage(context.sequence)

        splash(context, origin)
        roll(context, origin, forward, damage)

        return AbilityOutcome.done("Лазурна хвиля котиться вперед")
    }

    /** The front advances one step per tick and hits whoever it currently overlaps, once each. */
    private fun roll(context: CastContext, origin: Vec3, forward: Vec3, damage: Int) {
        val step = LENGTH / DURATION_TICKS
        val half = WIDTH / 2.0
        val hit = mutableSetOf<UUID>()
        var tick = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            tick++
            if (tick > DURATION_TICKS) return@every handle.cancel()

            val front = origin.add(forward.scale(step * tick))
            val box = net.minecraft.world.phys.AABB(
                front.x - half, front.y - 2.0, front.z - half,
                front.x + half, front.y + 2.0, front.z + half,
            )
            for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== context.caster }) {
                if (!hit.add(target.uuid)) continue
                target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
                val push = forward.scale(KNOCKBACK)
                target.deltaMovement = target.deltaMovement.add(push.x, 0.4, push.z)
                target.hurtMarked = true
            }
            context.level.sendParticles(ParticleTypes.SPLASH, front.x, front.y + 0.1, front.z, 6, half, 0.1, half, 0.02)
        }
    }

    private fun splash(context: CastContext, at: Vec3) {
        context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 20, 1.0, 0.3, 1.0, 0.08)
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 8
        const val LENGTH = 12.0
        const val WIDTH = 5.0
        const val KNOCKBACK = 1.3
        const val DURATION_TICKS = 20
        const val COOLDOWN_SECONDS = 15
    }
}
