package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.runtime.LiveTargeting
import me.vangoo.mysteries.ability.session
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Justiciar, Seq. 7 (Interrogator): **Psychic Piercing**.
 *
 * Concentrates mental force behind the eyes; the next enemy caught in the crosshair within five
 * seconds is struck by a bolt that shreds their spirit and their buffs alike.
 *
 * A [me.vangoo.mysteries.ability.AbilityOutcome.Awaiting] rather than an instant hit: the plugin
 * spent the cost the moment the cast began. Here the ticket only settles when the bolt actually
 * lands — concentration that finds no target was never worth paying for.
 */
class PsychicPiercing : ActiveAbility(AbilityIdentity.of("justiciar_psychic_piercing")) {

    override val displayName: Component = Component.literal("Психічний Прокол")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Наступний погляд на ворога (до $RANGE бл.) протягом ${WINDOW_TICKS / 20} с випускає " +
            "блискавку, що пробиває Духовне Тіло, знищує захисні ефекти й паралізує ціль.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val ticket = context.cast
        val messaging = context.messaging
        val caster = context.caster

        messaging.actionBar(caster, Component.literal("Ви концентруєте Психічний Прокол. Подивіться на ворога..."))
        context.level.playSound(
            null, caster.x, caster.y, caster.z,
            SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 2.0f,
        )

        context.session {
            val deadline = ticksAlive + WINDOW_TICKS
            while (ticksAlive < deadline) {
                val player = owner ?: return@session
                val target = LiveTargeting(player).lookingAt(RANGE)
                if (target != null) {
                    pierce(player, target)
                    ticket.complete()
                    return@session
                }
                waitTicks(SCAN_PERIOD_TICKS)
            }
            owner?.let { messaging.actionBar(it, Component.literal("Концентрація розсіялась...")) }
        }

        return AbilityOutcome.Awaiting(Component.literal("Психічний Прокол заряджається"))
    }

    private fun pierce(caster: net.minecraft.server.level.ServerPlayer, target: LivingEntity) {
        val level = caster.level()
        level.sendParticles(
            ParticleTypes.FIREWORK,
            (caster.eyePosition.x + target.eyePosition.x) / 2,
            (caster.eyePosition.y + target.eyePosition.y) / 2,
            (caster.eyePosition.z + target.eyePosition.z) / 2,
            20, 0.2, 0.2, 0.2, 0.1,
        )
        level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 2.0f,
        )

        target.removeEffect(MobEffects.RESISTANCE)
        target.removeEffect(MobEffects.REGENERATION)
        target.removeEffect(MobEffects.ABSORPTION)

        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 20, 10))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 20, 128))
        target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SPIRIT_DAMAGE)
        target.addEffect(MobEffectInstance(MobEffects.WITHER, 40, 1))
        if (target is Player) target.addEffect(MobEffectInstance(MobEffects.NAUSEA, 50, 0))
    }

    private companion object {
        const val RANGE = 5.0
        const val WINDOW_TICKS = 100 // 5 seconds
        const val SCAN_PERIOD_TICKS = 2
        const val SPIRIT_DAMAGE = 12.0f
        const val COOLDOWN_SECONDS = 20
    }
}
