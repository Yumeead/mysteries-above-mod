package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import kotlin.math.min

/**
 * WhiteTower, Seq. 8 (Student): **Deduction**.
 *
 * The Student does not hit harder — they learn fast. A marked target glows, and every next blow
 * of yours against it lands more precisely than the last: the first as usual, the fifth into the
 * weak spot you have already worked out.
 *
 * ## Why not a flat +28 %, as in the plugin
 *
 * The plugin's mark multiplied damage by a constant — i.e. it was just an expensive 8-second
 * buff. Deduction does not work that way: a conclusion does not arrive ready-made, it is
 * **assembled from observations**. So the bonus grows by [BASE_STEP_PERCENT] % per hit and caps
 * at [MAX_STACKS] — and whoever landed all five blows on one target gets more than the plugin's
 * number, while whoever poked once and ran gets less. The mechanic now matches what it depicts.
 *
 * ## The mark glows, and that is deliberately two-sided
 *
 * `setGlowingTag` draws an outline through walls — for **everyone**, not just the caster. In the
 * plugin this was absent entirely: the victim learned they were being analysed only from starting
 * to die faster. Here the marked one sees they are being studied and has something to answer
 * with. The price is one vanilla call.
 */
class Deduction : ActiveAbility(AbilityIdentity.of("whitetower_deduction")) {

    override val displayName: Component = Component.literal("Дедукція")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви прораховуєте ціль, і кожне наступне влучання лягає точніше за попереднє.\n" +
            "▪ Мітка: ${markTicks(sequence) / TICKS_PER_SECOND} с, світиться крізь стіни\n" +
            "▪ Шкода: +${stepPercent(sequence)}% за влучання, до +${stepPercent(sequence) * MAX_STACKS}%\n" +
            "▪ Потрібна жива ціль у межах ${RANGE.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Нема кого розбирати: наведіть погляд на ціль")

        val ticks = markTicks(context.sequence)
        val step = stepPercent(context.sequence)
        val casterId = context.caster.uuid
        val targetId = target.uuid
        val level = context.level
        // The service goes into the handler, not the context: the context carries the caster's
        // identity and lives one call, while `Messaging` holds nobody's identity and lives as long
        // as the server.
        val messaging = context.messaging

        var hits = 0
        val mark = context.events.on<IncomingDamage>(durationTicks = ticks) { damage ->
            if (damage.victim.uuid != targetId) return@on
            if (damage.attacker?.uuid != casterId) return@on

            hits = min(hits + 1, MAX_STACKS)
            damage.scale(1.0 + step * hits / 100.0)
            deduce(level, damage.victim)
            messaging.actionBar(casterId, Component.literal("Розбір: +${step * hits}%"))
        }

        // The session here is not about ticking but about cleanup: a recast on a new target must
        // put out the old mark together with its subscription, and that is exactly what the
        // session registry does by itself.
        target.setGlowingTag(true)
        context.session {
            try {
                waitTicks(ticks)
            } finally {
                mark.cancel()
                (level.getEntityInAnyDimension(targetId) as? LivingEntity)?.setGlowingTag(false)
            }
        }

        study(context, target)
        context.messaging.actionBar(targetId, Component.literal("Вас вивчають"))
        return AbilityOutcome.done("Ви взялись за розбір: ${target.name.string}")
    }

    /** The moment of the cast: the gaze settled on the target. The aura holds exactly as long as the mark. */
    private fun study(context: CastContext, target: LivingEntity) {
        context.effects.bodyAura(target, markTicks(context.sequence))
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f,
        )
    }

    /** The conclusion was confirmed — the hit landed where it had been worked out. */
    private fun deduce(level: ServerLevel, target: LivingEntity) {
        level.sendParticles(
            ParticleTypes.ENCHANT,
            target.x, target.y + target.bbHeight * 0.6, target.z,
            8, 0.25, 0.35, 0.25, 0.4,
        )
    }

    private fun markTicks(sequence: Sequence): Int =
        scale(BASE_MARK_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE) * TICKS_PER_SECOND

    private fun stepPercent(sequence: Sequence): Int =
        scale(BASE_STEP_PERCENT, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val RANGE = 20.0
        const val COOLDOWN_SECONDS = 30
        const val BASE_MARK_SECONDS = 8
        /** +10 % per hit at Seq. 9; five hits is half the damage again on top. */
        const val BASE_STEP_PERCENT = 10
        const val MAX_STACKS = 5
    }
}
