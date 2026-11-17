package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.pathways.justiciar.lash
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Justiciar, Seq. 7 (Interrogator): **Whip of Pain**.
 *
 * A barbed whip is swung over the shoulder, unrolls and cracks on the target; the blow and the debuffs
 * land with the crack, not before. The pain shakes answers out of the target, and they
 * hang over it one at a time for the caster alone: what rank it is, what it holds, how much is left of it.
 * This is knowledge, so it is the one place the power writes into the world.
 */
class WhipOfPain : ActiveAbility(AbilityIdentity.of("justiciar_whip_of_pain")) {

    override val displayName: Component = Component.literal("Хлист Болю")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Батіг із залізних ланок б'є в ціль під прицілом.\n" +
            "▪ Шкода $DAMAGE, Повільність і Сліпота\n" +
            "▪ Дальність $RANGE бл.\n" +
            "▪ Болю не стримати: над ціллю по черзі з'являються Послідовність, зброя й здоров'я, їх бачите лише ви",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає цілі під прицілом")
        val caster = context.caster
        val level = context.level
        val color = context.effects.pathwayColor

        lash(level, caster, target, WorldEffectPayload.Lash.Shape.WHIP, color)
        caster.swing(InteractionHand.MAIN_HAND, true)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.6f)

        // The crack: the whip is taut on this tick, and so is the blow.
        context.scheduling.after(WorldEffectPayload.Lash.WHIP_CRACK_TICKS) {
            if (!target.isAlive) return@after
            level.playSound(
                null, target.x, target.y, target.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.5f,
            )
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), DAMAGE)
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 4))
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0))
        }

        val beyonders = context.beyonders
        INTERROGATION_TICKS.forEachIndexed { question, delay ->
            context.scheduling.after(delay) {
                if (!target.isAlive) return@after
                val answer = when (question) {
                    0 -> beyonders.of(target.uuid)?.let { Component.literal("Посл. ${it.sequenceLevel}") } ?: Component.literal("Не Потойбічний")
                    1 -> (target as? Player)?.mainHandItem?.takeUnless { it.isEmpty }?.displayName ?: Component.literal("Нічого в руці")
                    else -> Component.literal(health(target))
                }
                privateText(caster, target.eyePosition, answer, ANSWER_TICKS, color, anchor = target)
            }
        }
        return AbilityOutcome.Done()
    }

    /** What is left of [target] as [SQUARES] squares, a filled one for each fifth. */
    private fun health(target: LivingEntity): String {
        val filled = Math.ceil(SQUARES * target.health / target.maxHealth.toDouble()).toInt().coerceIn(0, SQUARES)
        return "■".repeat(filled) + "□".repeat(SQUARES - filled)
    }

    private companion object {
        const val RANGE = 10.0
        const val DAMAGE = 7.0f
        const val COOLDOWN_SECONDS = 12
        const val SQUARES = 5

        /** Ticks after the cast at which each answer is shaken out, and for how long it hangs. */
        val INTERROGATION_TICKS = intArrayOf(10, 20, 30)
        const val ANSWER_TICKS = 80
    }
}
