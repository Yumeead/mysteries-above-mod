package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.phys.Vec3

/**
 * Error, Seq. 8 (Swindler): **Eloquence**.
 *
 * A few phrases and the target no longer sees an enemy in you. Zero damage: they simply cannot
 * reach you, and a creature turns on the nearest monster instead. Trust ends the instant you
 * strike first — a swindler loses it the moment they raise a hand.
 *
 * ## Three subscriptions instead of a session
 *
 * Everything the power does lives in the events themselves, so there is nothing to keep ticking:
 * a subscription with a term expires on its own, and the "you struck first" one cancels the other
 * two. The plugin needed its own subscription key for exactly this and warned about it in a
 * comment — here the key is the caster+ability pair the context was issued for, and reaching
 * another ability's subscriptions is not expressible.
 *
 * ## Why the mob is retargeted through the event, not by a repeating task
 *
 * `setTarget` on the mob only redirects it once; a second later its own targeting goal picks the
 * caster again. The plugin answered that by re-pointing every mob back every ten ticks. Here
 * [MobTargeting] denies the reacquisition at its source, and nothing is scheduled at all.
 */
class Eloquence : ActiveAbility(AbilityIdentity.of("error_eloquence")) {

    override val displayName: Component = Component.literal("Красномовність")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви заговорюєте ціль, і вона більше не бачить у вас ворога.\n" +
            "▪ Дальність ${RANGE.toInt()} бл., триває ${durationTicks(sequence) / TICKS_PER_SECOND} с\n" +
            "▪ Ціль не може вас ударити; створіння б'ється за вас із найближчим монстром\n" +
            "▪ Ударите першим — слова більше не тримають\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть погляд на живу ціль")

        val caster = context.caster
        val duration = durationTicks(context.sequence)
        val held = ArrayList<Subscription>(3)

        // 1. The target cannot reach the caster.
        held += context.events.on<IncomingDamage>(duration) { damage ->
            if (damage.victim === caster && damage.attacker === target) damage.cancel()
        }

        // 2. The caster struck — trust is gone along with every subscription.
        held += context.events.on<IncomingDamage>(duration) { damage ->
            if (damage.attacker !== caster) return@on
            held.forEach(Subscription::cancel)
            context.messaging.actionBar(caster, Component.literal("Ваші слова більше не тримають"))
        }

        if (target is Mob) {
            // 3. The creature does not take the caster again, and fights someone else meanwhile.
            held += context.events.on<MobTargeting>(duration) { event ->
                if (event.mob === target && event.target === caster) event.deny()
            }
            target.target = nearestMonster(context, target)
        } else if (target is ServerPlayer) {
            context.messaging.actionBar(target, Component.literal("Слова обеззброюють вас…"))
            context.level.playSound(
                null, target.x, target.y, target.z,
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 0.8f,
            )
        }

        speak(context, target)
        return AbilityOutcome.done("Ви заговорили ${target.name.string}")
    }

    /** Whoever the talked-down creature will take out its aggression on instead. */
    private fun nearestMonster(context: CastContext, except: LivingEntity): LivingEntity? =
        context.targeting.livingNear(RANGE)
            .filter { it is Monster && it !== except }
            .minByOrNull { it.distanceToSqr(except) }

    /** Words travel: a thread of motes from the caster's mouth to the target's head. */
    private fun speak(context: CastContext, target: LivingEntity) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        thread(context.level, context.eye, target.eyePosition, mote)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.3f,
        )
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 0.9f,
        )
    }

    private fun thread(level: ServerLevel, from: Vec3, to: Vec3, mote: ParticleOptions) {
        val steps = (from.distanceTo(to) / THREAD_STEP).toInt().coerceIn(1, MAX_THREAD_STEPS)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            level.sendParticles(mote, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val RANGE = 12.0
        const val COOLDOWN_SECONDS = 45
        const val BASE_DURATION_TICKS = 8 * TICKS_PER_SECOND
        const val THREAD_STEP = 0.4
        const val MAX_THREAD_STEPS = 40
    }
}
