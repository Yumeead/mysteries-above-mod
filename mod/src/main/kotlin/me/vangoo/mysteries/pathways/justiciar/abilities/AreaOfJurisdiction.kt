package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Jurisdiction
import me.vangoo.mysteries.pathways.justiciar.Zone
import me.vangoo.mysteries.pathways.justiciar.gavel
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID

/**
 * Justiciar, Seq. 8 (Sheriff): **Area of Jurisdiction**.
 *
 * Plants a territory of law at the caster's feet, a square on the block grid: while they stand inside it, Resistance I
 * and Haste I hold — the law favours whoever stands on its own ground.
 *
 * The bound is the Sheriff's alone to see (the user's call, 19.11.2026): a wave of light runs from their feet over the
 * whole square as it is laid, iron posts rise along the edge wherever they come near it, and crossing it they hear the
 * law close or open behind them (`client/render/Jurisdictions`). Anyone else sees nothing of it, until they walk in:
 * then a wave runs over the ground from where they stepped, lights the square up to its posts, and is gone.
 *
 * A session, so a new cast replaces the old territory, as the plugin had it; the territory goes with its Sheriff when
 * they leave the world or die.
 */
class AreaOfJurisdiction : ActiveAbility(AbilityIdentity.of("justiciar_area_of_jurisdiction")) {

    override val displayName: Component = Component.literal("Сфера Юрисдикції")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваша територія закону: квадрат зі стороною ${2 * half(sequence) + 1} бл. довкола вас.\n" +
            "▪ Усередині: Опір I та Квапливість I\n" +
            "▪ Межу бачите лише ви, коли підходите до неї\n" +
            "▪ Хто входить, бачить, як територія спалахує\n" +
            "▪ Новий каст замінює попередню територію",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val level = context.level
        val zone = Zone(caster.blockPosition(), half(context.sequence))
        val id = level.random.nextInt()
        val color = context.effects.pathwayColor.packed

        // The Sheriff stamps on their ground: the knock of the law and the dust of what they stand on.
        caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true)
        gavel(level, caster.position(), 1.2f, 0.7f)
        val ground = level.getBlockState(caster.blockPosition().below())
        if (!ground.isAir) {
            level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, ground), caster.x, caster.y + 0.1, caster.z, 24, 0.4, 0.05, 0.4, 0.15)
        }
        ModChannels.WORLD_EFFECT.sendTo(caster, Jurisdiction(id, Jurisdiction.Kind.OWN, zone.centre, zone.half, caster.position(), color))

        val dimension = level.dimension()
        context.session {
            // Those who stood inside at the last look: walking in is what shows them the ground.
            val inside = HashSet<UUID>()
            try {
                sustain(periodTicks = PERIOD_TICKS) { sheriff ->
                    if (sheriff.level().dimension() != dimension) return@sustain
                    if (zone.contains(sheriff.position())) {
                        sheriff.addEffect(MobEffectInstance(MobEffects.RESISTANCE, BUFF_TICKS, 0, true, false, true))
                        sheriff.addEffect(MobEffectInstance(MobEffects.HASTE, BUFF_TICKS, 0, true, false, true))
                    }
                    val now = HashSet<UUID>()
                    for (player in sheriff.level().players()) {
                        if (player === sheriff || player.isSpectator || !zone.contains(player.position())) continue
                        now += player.uuid
                        if (player.uuid !in inside) {
                            ModChannels.WORLD_EFFECT.sendTo(player, Jurisdiction(id, Jurisdiction.Kind.REVEAL, zone.centre, zone.half, player.position(), color))
                        }
                    }
                    inside.clear()
                    inside += now
                }
            } finally {
                owner?.let { ModChannels.WORLD_EFFECT.sendTo(it, Jurisdiction(id, Jurisdiction.Kind.CLEAR, zone.centre, zone.half, it.position(), color)) }
            }
        }

        return AbilityOutcome.Done()
    }

    /** Blocks the square reaches out from its centre each way. */
    private fun half(sequence: Sequence): Int =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.DIVINE)

    private companion object {
        const val BASE_RADIUS = 50
        const val COOLDOWN_SECONDS = 70
        const val PERIOD_TICKS = 10
        const val BUFF_TICKS = 40
    }
}
