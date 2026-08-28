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
 * WhiteTower, Посл. 8 (Студент): **Дедукція**.
 *
 * Студент не б'є сильніше — він швидко вчиться. Позначена ціль світиться, і кожен
 * наступний ваш удар по ній влучає точніше за попередній: перший — як завжди, п'ятий —
 * у слабке місце, яке ви вже прорахували.
 *
 * ## Чому не пласкі +28 %, як у плагіні
 *
 * Плагінна мітка множила шкоду на сталу — тобто була просто дорогим бафом на 8 секунд.
 * Дедукція так не працює: висновок не приходить готовим, він **збирається зі спостережень**.
 * Тому бонус наростає по [BASE_STEP_PERCENT] % за влучання й тримається на
 * [MAX_STACKS] — і той, хто провів усі п'ять ударів по одній цілі, отримує більше за
 * плагінне число, а той, хто ткнув один раз і побіг, — менше. Механіка тепер збігається
 * з тим, що вона зображає.
 *
 * ## Мітка світиться, і це навмисно двосторонньо
 *
 * `setGlowingTag` малює контур крізь стіни — **всім**, не лише кастеру. У плагіні цього
 * не було зовсім: жертва дізнавалась про розбір хіба з того, що почала швидше вмирати.
 * Тут позначений бачить, що його вивчають, і має чим на це відповісти. Ціна питання —
 * один ванільний виклик.
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

        var hits = 0
        val mark = context.events.on<IncomingDamage>(durationTicks = ticks) { damage ->
            if (damage.victim.uuid != targetId) return@on
            if (damage.attacker?.uuid != casterId) return@on

            hits = min(hits + 1, MAX_STACKS)
            damage.scale(1.0 + step * hits / 100.0)
            deduce(level, damage.victim)
            level.server.playerList.getPlayer(casterId)
                ?.sendSystemMessage(Component.literal("Розбір: +${step * hits}%"), true)
        }

        // Сесія тут не за тактом, а за прибиранням: перекаст по новій цілі мусить
        // погасити стару мітку разом із її підпискою, і саме це реєстр сесій робить сам.
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

    /** Мить касту: погляд ліг на ціль. Покрив тримається рівно стільки, скільки мітка. */
    private fun study(context: CastContext, target: LivingEntity) {
        context.effects.bodyAura(target, markTicks(context.sequence))
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f,
        )
    }

    /** Висновок підтвердився — влучання лягло туди, куди прораховано. */
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
        /** +10 % за влучання на Посл. 9; п'ять влучань — половина шкоди зверху. */
        const val BASE_STEP_PERCENT = 10
        const val MAX_STACKS = 5
    }
}
