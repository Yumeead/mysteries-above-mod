package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 6 (Wind-Blessed): **Water Control**.
 *
 * The Wind-Blessed commands water that is already there — so the power only works near water or
 * under rain. One ability with three outlets, a sneaking cast cycles between them: **Jet**,
 * **Shield**, **Soak**.
 */
class WaterControl : ActiveAbility(AbilityIdentity.of("tyrant_water_control")) {

    override val displayName: Component = Component.literal("Владарювання Водою")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Керує наявною водою — лише біля води або під дощем.\n" +
            "▪ Струмінь: ${jetDamage(sequence)} шкоди й відкид цілі\n" +
            "▪ Щит: водяна завіса, Опір і Регенерація на ${SHIELD_TICKS / 20} с\n" +
            "▪ Просочення: ${soakDamage(sequence)} шкоди, сповільнення й збите полум'я\n" +
            "▪ Присід + застосування — перемкнути режим",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val owner = caster.uuid

        if (caster.isShiftKeyDown) {
            val next = context.modes.cycle(owner, this, context.sequence)
            caster.playSound(SoundEvents.BUCKET_FILL, 0.8f, 1.4f)
            return AbilityOutcome.Awaiting(Component.literal("Режим води: ${next?.name?.string}"))
        }

        if (!nearWater(context)) {
            return AbilityOutcome.failed("Поруч немає води — і дощу теж")
        }

        return when (Mode.entries.firstOrNull { it.name.lowercase() == mode(context) } ?: Mode.JET) {
            Mode.JET -> jet(context)
            Mode.SHIELD -> shield(context)
            Mode.SOAK -> soak(context)
        }
    }

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Mode.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.label)) }

    /** In water, under open-sky rain, or a water block within a small scan radius. */
    private fun nearWater(context: CastContext): Boolean {
        val caster = context.caster
        if (caster.isInWater) return true

        val level = context.level
        val pos = caster.blockPosition()
        if (level.isRaining && level.canSeeSky(pos)) return true

        for (x in -WATER_SCAN_RADIUS..WATER_SCAN_RADIUS) {
            for (y in -WATER_SCAN_RADIUS..WATER_SCAN_RADIUS) {
                for (z in -WATER_SCAN_RADIUS..WATER_SCAN_RADIUS) {
                    if (level.getFluidState(pos.offset(x, y, z)).`is`(net.minecraft.tags.FluidTags.WATER)) return true
                }
            }
        }
        return false
    }

    private fun jet(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі на лінії зору")
        val caster = context.caster
        val damage = jetDamage(context.sequence)

        target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
        target.clearFire()
        var push = target.position().subtract(caster.position())
        push = Vec3(push.x, 0.0, push.z)
        push = if (push.lengthSqr() < 1.0e-4) Vec3(caster.lookAngle.x, 0.0, caster.lookAngle.z) else push.normalize()
        push = push.scale(JET_KNOCKBACK)
        target.deltaMovement = target.deltaMovement.add(push.x, 0.35, push.z)
        target.hurtMarked = true

        val at = target.position()
        context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.06)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f, 0.9f)
        val casterAt = caster.position()
        context.level.playSound(
            null, casterAt.x, casterAt.y, casterAt.z,
            SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
        return AbilityOutcome.done("Струмінь води б'є в ${target.name.string}")
    }

    private fun shield(context: CastContext): AbilityOutcome {
        val caster = context.caster
        caster.addEffect(MobEffectInstance(MobEffects.RESISTANCE, SHIELD_TICKS, 1))
        caster.addEffect(MobEffectInstance(MobEffects.REGENERATION, SHIELD_TICKS, 0))
        caster.clearFire()
        context.effects.bodyAura(caster, SHIELD_TICKS, counterFlow = ParticleTypes.DRIPPING_WATER)

        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 1.0f, 0.9f)
        return AbilityOutcome.done("Водяна завіса огортає вас")
    }

    private fun soak(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(MAX_RANGE)
            ?: return AbilityOutcome.failed("Немає цілі на лінії зору")
        val damage = soakDamage(context.sequence)

        target.clearFire()
        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), damage.toFloat())
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, SOAK_TICKS, 1))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, SOAK_TICKS, 1))
        context.effects.bodyAura(target, SOAK_TICKS, counterFlow = ParticleTypes.DRIPPING_WATER)

        val at = target.position()
        context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 10, 0.4, 0.4, 0.4, 0.06)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f)
        return AbilityOutcome.done("${target.name.string} набрякає водою")
    }

    private fun jetDamage(sequence: Sequence): Int = scale(BASE_JET_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)
    private fun soakDamage(sequence: Sequence): Int = scale(BASE_SOAK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private enum class Mode(val label: String) { JET("Струмінь"), SHIELD("Щит"), SOAK("Просочення") }

    private companion object {
        const val BASE_JET_DAMAGE = 7
        const val BASE_SOAK_DAMAGE = 3
        const val MAX_RANGE = 20.0
        const val JET_KNOCKBACK = 1.6
        const val SHIELD_TICKS = 120
        const val SOAK_TICKS = 100
        const val WATER_SCAN_RADIUS = 4
        const val COOLDOWN_SECONDS = 8
    }
}
