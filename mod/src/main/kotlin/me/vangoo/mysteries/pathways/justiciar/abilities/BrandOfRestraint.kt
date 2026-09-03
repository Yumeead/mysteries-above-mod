package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.justiciar.rootInPlace
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

/**
 * Justiciar, Seq. 7 (Interrogator): **Brand of Restraint**.
 *
 * Burns an illusory brand onto the target's soul: total immobility, plus a lock on the target's
 * own powers if they are a Beyonder.
 */
class BrandOfRestraint : ActiveAbility(AbilityIdentity.of("justiciar_brand_of_restraint")) {

    override val displayName: Component = Component.literal("Клеймо Обмеження")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Випалює ілюзорне клеймо на душі ворога під прицілом (дальність $RANGE бл.).\n" +
            "▪ Ціль повністю знерухомлюється на ${DURATION_TICKS / 20} с\n" +
            "▪ Якщо ціль — Потойбічний, її здібності теж блокуються",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть на ціль, щоб накласти клеймо")

        showBrandingEffect(context, target)

        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, DURATION_TICKS, 255))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, DURATION_TICKS, 250))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, DURATION_TICKS, 255))
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, DURATION_TICKS, 5))
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, DURATION_TICKS, 0))
        context.rootInPlace(target, DURATION_TICKS, boundaryParticle = ParticleTypes.CRIT, boundarySound = SoundEvents.CHAIN_BREAK)

        if (target is ServerPlayer) {
            context.cooldowns.lockAll(target.uuid, DURATION_TICKS / 20)
            context.messaging.actionBar(target, Component.literal("Вас скуто Клеймом Обмеження! Рух неможливий."))
        }

        context.messaging.actionBar(context.caster, Component.literal("Ви повністю обмежили ${target.name.string}"))
        return AbilityOutcome.done("Клеймо Обмеження накладено")
    }

    private fun showBrandingEffect(context: CastContext, target: LivingEntity) {
        val eye = target.eyePosition
        context.level.sendParticles(ParticleTypes.FLAME, eye.x, eye.y + 0.5, eye.z, 20, 0.8, 0.3, 0.8, 0.05)
        context.level.sendParticles(ParticleTypes.CRIT, eye.x, eye.y + 1.0, eye.z, 20, 0.3, 0.5, 0.3, 0.1)
        context.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 2.0f)
        context.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f)
    }

    private companion object {
        const val RANGE = 15.0
        const val DURATION_TICKS = 6 * 20
        const val COOLDOWN_SECONDS = 25
    }
}
