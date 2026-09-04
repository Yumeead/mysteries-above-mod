package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 8 (Clown): **Expression Control**.
 *
 * A perfect mask, on for as long as it is paid for. What it blocks lives outside this file: a
 * reading power (`ScanGaze`, `Telepathy`, `SeerSpiritVision`'s own Beyonder callout) is expected
 * to check [me.vangoo.mysteries.ability.context.Beyonders.isToggled] against [identity] before
 * it reports anything about this Beyonder — the same shield contract `AntiDivination` already
 * established, not a static `isMasked(UUID)` of its own the way the plugin built it.
 */
class ExpressionControl :
    ToggleablePassiveAbility(AbilityIdentity.of("fool_expression_control")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Контроль міміки")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Надягає ідеальну маску: здібності читання не бачать ваших намірів чи стану.\n" +
            "▪ Витрата $COST_PER_SECOND духовності/с",
    )

    override fun onEnable(context: AbilityContext) {
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
        context.messaging.actionBar(context.caster, Component.literal("🎭 Маска активна"))
    }

    override fun onDisable(context: AbilityContext) {
        chime(context, SoundEvents.BEACON_DEACTIVATE, 1.2f)
        context.messaging.actionBar(context.caster, Component.literal("🎭 Маска знята"))
    }

    override fun tick(context: AbilityContext) {
        if (!context.beyonder.payUpkeep(COST_PER_SECOND)) {
            context.messaging.actionBar(context.caster, Component.literal("🎭 Духовність вичерпана — маска тримається на волосині"))
            return
        }
        val at: Vec3 = context.caster.position()
        context.level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 2.0, at.z, 3, 0.15, 0.1, 0.15, 0.0)
    }

    private fun chime(context: AbilityContext, sound: SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    private companion object {
        const val COST_PER_SECOND = 10
    }
}
