package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Посл. 7 (Сонячний Первосвященник): **Резистентність до жаху**.
 *
 * Святе світло опирається страху: щосекунди зрізає по дві секунди з тривалості
 * ментальних дебафів, прискорюючи їхній сплив. Миттєво не знімає нічого —
 * повільність теж не захищена, вона тут вважається фізичним ефектом, не ментальним.
 *
 * Такт тут доречний, на відміну від `IllusoryScales`: дебаф спливає **сам по собі**, і
 * події, на яку можна було б почекати, не існує взагалі. Саме тому `Ticking` тепер
 * дописується явно — пасивка оголошує, що їй потрібен годинник.
 */
class ResistanceToHorror :
    PermanentPassiveAbility(AbilityIdentity.of("sun_resistance_to_horror")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Резистентність до жаху")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Святе світло опирається страху: щосекунди зрізає по " +
            "${REDUCTION_TICKS / TICKS_PER_SECOND} секунди з тривалості дебафів " +
            "(Нудота, Сліпота, Слабкість, Темрява), прискорюючи їхній сплив.",
    )

    override fun tick(context: AbilityContext) {
        FEAR_EFFECTS.forEach { effect -> shorten(context, effect) }
    }

    /**
     * Скорочує один дебаф на [REDUCTION_TICKS].
     *
     * `addEffect` тут не годиться: ванільний `update` бере сильніше з двох, тож коротшу
     * тривалість він просто проігнорує. `forceAddEffect` кладе екземпляр на місце
     * попереднього і сам сповіщає клієнт — інакше іконка показувала б стару тривалість.
     */
    private fun shorten(context: AbilityContext, effect: Holder<MobEffect>) {
        val player = context.caster
        val active = player.getEffect(effect) ?: return
        if (active.isInfiniteDuration) return

        val remaining = active.duration - REDUCTION_TICKS
        if (remaining <= 0) {
            player.removeEffect(effect)
            burnOff(context, player)
            return
        }

        player.forceAddEffect(
            MobEffectInstance(
                effect,
                remaining,
                active.amplifier,
                active.isAmbient,
                active.isVisible,
                active.showIcon(),
            ),
            null,
        )
    }

    /** Дебаф догорів — світло видно. Поки він лише коротшає, пасивка мовчить. */
    private fun burnOff(context: AbilityContext, player: ServerPlayer) {
        context.level.sendParticles(
            ParticleTypes.GLOW,
            player.x, player.y + player.bbHeight * 0.75, player.z,
            8, 0.3, 0.3, 0.3, 0.01,
        )
    }

    private companion object {
        /** −2 с тривалості дебафу за спрацювання. */
        const val REDUCTION_TICKS = 2 * TICKS_PER_SECOND

        val FEAR_EFFECTS: List<Holder<MobEffect>> = listOf(
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.WEAKNESS,
            MobEffects.DARKNESS,
        )
    }
}
