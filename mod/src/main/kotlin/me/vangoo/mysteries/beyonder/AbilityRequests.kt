package me.vangoo.mysteries.beyonder

import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastMessages
import me.vangoo.mysteries.ability.CastReport
import me.vangoo.mysteries.ability.runtime.AbilityServices
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Єдина точка входу в каст ззовні: команда й клавіша приходять сюди обидві.
 *
 * `AbilityExecutor` знає **порядок** одного касту; тут — усе, що йому передує й слідує
 * ззовні: знайти здібність у списку гравця, звірити, що вона взагалі активна, і
 * штовхнути клієнту оновлений стан. Дві точки входу, що роблять це кожна по-своєму, —
 * рівно та помилка, з якої в плагіні виросли два різні шляхи списання ресурсів.
 */
object AbilityRequests {

    /** Кастує обрану клавішею здібність. Нічого не обрано — каже про це. */
    fun castSelected(player: ServerPlayer) {
        val selected = AbilityServices.selection.of(player.uuid)
        if (selected == null) {
            AbilityServices.messaging.actionBar(
                player,
                Component.literal("Здібність не обрано: /mysteries select <здібність>"),
            )
            return
        }
        cast(player, selected)
    }

    fun cast(player: ServerPlayer, identity: AbilityIdentity): CastReport {
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return report(player, CastReport.NotBeyonder)

        val spec = beyonder.abilityBy(identity)
            ?: return report(player, CastReport.Refused(CastRefusal.NotOwned))
        val ability = spec as? ActiveAbility
            ?: return report(player, CastReport.Refused(CastRefusal.NotCastable))

        val result = AbilityServices.executor.cast(player, ability)
        // Дія гравця штовхає стан негайно: після касту бар має впасти в той самий кадр,
        // а не за 900 мс до наступного секундного такту.
        //
        // Знімок статусу (`BeyonderStatus.push`) сюди **не** йде, і це не економія:
        // клієнт на цей пакет **відкриває екран**, тож кожен каст розгортав гравцеві
        // меню здібностей просто в бою. Знімок належить явному запиту — команді
        // `/mysteries status` і утриманню клавіші, — а не побічному ефекту удару.
        AbilityServices.statePump.pushNow(player.uuid)
        return result
    }

    /** Обирає здібність під клавішу. `false` — такої в гравця немає. */
    fun select(player: ServerPlayer, identity: AbilityIdentity): Boolean {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return false
        val spec = beyonder.abilityBy(identity) ?: return false
        AbilityServices.selection.select(player.uuid, spec.identity)
        AbilityServices.statePump.pushNow(player.uuid)
        return true
    }

    private fun report(player: ServerPlayer, report: CastReport): CastReport {
        CastMessages.of(report)?.let { AbilityServices.messaging.actionBar(player, it) }
        return report
    }
}
