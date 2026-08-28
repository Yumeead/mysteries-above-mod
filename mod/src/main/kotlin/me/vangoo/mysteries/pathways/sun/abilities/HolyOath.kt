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
 * Sun, Посл. 7 (Сонячний Первосвященник): **Свята клятва**.
 *
 * Первосвященник дає клятву — і поки вона тримається, він б'є сильніше, рухається
 * швидше, а кожен його удар підпалює.
 *
 * ## Клятва має ціну, і це не духовність
 *
 * Плагінна версія була просто бафом на п'ятнадцять секунд. Клятва так не звучить:
 * обіцянка, з якої нічого не випливає, — не обіцянка. Тому разом із силою вона накладає
 * [MobEffects.GLOWING]: доки клятва тримається, Первосвященника **видно всім і крізь
 * стіни**. Сховатись із нею не вийде — ні від того, кому клявся, ні від решти.
 *
 * Механічно це той самий буст, але з ним не можна засідати в кущах: сила, що робить
 * тебе сильнішим, робить тебе й помітнішим.
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
        // Видимість — це ціна, тож ефект показуємо: гравець мусить бачити, що з ним.
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
