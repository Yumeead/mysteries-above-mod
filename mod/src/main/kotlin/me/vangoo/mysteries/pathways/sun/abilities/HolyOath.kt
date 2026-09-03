package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Seq. 7 (Solar High Priest): **Holy Oath**.
 *
 * The High Priest swears an oath — and while it holds, they strike harder, move faster, and every
 * blow of theirs sets the target alight.
 *
 * ## The oath has a price, and it is not spirituality
 *
 * The plugin's version was simply a fifteen-second buff. An oath does not sound like that: a
 * promise with nothing following from it is not a promise. So along with the power it applies
 * [MobEffects.GLOWING]: while the oath holds, the High Priest is **visible to everyone and
 * through walls**. There is no hiding with it — neither from the one sworn to nor from the rest.
 *
 * Mechanically it is the same boost, but you cannot lie in the bushes with it: a power that makes
 * you stronger makes you more noticeable too.
 */
class HolyOath : ActiveAbility(AbilityIdentity.of("sun_holy_oath")) {

    override val displayName: Component = Component.literal("Свята клятва")

    override val spiritualityCost: Int = 90

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Клятва на ${duration(sequence)} с: Сила, Швидкість, і кожен ваш удар підпалює " +
            "на ${FIRE_SECONDS} с.\n" +
            "▪ Поки клятва тримається, вас видно крізь стіни — і це частина ціни\n" +
            "▪ Порушити не вийде: клятва спливає сама",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val seconds = duration(context.sequence)
        val ticks = seconds * TICKS_PER_SECOND
        val caster = context.caster
        val casterId = caster.uuid

        caster.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, 0, false, true, true))
        caster.addEffect(MobEffectInstance(MobEffects.SPEED, ticks, 0, false, true, true))
        // The visibility is the price, so the effect is shown: the player must see what is on them.
        caster.addEffect(MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, true, true))

        context.events.on<IncomingDamage>(durationTicks = ticks) { damage ->
            if (damage.attacker?.uuid != casterId) return@on
            if (damage.directCause !== damage.attacker) return@on
            damage.victim.igniteForSeconds(FIRE_SECONDS.toFloat())
        }

        context.effects.bodyAura(caster, ticks)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.9f,
        )
        return AbilityOutcome.done("Клятву дано: $seconds с")
    }

    private fun duration(sequence: Sequence): Int =
        scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DURATION_SECONDS = 15
        const val COOLDOWN_SECONDS = 35
        const val FIRE_SECONDS = 2
    }
}
