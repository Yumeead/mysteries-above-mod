package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.pathways.justiciar.Zone
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Justiciar, Seq. 8 (Sheriff): **Area of Jurisdiction**.
 *
 * Plants a territory of law at the caster's feet: while they stand inside it, Resistance I and
 * Haste I hold — the law favours whoever stands on its own ground.
 *
 * A session rather than the [me.vangoo.mysteries.pathways.justiciar.raiseZone] primitive: the
 * plugin's domain had no fixed duration at all, only "a new cast replaces the old one" — exactly
 * the semantics [me.vangoo.mysteries.ability.context.session] already gives for free.
 */
class AreaOfJurisdiction : ActiveAbility(AbilityIdentity.of("justiciar_area_of_jurisdiction")) {

    override val displayName: Component = Component.literal("Сфера Юрисдикції")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Встановлює територію закону радіусом ${radius(sequence)} бл. на вашій позиції.\n" +
            "▪ Усередині: Опір I та Квапливість I, поки ви там стоїте\n" +
            "▪ Новий каст замінює попередню територію",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val zone = Zone(context.origin, context.level, radius(context.sequence).toDouble())
        showCreationEffects(context, zone)
        context.messaging.actionBar(context.caster, Component.literal("Ви встановили Територію (${zone.radius.toInt()} бл.)"))

        context.session {
            sustain(periodTicks = 20) { player ->
                if (zone.contains(player.position())) {
                    player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 50, 0))
                    player.addEffect(MobEffectInstance(MobEffects.HASTE, 50, 0))
                }
                zone.drawBoundary(ParticleTypes.FLAME, points = 24)
            }
        }

        return AbilityOutcome.done("Територія закону встановлена")
    }

    private fun showCreationEffects(context: CastContext, zone: Zone) {
        val center = context.origin
        context.level.playSound(
            null, center.x, center.y, center.z,
            SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.5f,
        )
        zone.drawBoundary(ParticleTypes.END_ROD, points = 36)
        context.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0)
    }

    private fun radius(sequence: Sequence): Int =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.DIVINE)

    private companion object {
        const val BASE_RADIUS = 50
        const val COOLDOWN_SECONDS = 70
    }
}
