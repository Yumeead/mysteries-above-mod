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
 * The single entry point into a cast from outside: the command and the key both arrive here.
 *
 * `AbilityExecutor` knows the **order** of one cast; here is everything that precedes and
 * follows it from outside: find the ability in the player's list, check that it is active at
 * all, and push the updated state to the client. Two entry points doing this each in its own
 * way is exactly the mistake the plugin's two different resource-spending paths grew from.
 */
object AbilityRequests {

    /** Casts the ability selected with the key. If nothing is selected, says so. */
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
        // A player action pushes the state immediately: after a cast the bar must drop in the
        // same frame, not 900 ms before the next one-second tick.
        //
        // The status snapshot (`BeyonderStatus.push`) does **not** go here, and this is not a
        // saving: the client **opens a screen** on that packet, so every cast used to unfold
        // the ability menu right in the middle of a fight. The snapshot belongs to an explicit
        // request — the `/mysteries status` command and holding the key — not to a side effect
        // of a blow.
        AbilityServices.statePump.pushNow(player.uuid)
        return result
    }

    /** Selects the ability for the key. `false` — the player does not have it. */
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
