package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.context.Beyonders
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The passives' tick — the descendant of the plugin's `PassiveAbilityScheduler` plus
 * `PassiveAbilityManager` (490 lines between them).
 *
 * It shrank through three decisions:
 *
 * 1. **The period is declared, not counted.** The plugin called `tick()` on every passive every
 *    tick, and one that only needed a second's cadence kept a `runs` field of its own.
 *    Now it is [Ticking.tickPeriodTicks] — a number, not state that survives a relog.
 * 2. **The context is not cached.** The plugin kept a `UUID → IAbilityContext` map, because a
 *    context was expensive and carried a `Player`. This one is eight references and is built
 *    every tick; in exchange it cannot hand out a `ServerPlayer` who is no longer that player
 *    after a respawn.
 * 3. **There is no ability registry.** The plugin kept a `Map<UUID, Set<AbilityIdentity>>` and a
 *    `Map<UUID, Map<Class<?>, PermanentPassiveAbility>>` in parallel with the Beyonder itself;
 *    the ability list already lies in `Beyonder` and is read from there.
 *    Exactly two records of state remain: **what is switched on** and **what is already activated**.
 *
 * Ever since a passive learned to subscribe to events (phase 06), the lifecycle here manages
 * those too: **switching on removes this ability's previous subscriptions, switching off removes
 * all of them**. A passive that subscribed in `onActivate` will not double up after death and
 * respawn, and it need write nothing for that — exactly as `SessionHost` cancels the previous
 * session on a recast.
 */
class PassiveTicker(
    private val beyonders: Beyonders,
    private val contexts: AbilityContexts,
    private val events: EventHub,
    private val players: (UUID) -> ServerPlayer?,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    /** The toggles the player switched on. An empty set is not stored. */
    private val enabled = HashMap<UUID, MutableSet<AbilityIdentity>>()

    /**
     * Permanent passives that have already been told `onActivate`. **Instances** are held, not
     * identities: when an ability disappears from the Beyonder (demotion, pathway change), there
     * is nobody left to tell `onDeactivate` unless the ability itself is held.
     */
    private val activated = HashMap<UUID, MutableMap<AbilityIdentity, PermanentPassiveAbility>>()

    // ── Tick ────────────────────────────────────────────────────────────────────

    fun tick(tickCount: Long) {
        for (beyonder in beyonders.online.toList()) {
            val player = players(beyonder.playerId) ?: continue
            val on = enabled[beyonder.playerId]

            for (spec in beyonder.abilities) {
                val ticking = spec as? Ticking ?: continue
                if (spec is ToggleablePassiveAbility && on?.contains(spec.identity) != true) continue
                if (tickCount % ticking.tickPeriodTicks.coerceAtLeast(1) != 0L) continue

                guard("tick of ${spec.identity} for ${beyonder.playerId}") {
                    ticking.tick(contexts.of(player, beyonder, spec))
                }
            }
        }
    }

    // ── Toggles ─────────────────────────────────────────────────────────────────

    fun isEnabled(owner: UUID, ability: AbilityIdentity): Boolean =
        enabled[owner]?.contains(ability) == true

    /** Flips a toggle. Returns the new state: `true` — on. */
    fun toggle(player: ServerPlayer, ability: ToggleablePassiveAbility): Boolean {
        val beyonder = beyonders.of(player.uuid) ?: return false
        val context = contexts.of(player, beyonder, ability)
        val on = enabled.getOrPut(player.uuid) { LinkedHashSet() }

        val nowEnabled = if (on.remove(ability.identity)) {
            guard("disable of ${ability.identity}") { ability.onDisable(context) }
            events.forget(player.uuid, ability.identity)
            false
        } else {
            on += ability.identity
            events.forget(player.uuid, ability.identity)
            guard("enable of ${ability.identity}") { ability.onEnable(context) }
            true
        }
        if (on.isEmpty()) enabled.remove(player.uuid)
        return nowEnabled
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    /** Joining a world or gaining new abilities: activates the permanent passives not yet activated. */
    fun activate(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid) ?: return
        val live = activated.getOrPut(player.uuid) { LinkedHashMap() }

        val current = beyonder.abilities.filterIsInstance<PermanentPassiveAbility>()

        // Lost on demotion or a pathway change — say goodbye while the instance is still at hand.
        val lost = live.keys - current.map { it.identity }.toSet()
        for (identity in lost) {
            val ability = live.remove(identity) ?: continue
            guard("deactivate of $identity") {
                ability.onDeactivate(contexts.of(player, beyonder, ability))
            }
            events.forget(player.uuid, identity)
        }

        for (ability in current) {
            if (live.putIfAbsent(ability.identity, ability) != null) continue
            // A clean slate before `onActivate`: activating the same ability a second time
            // (respawn, rejoining a world) must not leave the first subscription behind.
            events.forget(player.uuid, ability.identity)
            guard("activate of ${ability.identity}") {
                ability.onActivate(contexts.of(player, beyonder, ability))
            }
        }
    }

    /** A Sequence change: the ability list is different now, the toggles go out. */
    fun refresh(player: ServerPlayer) {
        disableToggles(player)
        activate(player)
    }

    /** Leaving the world or losing Beyonder status. */
    fun deactivate(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid)
        disableToggles(player)

        val live = activated.remove(player.uuid) ?: return
        if (beyonder == null) return
        for ((identity, ability) in live) {
            guard("deactivate of $identity") {
                ability.onDeactivate(contexts.of(player, beyonder, ability))
            }
            events.forget(player.uuid, identity)
        }
    }

    /** The player is no longer on the server — there is nothing to ask them, simply forget. */
    fun forget(owner: UUID) {
        enabled.remove(owner)
        activated.remove(owner)
    }

    private fun disableToggles(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid) ?: return
        val on = enabled.remove(player.uuid) ?: return
        beyonder.abilities.filterIsInstance<ToggleablePassiveAbility>()
            .filter { it.identity in on }
            .forEach { ability ->
                guard("disable of ${ability.identity}") {
                    ability.onDisable(contexts.of(player, beyonder, ability))
                }
                events.forget(player.uuid, ability.identity)
            }
    }

    /**
     * One fallen passive must not stop the rest from ticking. In the plugin this very invariant
     * was held by three separate `try/catch` blocks in `PassiveAbilityManager`'s loops.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            onError("Passive threw: $what", error)
        }
    }
}
