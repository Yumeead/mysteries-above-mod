package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Посл. 8 (Прохач Світла): **Нічне бачення**.
 *
 * Два мініатюрних сонця в очах: темрява для Прохача не існує — ні звичайна, ні та,
 * яку насилають.
 *
 * ## Імунітет до `DARKNESS` — це і є суть
 *
 * Саме нічне бачення дає будь-яке зілля. Чого зілля не дає — це стійкості до ванільної
 * `DARKNESS` (сліпучі спалахи Стража): вона накладається поверх і гасить екран навіть
 * тому, хто бачить у темряві. Такт знімає її щосекунди, і **це** відрізняє силу від
 * пляшки.
 */
class NightVision : ActiveAbility(AbilityIdentity.of("sun_night_vision")) {

    override val displayName: Component = Component.literal("Нічне бачення")

    /** Каст безкоштовний: ціна — утримання, і гасити очі має бути дарма. */
    override val spiritualityCost: Int = 0

    /**
     * Ціна утримання росте з Послідовністю — і мусить, бо разом із нею росте
     * **регенерація**: 2 очки/с на Посл. 8 проти 6 на Посл. 5 (і 9 при повному
     * засвоєнні). Пласка ціна, взята з плагіна, на Посл. 5 виявилась нижчою за
     * відновлення, тобто сила ставала безкоштовною, а смуга духовності повзла назад
     * до стелі просто під час утримання.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Два мініатюрних сонця у ваших очах.\n" +
            "▪ Нічне бачення, поки тримаєте\n" +
            "▪ Темрява вас не засліплює — навіть наслана\n" +
            "▪ Утримання: ${periodicCost(sequence)} духовності/с; повторний каст гасить",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Сонця в очах згасли")
        }

        val messaging = context.messaging

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { priest ->
                    priest.addEffect(
                        MobEffectInstance(MobEffects.NIGHT_VISION, VISION_TICKS, 0, true, false, true),
                    )
                    priest.removeEffect(MobEffects.DARKNESS)
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Сонця згасли: духовність скінчилась"))
                }
            } finally {
                owner?.removeEffect(MobEffects.NIGHT_VISION)
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 2.0f,
        )
        return AbilityOutcome.done("Темрява для вас скінчилась")
    }

    private companion object {
        const val BASE_PERIODIC_COST = 9
        const val COOLDOWN_SECONDS = 3
        /**
         * Понад двадцять секунд — щоб клієнт не блимав затемненням: ванілька починає
         * мерехтіти нічним баченням на останніх десяти секундах його тривалості.
         */
        const val VISION_TICKS = 25 * TICKS_PER_SECOND
    }
}
