package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.Restraints
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.runtime.LiveTargeting
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import me.vangoo.mysteries.effect.flash
import me.vangoo.mysteries.effect.ward
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.pathways.justiciar.lash
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Justiciar, Seq. 7 (Interrogator): **Psychic Piercing**.
 *
 * Concentrates mental force behind the eyes: for five seconds they burn bone-white, a warning to everyone
 * not to walk in front of them. The next enemy caught in the crosshair within that time is pierced: lightning
 * leaps out of both eyes into its head, and its spirit is knocked half out of the body (a pale double thrown
 * back), shredding the spirit and its buffs alike.
 *
 * A [me.vangoo.mysteries.ability.AbilityOutcome.Awaiting] rather than an instant hit: the plugin
 * spent the cost the moment the cast began. Here the ticket only settles when the needle actually
 * lands — concentration that finds no target was never worth paying for.
 */
class PsychicPiercing : ActiveAbility(AbilityIdentity.of("justiciar_psychic_piercing")) {

    override val displayName: Component = Component.literal("Психічний Прокол")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Очі палають білим ${WINDOW_TICKS / 20} с. Наступний ворог під вашим поглядом (до $RANGE бл.) " +
            "буде проколений голкою, що пробиває Духовне Тіло, знищує захисні ефекти й паралізує ціль.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val ticket = context.cast
        val messaging = context.messaging
        val beyonders = context.beyonders
        val caster = context.caster
        val color = context.effects.pathwayColor

        Eyes.light(caster, EyeGlow.WHITE)
        context.level.playSound(
            null, caster.x, caster.y, caster.z,
            SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 2.0f,
        )

        context.session {
            try {
                val deadline = ticksAlive + WINDOW_TICKS
                while (ticksAlive < deadline) {
                    val player = owner ?: return@session
                    val target = LiveTargeting(player).lookingAt(RANGE)
                    if (target != null) {
                        pierce(player, target, color, beyonders.of(target.uuid)?.pathway?.name)
                        ticket.complete()
                        return@session
                    }
                    waitTicks(SCAN_PERIOD_TICKS)
                }
                owner?.let { messaging.hint(it, Component.literal("Концентрація розсіялась"), Fizzle.SPARK) }
            } finally {
                owner?.let(Eyes::dim)
            }
        }

        return AbilityOutcome.Awaiting()
    }

    private fun pierce(caster: ServerPlayer, target: LivingEntity, color: RgbColor, targetPathway: String?) {
        val level = caster.level()
        lash(level, caster, target, WorldEffectPayload.Lash.Shape.NEEDLE, color)
        flash(level, target.eyePosition, FLASH_RADIUS, FLASH_STRENGTH, color)
        level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 2.0f,
        )

        // Whatever guard was really there breaks on the spot, in the colour of the one who held it.
        val stripped = listOf(MobEffects.RESISTANCE, MobEffects.REGENERATION, MobEffects.ABSORPTION).count { target.removeEffect(it) }
        if (stripped > 0) {
            ward(target, if (targetPathway != null) PathwayBranding.liquidOf(targetPathway) else color)
        }

        // The spirit knocked half out of the body: for a moment the body answers nothing.
        Restraints.hold(target, STUN_TICKS)
        target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SPIRIT_DAMAGE)
        target.addEffect(MobEffectInstance(MobEffects.WITHER, 40, 1))
        if (target is Player) target.addEffect(MobEffectInstance(MobEffects.NAUSEA, 50, 0))
    }

    private companion object {
        const val RANGE = 5.0
        const val WINDOW_TICKS = 100 // 5 seconds
        const val STUN_TICKS = 20
        const val SCAN_PERIOD_TICKS = 2
        const val SPIRIT_DAMAGE = 12.0f
        const val COOLDOWN_SECONDS = 20
        const val FLASH_RADIUS = 2.0
        const val FLASH_STRENGTH = 0.6f
    }
}
