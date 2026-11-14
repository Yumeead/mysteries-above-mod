package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Face.Kind
import me.vangoo.mysteries.pathways.fool.face
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

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
    ToggleablePassiveAbility(IDENTITY),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Контроль міміки")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Надягає ідеальну маску: здібності читання не бачать ваших намірів чи стану.\n" +
            "▪ Витрата $COST_PER_SECOND духовності/с",
    )

    // A paper mask settles on the face and sinks in: from then on the face is the mask.
    override fun onEnable(context: AbilityContext) {
        face(context.level, Kind.MASK, context.caster)
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
    }

    override fun onDisable(context: AbilityContext) {
        face(context.level, Kind.UNMASK, context.caster)
        chime(context, SoundEvents.BEACON_DEACTIVATE, 1.2f)
    }

    override fun tick(context: AbilityContext) {
        // A perfect mask shows nothing — that is the whole of it. Only its slipping is felt.
        if (!context.beyonder.payUpkeep(COST_PER_SECOND)) {
            context.messaging.hint(context.caster, Component.literal("Духовність вичерпана — маска тримається на волосині"), Fizzle.EMPTY)
        }
    }

    private fun chime(context: AbilityContext, sound: SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    companion object {
        /** The mask a reading power checks for before it reports anything about this Beyonder. */
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("fool_expression_control")
        private const val COST_PER_SECOND = 10
    }
}
