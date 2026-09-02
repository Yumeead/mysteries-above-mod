package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.CastAuthorization
import me.vangoo.domain.abilities.CastReceipt
import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.abilities.CastTicket
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.SanityPenalty
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.runtime.AbilityContexts
import me.vangoo.mysteries.event.AbilityCast
import me.vangoo.mysteries.event.GameEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory

/**
 * Порядок одного касту — від натискання до розрахунку.
 *
 * У плагіні цей порядок був розмазаний по трьох місцях: `Beyonder.useAbility` знімав
 * ресурси, `Ability.execute` тримав хуки й кулдаун, `AbilityResourceConsumer` робив те
 * саме ще раз для відкладених здібностей — і трохи інакше. Тут він один, і різниця
 * між звичайним і відкладеним кастом зводиться до того, хто гасить квиток.
 */
class AbilityExecutor(
    private val beyonders: Beyonders,
    private val cooldowns: Cooldowns,
    private val messaging: Messaging,
    private val contexts: AbilityContexts,
    /**
     * Куди повідомити, що сила спрацювала. Шина мода — вона ж, у яку їде шкода з міксину:
     * каст помітний збоку так само, як удар, і здібностям, що його читають, байдуже,
     * звідки подія прийшла.
     */
    private val publish: (GameEvent) -> Unit = {},
) {

    private val log = LoggerFactory.getLogger(MOD_ID)

    fun cast(caster: ServerPlayer, ability: ActiveAbility): CastReport {
        val beyonder = beyonders.of(caster.uuid) ?: return report(caster, CastReport.NotBeyonder)

        val remaining = cooldowns.remainingSeconds(caster.uuid, ability.identity)
        if (remaining > 0) return report(caster, CastReport.OnCooldown(remaining))

        val ticket = when (val authorization = beyonder.authorize(ability)) {
            is CastAuthorization.Refused -> return report(caster, CastReport.Refused(authorization.reason))
            is CastAuthorization.Granted -> authorization.ticket
        }

        val context = contexts.forCast(
            caster = caster,
            beyonder = beyonder,
            ability = ability,
            cast = PendingCast(this, caster, beyonder, ticket),
        )

        // Далі керування йде в код здібності. Виняток звідти інакше просто спливе:
        // Fabric його залогує й перекине, а `BlockableEventLoop.doRunTask` проковтне —
        // тобто квиток лишиться непогашеним, і зламана здібність стане безкоштовною
        // й нескінченною. Решта рантайму (`TickScheduler`, `SessionHost`, `EventHub`)
        // чужий код уже ізолює; це було єдине місце, де ні.
        val outcome = try {
            if (!ability.canCast(context)) {
                // Квиток просто зникає невитраченим — гравець нічого не заплатив.
                return report(caster, CastReport.Refused(CastRefusal.NotCastable))
            }

            contest(context, ability)?.let { chance ->
                // Опір з'їдає кулдаун, але не духовність: спроба була, ефекту не було.
                cooldowns.set(caster.uuid, ability.identity, ability.cooldownSeconds(beyonder.sequence))
                return report(caster, CastReport.Resisted(chance))
            }

            ability.cast(context)
        } catch (error: Exception) {
            log.error("Здібність {} впала під час касту", ability.identity, error)
            // Той самий розрахунок, що й при опорі: кулдаун списано, духовність — ні.
            // Без кулдауна гравець тримав би клавішу, а лог набирав би по рядку на тік.
            cooldowns.set(caster.uuid, ability.identity, ability.cooldownSeconds(beyonder.sequence))
            AbilityOutcome.failed("Сила зірвалась")
        }
        val receipt = when (outcome) {
            is AbilityOutcome.Done -> if (context.cast.isComplete) null else context.cast.complete()
            // Квиток лишився в здібності: вона погасить його, коли ефект справді станеться.
            is AbilityOutcome.Awaiting -> null
            is AbilityOutcome.Failed -> null
        }
        // Зірваний каст збоку не видно ніяк — ні спалаху, ні звуку, тож і події немає.
        if (outcome !is AbilityOutcome.Failed) publish(AbilityCast(caster, ability))
        return report(caster, CastReport.Performed(outcome, receipt))
    }

    /**
     * Погашення квитка. Викликається або звідси одразу після [AbilityOutcome.Done],
     * або пізніше самою здібністю через [PendingCast.complete].
     */
    internal fun settle(caster: ServerPlayer, beyonder: Beyonder, ticket: CastTicket): CastReceipt {
        val receipt = beyonder.settle(ticket)
        if (receipt.cooldownSeconds > 0) {
            cooldowns.set(beyonder.playerId, ticket.spec.identity, receipt.cooldownSeconds)
        }
        applyPenalty(caster, receipt.penalty)
        return receipt
    }

    /**
     * Втрату духовності `core` вже зняв сам — тут лишаються ефекти у світі. `EXTREME`
     * поки що б'є на смерть; повний ритуал шаленства — фаза, що принесе Rampage.
     */
    private fun applyPenalty(caster: ServerPlayer, penalty: SanityPenalty) {
        when (penalty.type) {
            SanityPenalty.PenaltyType.DAMAGE ->
                caster.hurtServer(caster.level(), sanityDamage(caster), penalty.amount.toFloat())

            SanityPenalty.PenaltyType.EXTREME ->
                caster.hurtServer(caster.level(), sanityDamage(caster), Float.MAX_VALUE)

            SanityPenalty.PenaltyType.SPIRITUALITY_LOSS -> Unit
            SanityPenalty.PenaltyType.NONE -> Unit
        }
    }

    private fun sanityDamage(caster: ServerPlayer): DamageSource =
        caster.damageSources().magic()

    /**
     * Кидок опору за Послідовністю. Опирається лише інший Потойбічний: звичайна
     * істота нічого не протиставляє. Повертає шанс, якщо ціль устояла.
     */
    private fun contest(
        context: CastContext,
        ability: ActiveAbility,
    ): SequenceBasedSuccessChance? {
        val target: LivingEntity = ability.contestedTarget(context) ?: return null
        val defender = beyonders.of(target.uuid) ?: return null

        val chance = SequenceBasedSuccessChance(
            casterSequence = context.beyonder.sequenceLevel,
            targetSequence = defender.sequenceLevel,
        )
        return if (chance.rollSuccess()) null else chance
    }

    private fun report(caster: ServerPlayer, report: CastReport): CastReport {
        CastMessages.of(report)?.let { messaging.actionBar(caster, it) }
        return report
    }
}
