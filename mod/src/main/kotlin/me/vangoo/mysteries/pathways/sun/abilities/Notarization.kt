package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.burst
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

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

    /**
     * The annulment is a power against a Beyonder, so it is contested by Sequence — but only once
     * the gesture is made: a notary who did not kneel has not begun, and must not lose a cooldown to it.
     */
    override fun contestedTarget(context: CastContext): LivingEntity? =
        context.targeting.lookingAt(RANGE)?.takeIf { context.caster.isShiftKeyDown && it.isProfane(context.beyonders) }

    override fun cast(context: CastContext): AbilityOutcome {
        // The gesture is part of the ability: a certification is not done in passing.
        if (!context.caster.isShiftKeyDown) {
            return AbilityOutcome.failed("Засвідчення робиться присівши")
        }
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає кого засвідчувати: наведіть погляд на ціль")

        seal(context, target)

        if (target.isProfane(context.beyonders)) {
            val seconds = silence(context.sequence)
            context.cooldowns.lockAll(target.uuid, seconds)
            shatter(context, target)
            // The breaking is seen; the words are for a target who turned hints on.
            context.messaging.hint(target.uuid, Component.literal("Анулювання: ваші сили замовкли на $seconds с"))
            return AbilityOutcome.Done()
        }

        val ticks = amplify(context.sequence) * TICKS_PER_SECOND
        target.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, 0, false, true, true))
        target.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ticks, 0, false, true, true))
        context.effects.bodyAura(target, ticks)
        return AbilityOutcome.Done()
    }

    /**
     * The seal: for a moment a stamp of light hangs over the target, turned to the notary — the
     * Sun's own mark, a ring with rays around it — and the thud of a stamp both of them hear. A
     * certification is a mark set on something, and this is the one power that may draw a mark.
     */
    private fun seal(context: CastContext, target: LivingEntity) {
        val level = context.level
        val light = ModParticles.light(context.effects.pathwayColor)
        val centre = target.position().add(0.0, target.bbHeight + SEAL_LIFT, 0.0)
        val toward = context.caster.position().subtract(target.position())
        // The stamp's plane faces the notary: "aside" runs across their view, "up" is up.
        val aside = Vec3(-toward.z, 0.0, toward.x).normalize().takeIf { it.lengthSqr() > 0 } ?: Vec3(1.0, 0.0, 0.0)
        val up = Vec3(0.0, 1.0, 0.0)
        fun mark(x: Double, y: Double) {
            val at = centre.add(aside.scale(x)).add(up.scale(y))
            level.sendParticles(light, at.x, at.y, at.z, 0, 0.0, 0.0, 0.0, 0.0)
        }
        repeat(SEAL_POINTS) { i ->
            val angle = TAU * i / SEAL_POINTS
            mark(SEAL_RADIUS * cos(angle), SEAL_RADIUS * sin(angle))
        }
        // The rays stand apart from the ring, so they read as rays and not as a thicker ring.
        repeat(SEAL_RAYS) { i ->
            val angle = TAU * (i + 0.5) / SEAL_RAYS
            mark(SEAL_RAY_RADIUS * cos(angle), SEAL_RAY_RADIUS * sin(angle))
        }
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f)
    }

    /**
     * The annulment breaks the target's own power around them: a shell in the colour of **their**
     * pathway (ash for what has none) bursts and is gone, with a sound of breaking crystal.
     */
    private fun shatter(context: CastContext, target: LivingEntity) {
        val color = PathwayBranding.liquidOf(context.beyonders.of(target.uuid)?.pathway?.name)
        val centre = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        burst(context.level, centre, maxOf(target.bbWidth.toDouble(), target.bbHeight * 0.5), color, SHATTER_POINTS, SHATTER_SPEED)
        context.level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f)
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
        const val SEAL_LIFT = 0.6
        const val SEAL_POINTS = 14
        const val SEAL_RADIUS = 0.4
        const val SEAL_RAYS = 8
        const val SEAL_RAY_RADIUS = 0.85
        const val SHATTER_POINTS = 36
        const val SHATTER_SPEED = 0.25
        const val TAU = 2.0 * Math.PI
    }
}
