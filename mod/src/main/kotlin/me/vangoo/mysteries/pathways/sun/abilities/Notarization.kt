package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity

/**
 * Sun, Seq. 6 (Notary): **Notarization**.
 *
 * The Notary certifies a fact — and the fact becomes binding. Sneaking and looking at a target,
 * they either **annul** it (every ability on cooldown) or **certify** an ally (a boost for a time).
 *
 * ## Sneaking chooses whom and what
 *
 * No menu is needed here: the notary is either a friend of the target or not, and the target
 * itself decides that. The unclean get the annulment, everyone else the certification. One
 * gesture, two actions, zero interface.
 *
 * ## The annulment is `cooldowns.lockAll`, not a registry of "silenced" players
 *
 * The plugin kept its own map of silenced players and checked it at the start of every cast. The
 * mod has a ready service for that: [me.vangoo.mysteries.ability.context.Cooldowns.lockAll] puts
 * a cooldown on **all** of the target's abilities at once, and `AbilityExecutor` checks the
 * cooldown before every cast anyway. So there is neither new state nor a new check: the mechanic
 * was already there, it just had not been used.
 */
class Notarization : ActiveAbility(AbilityIdentity.of("sun_notarization")) {

    override val displayName: Component = Component.literal("Нотаріальність")

    override val spiritualityCost: Int = 140

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Присівши й дивлячись на ціль, засвідчуєте її стан.\n" +
            "▪ Нечисті — Анулювання: усі її здібності йдуть у відкат на ${silence(sequence)} с\n" +
            "▪ Решті — Засвідчення: Сила й Опір на ${amplify(sequence)} с\n" +
            "▪ Потрібно присісти; дальність ${RANGE.toInt()} бл.",
    )

    /** The gesture is part of the ability: a certification is not done in passing. */
    override fun canCast(context: CastContext): Boolean = context.caster.isShiftKeyDown

    /** The annulment is a power against a Beyonder, so it is contested by Sequence. */
    override fun contestedTarget(context: CastContext): LivingEntity? =
        context.targeting.lookingAt(RANGE)?.takeIf { it.isProfane(context.beyonders) }

    override fun cast(context: CastContext): AbilityOutcome {
        if (!context.caster.isShiftKeyDown) {
            return AbilityOutcome.failed("Засвідчення робиться присівши")
        }
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає кого засвідчувати: наведіть погляд на ціль")

        seal(context, target)

        if (target.isProfane(context.beyonders)) {
            val seconds = silence(context.sequence)
            context.cooldowns.lockAll(target.uuid, seconds)
            context.messaging.actionBar(
                target.uuid,
                Component.literal("Анулювання: ваші сили замовкли на $seconds с"),
            )
            return AbilityOutcome.done("Сили ${target.name.string} замовкли на $seconds с")
        }

        val seconds = amplify(context.sequence)
        val ticks = seconds * TICKS_PER_SECOND
        target.addEffect(
            net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.STRENGTH, ticks, 0, false, true, true,
            ),
        )
        target.addEffect(
            net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, ticks, 0, false, true, true,
            ),
        )
        context.effects.bodyAura(target, ticks)
        context.messaging.actionBar(target.uuid, Component.literal("Ваш стан засвідчено"))
        return AbilityOutcome.done("Засвідчено ${target.name.string} на $seconds с")
    }

    /** The seal: a short ring around the target and a sound both of them hear. */
    private fun seal(context: CastContext, target: LivingEntity) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val at = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        repeat(SEAL_POINTS) { i ->
            val angle = TAU * i / SEAL_POINTS
            context.level.sendParticles(
                mote,
                at.x + SEAL_RADIUS * Math.cos(angle), at.y, at.z + SEAL_RADIUS * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f,
        )
    }

    private fun silence(sequence: Sequence): Int =
        scale(BASE_SILENCE_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private fun amplify(sequence: Sequence): Int =
        scale(BASE_AMPLIFY_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val RANGE = 15.0
        const val BASE_SILENCE_SECONDS = 13
        const val BASE_AMPLIFY_SECONDS = 15
        const val COOLDOWN_SECONDS = 45
        const val SEAL_POINTS = 24
        const val SEAL_RADIUS = 1.0
        const val TAU = 2.0 * Math.PI
    }
}
