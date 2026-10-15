package me.vangoo.mysteries.beyonder

import me.vangoo.domain.abilities.CastRefusal
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastMessages
import me.vangoo.mysteries.ability.CastReport
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
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
                Component.literal("Здібність не обрано — утримайте клавішу касту, щоб обрати"),
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

        // A toggle has no ticket to settle and no cooldown to charge — it flips a switch and the
        // ability itself announces the new state (`onEnable`/`onDisable`), the same way a cast
        // words its own outcome.
        if (spec is ToggleablePassiveAbility) {
            val nowEnabled = AbilityServices.passives.toggle(player, spec)
            AbilityServices.statePump.pushNow(player.uuid)
            return CastReport.Performed(AbilityOutcome.done(if (nowEnabled) "Увімкнено" else "Вимкнено"), null)
        }

        val ability = spec as? ActiveAbility
            ?: return report(player, CastReport.Refused(CastRefusal.NotCastable))

        val result = AbilityServices.executor.cast(player, ability)
        // A player action pushes the state immediately: after a cast the bar must drop in the
        // same frame, not 900 ms before the next one-second tick.
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

    /**
     * One `ABILITY_ACTION` from the client. The identity is foreign input: a blank one is
     * dropped here rather than tripping `AbilityIdentity`'s `require` inside the network handler.
     */
    fun act(player: ServerPlayer, payload: AbilityActionPayload) {
        val identity = payload.identity?.takeIf { it.isNotBlank() }?.let(AbilityIdentity::of)
        val mode = payload.mode?.takeIf { it.isNotBlank() }
        // A running power that took the new mode on the spot has nothing left to cast — but a
        // pick from the wheel still makes it the selected one.
        if (identity != null && mode != null && pickMode(player, identity, mode) && payload.action != Action.SELECT) return
        when (payload.action) {
            Action.SELECT -> identity?.let { select(player, it) }
            Action.CAST -> if (identity == null) castSelected(player) else cast(player, identity)
            // Select first, so a refused cast still leaves the wheel's choice "in hand".
            Action.SELECT_AND_CAST -> identity?.let { if (select(player, it)) cast(player, it) }
            // `cast` already flips a toggle; the distinct verb exists for the client's sake (M5).
            Action.TOGGLE -> identity?.let { cast(player, it) }
            Action.MODE -> Unit
        }
    }

    /**
     * Picks [mode] for the ability. `true` — it was a change, and the power, already running,
     * switched to it live ([Ability.switchMode]); the caller must not cast on top of that.
     * A foreign mode id is dropped here, like a foreign identity.
     */
    private fun pickMode(player: ServerPlayer, identity: AbilityIdentity, mode: String): Boolean {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return false
        val ability = beyonder.abilityBy(identity) as? Ability ?: return false
        val before = AbilityServices.modes.of(player.uuid, ability, beyonder.sequence)?.id
        if (!AbilityServices.modes.select(player.uuid, ability, beyonder.sequence, mode)) return false
        AbilityServices.statePump.pushNow(player.uuid)
        if (before == mode) return false
        return ability.switchMode(AbilityServices.contexts.of(player, beyonder, ability))
    }

    private fun report(player: ServerPlayer, report: CastReport): CastReport {
        CastMessages.of(report)?.let { AbilityServices.messaging.actionBar(player, it) }
        return report
    }
}
