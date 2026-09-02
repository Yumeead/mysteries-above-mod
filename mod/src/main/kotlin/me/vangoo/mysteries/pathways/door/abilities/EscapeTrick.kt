package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Door, Посл. 8 (Трюкач): **Трюк зі зникненням**.
 *
 * Трюкача смикає назад, а на місці, де він щойно стояв, лишається завіса. Хто в неї
 * увійде — грузне.
 *
 * ## Ривок — це швидкість, а не телепорт
 *
 * [net.minecraft.world.entity.Entity.hurtMarked] — той самий прапорець, яким ванілька
 * штовхає гравця вибухом: без нього сервер змінив би швидкість у себе, а клієнт нічого
 * не помітив би й наступним тіком повернув гравця назад. Телепорт замість ривка з'їв би
 * головне: ривок можна перервати стіною, а телепорт — ні.
 *
 * ## Завіса лишається на місці, а не їде за Трюкачем
 *
 * Тому центр запам'ятовується один раз, у мить касту, і сесія малює саме там. Завіса,
 * прив'язана до кастера, була б димом на комірі — тобто нічого не ховала б.
 */
class EscapeTrick : ActiveAbility(AbilityIdentity.of("door_escape_trick")) {

    override val displayName: Component = Component.literal("Трюк зі зникненням")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вас смикає назад, а на вашому місці лишається завіса диму.\n" +
            "▪ Завіса тримається ${SMOKE_TICKS / TICKS_PER_SECOND} с у радіусі ${SMOKE_RADIUS} бл.\n" +
            "▪ Хто в ній — грузне (Сповільнення II)\n" +
            "▪ Перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val centre = context.origin.add(0.0, 1.0, 0.0)
        val level = context.level

        // Ривок назад. Вертикальна складова невелика й стала: без неї гравець просто
        // тертиме об землю, з великою — злітає й отримує шкоду від падіння.
        val back = Vec3(-context.look.x, 0.0, -context.look.z).let {
            if (it.lengthSqr() < 1.0e-6) Vec3(0.0, 0.0, 0.0) else it.normalize().scale(PULL_STRENGTH)
        }
        context.caster.setDeltaMovement(back.x, LIFT, back.z)
        context.caster.hurtMarked = true

        puff(level, centre)
        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 0.6f,
        )
        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f,
        )

        context.session {
            sustain(periodTicks = SMOKE_PERIOD_TICKS, durationTicks = SMOKE_TICKS) { trickster ->
                val world = trickster.level() as? ServerLevel ?: return@sustain
                world.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    centre.x, centre.y, centre.z,
                    25, 1.3, 0.6, 1.3, 0.0,
                )
                world.getEntitiesOfClass(
                    LivingEntity::class.java,
                    AABB.ofSize(centre, SMOKE_RADIUS * 2, SMOKE_RADIUS * 2, SMOKE_RADIUS * 2),
                ) { it !== trickster && it.position().distanceToSqr(centre) <= SMOKE_RADIUS * SMOKE_RADIUS }
                    .forEach { it.addEffect(MobEffectInstance(MobEffects.SLOWNESS, MIRE_TICKS, 1)) }
            }
        }

        return AbilityOutcome.done("Вас там більше немає")
    }

    /** Початковий вибух: чотири ванільні дими, бо один читається як багаття, а не завіса. */
    private fun puff(level: ServerLevel, centre: Vec3) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 120, 1.2, 1.0, 1.2, 0.02)
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, centre.x, centre.y, centre.z, 80, 0.8, 1.5, 0.8, 0.01)
        level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 45, 0.7, 0.6, 0.7, 0.0)
        level.sendParticles(ParticleTypes.DUST_PLUME, centre.x, centre.y, centre.z, 60, 1.5, 0.8, 1.5, 0.0)
    }

    private companion object {
        const val PULL_STRENGTH = 1.3
        const val LIFT = 0.25
        const val SMOKE_RADIUS = 2.5
        const val COOLDOWN_SECONDS = 10
        const val SMOKE_TICKS = 3 * TICKS_PER_SECOND
        const val SMOKE_PERIOD_TICKS = 5
        /** Трохи довше за такт, щоб між тактами не відпускало. */
        const val MIRE_TICKS = 20
    }
}
