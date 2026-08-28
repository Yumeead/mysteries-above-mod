package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Посл. 8 (Прохач Світла): **Виявлення зла**.
 *
 * Скверна поруч світиться крізь стіни. Пасивка не питає дозволу й не має вимикача:
 * Прохач бачить те, що бачить.
 *
 * ## Контур — ванільний ефект, а не власна книга обліку
 *
 * Найпряміший спосіб — `setGlowingTag(true)`, але він **не має строку**: увімкнувши
 * контур, довелось би пам'ятати, кому саме, щоб погасити його, коли ціль вийде з
 * радіуса. А пам'ятати нема де: екземпляр здібності один на весь сервер, тож поле в
 * ньому змішало б цілі всіх Прохачів одразу.
 *
 * Ванільний [MobEffects.GLOWING] цю потребу знімає цілком: він **спливає сам**. Такт
 * поновлює його на [GLOW_TICKS], тобто трохи довше за свій період, і ціль, що вийшла
 * з радіуса, гасне за півтори секунди без жодного рядка обліку.
 *
 * ## Ціна прозорості
 *
 * Контур бачать усі, не лише Прохач. Це свідомо: світло не показує **вам**, воно
 * **позначає** — і той, кого позначено, має право це помітити. Плагін тут тягнув
 * бібліотеку `glowingentities` заради приватного контуру; мод не тягне нічого.
 */
class EvilDetection :
    PermanentPassiveAbility(AbilityIdentity.of("sun_evil_detection")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Виявлення зла")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Темні шляхи й нежить поруч світяться крізь стіни.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Контур бачать усі — світло позначає, а не підказує вам на вухо",
    )

    override fun tick(context: AbilityContext) {
        context.targeting.livingNear(range(context.sequence))
            .filter { it !== context.caster && it.isProfane(context.beyonders) }
            .forEach { profane ->
                profane.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, false))
            }
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private companion object {
        const val BASE_RANGE = 20
        /** Півтора такти: контур не блимає між скануваннями, але й не переживає вихід з радіуса. */
        const val GLOW_TICKS = 30
    }
}
