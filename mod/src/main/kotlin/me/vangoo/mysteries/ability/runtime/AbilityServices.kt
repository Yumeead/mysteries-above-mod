package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.AbilityExecutor
import me.vangoo.mysteries.beyonder.AbilityStatePump
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.beyonder.TheftRegistry
import net.minecraft.network.chat.Component
import me.vangoo.mysteries.event.AbilityCast
import me.vangoo.mysteries.event.EvidenceLog
import me.vangoo.mysteries.event.GameEvent
import me.vangoo.mysteries.event.GameEventBridge
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.fool.PaperWeaponHits
import me.vangoo.mysteries.pathways.fool.RecentDamageLedger
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TickScheduler
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * The services abilities see through `AbilityContext`, gathered in one place.
 *
 * Not the plugin's `ServiceContainer`: there a DI container assembled ~40 services and decided
 * the order itself. Here there are exactly as many objects as exist, and each new phase adds its
 * own (effects — 07). When there are too many for one file, that will be the signal to split —
 * not earlier.
 *
 * Tied to the server's life: the integrated server of a single-player game comes up and goes
 * down inside the same process, so state that survived a shutdown would arrive in the next
 * world. [reset] does not allow that.
 */
object AbilityServices {

    private val log = LoggerFactory.getLogger(MOD_ID)

    private var server: MinecraftServer? = null

    private val onError: (String, Throwable) -> Unit = { what, error -> log.error(what, error) }

    private val players: (UUID) -> ServerPlayer? = { id -> server?.playerList?.getPlayer(id) }

    // Toggles live in `passives`, and that is built later — the lambda body reads it at call
    // time, i.e. long after the object is initialised.
    val beyonders: BeyonderRegistry = BeyonderRegistry { id, ability -> passives.isEnabled(id, ability) }

    /** The whole mod's clock: its own counter, ticking only together with the server tick. */
    val scheduler = TickScheduler(onError)

    val cooldowns = CooldownTable { server?.tickCount ?: 0 }

    val messaging = ServerMessaging { server }

    val scheduling: SessionHost = SessionHost(scheduler, beyonders, players, onError)

    /**
     * Abilities' subscriptions to game events. The clock is the same as the sessions' — the
     * mod's tick: a temporary subscription does not age while paused, just as a session does not.
     */
    val events = EventHub({ scheduler.tick }, onError)

    /** Which ability the player holds under the key. Until phase 11 this is all the selection there is. */
    val selection = AbilitySelection()

    /** A choice an ability is waiting on, until phase 11's real menus exist. */
    val pendingChoices = PendingChoices()

    val contexts: AbilityContexts = AbilityContexts(beyonders, cooldowns, messaging, scheduling, events, pendingChoices)

    val passives: PassiveTicker = PassiveTicker(beyonders, contexts, events, players, onError)

    val executor: AbilityExecutor = AbilityExecutor(beyonders, cooldowns, messaging, contexts, ::publish)

    /**
     * When to push the ability state to the client. The clock is the same `tickCount`
     * [cooldowns] counts in: otherwise the throttle would measure one thing and `readyAtTick`
     * would mean another.
     */
    val statePump = AbilityStatePump(
        send = { id -> players(id)?.let(BeyonderStatus::pushState) },
        now = { (server?.tickCount ?: 0).toLong() },
    )

    fun bootstrap() {
        ServerLifecycleEvents.SERVER_STARTING.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { reset() }

        // Game → the mod's events. The hook search order and the mixins — see `GameEventBridge`.
        GameEventBridge.install(::publish)

        // Traces of what people do: the Cryptologist reads them, and a Beyonder's own work is
        // one of the kinds, so the record is fed from the same sink as the bus.
        EvidenceLog.install { scheduler.tick }

        // One mod tick: the scheduler first (sessions wake up), then the passives' tick.
        // The order is exactly this so that a passive sees the world after the sessions changed it.
        ServerTickEvents.END_SERVER_TICK.register { s ->
            scheduler.advance()
            passives.tick(scheduler.tick)
            // Spirituality regenerates once a second — the regeneration rates are stated in
            // exactly those units (`SpiritualityCalculator.REGEN_RATES` — points per second).
            if (scheduler.tick % TICKS_PER_SECOND == 0L) {
                regenerate()
                // The one-second sync tick lives right here, because regeneration is the most
                // frequent source of "dirty" players.
                statePump.flush()
            }
            // Once every half minute: otherwise the cooldown table grows by one ability forever.
            if (s.tickCount % PRUNE_PERIOD_TICKS == 0) cooldowns.prune()
            // Theft runs on the wall clock, so it is swept rather than scheduled: a task set for
            // ten minutes ahead would be eaten by a restart, and the stolen power would stay.
            if (s.tickCount % THEFT_SWEEP_TICKS == 0) TheftRegistry.sweep(revoke = ::takeBackStolen)
        }

        // Joining a world: permanent passives will not switch themselves on. Until phase 09 the
        // registry is empty at startup, so this only fires for those the potion filled this session.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            passives.activate(player)
            // The client has no other way of learning the ability list: the roster flies on
            // change, and joining a world is a change from "nothing" to "here is what there is".
            BeyonderStatus.pushRoster(player)
            statePump.pushNow(player.uuid)
        }

        // A player leaving cancels their sessions deterministically. A session would notice the
        // owner is gone by itself, but only on its own tick — and that tick may be once every
        // five seconds.
        // Death cancels sessions immediately. A session would notice by itself too — but again
        // only on its own tick, which may be once every five seconds: all that time the corpse
        // would be paying upkeep.
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) scheduling.stopAll(entity.uuid)
        }

        // A respawn is a **new** `ServerPlayer`, and death has already removed every effect.
        // Passives that put something on the player in `onActivate` would be left with nothing
        // after that, so the bookkeeping is reset and activation runs anew. `deactivate` will not
        // do here: it would mean saying goodbye to a corpse, and there is nothing left to clean up.
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            passives.forget(newPlayer.uuid)
            events.forget(newPlayer.uuid)
            passives.activate(newPlayer)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            passives.deactivate(player)
            passives.forget(player.uuid)
            events.forget(player.uuid)
            scheduling.stopAll(player.uuid)
            selection.forget(player.uuid)
            pendingChoices.forget(player.uuid)
            statePump.forget(player.uuid)
        }
    }

    /**
     * Spirituality regeneration for everyone in the game.
     *
     * The plugin's `BeyonderService.regenerateAll` walked the **repository**, i.e. including
     * those not in the game, and filtered them out inside. Here the registry holds only the
     * living anyway.
     */
    private fun regenerate() {
        beyonders.online.forEach { beyonder ->
            val before = beyonder.spirituality.current
            beyonder.regenerateSpirituality()
            // Mark dirty only when the number actually grew: a player at the spirituality
            // ceiling must generate no packet at all.
            if (beyonder.spirituality.current != before) statePump.markDirty(beyonder.playerId)
        }
    }

    /**
     * The single door into the bus — and the place where a power's own trace is written.
     *
     * The record could have subscribed to [AbilityCast] like any ability, but it is not an
     * ability: it must see every cast, including those nobody is subscribed to.
     */
    private fun publish(event: GameEvent) {
        if (event is AbilityCast) EvidenceLog.record(event)
        if (event is IncomingDamage) {
            RecentDamageLedger.record(event)
            PaperWeaponHits.onDamage(event)
        }
        events.publish(event)
    }

    /**
     * The loan is over: the power leaves the thief, and both sides are told.
     *
     * The victim is not restored to anything — they never lost the ability itself, only the right
     * to call it, and that right returns on its own when the record's suppression window closes.
     */
    private fun takeBackStolen(theft: TheftRegistry.Theft) {
        beyonders.of(theft.thiefId)?.remove(theft.identity)
        messaging.tell(theft.thiefId, Component.literal("Вкрадене розтануло у ваших руках"))
        messaging.tell(theft.victimId, Component.literal("Ваша сила більше не в чужих руках — але ще не ваша"))
    }

    private fun reset() {
        GameEventBridge.reset()
        EvidenceLog.reset()
        RecentDamageLedger.reset()
        TheftRegistry.reset()
        events.clear()
        scheduling.shutdown()
        // One farewell tick. Cancellation only takes a session out of its wait — unwinding the
        // stack to its `finally` is dispatched into the queue, and without this tick the cleanup
        // simply would not happen: this world will have no next tick.
        scheduler.advance()
        scheduler.cancelAll()
        beyonders.online.map { it.playerId }.forEach { id ->
            cooldowns.forget(id)
            passives.forget(id)
            selection.forget(id)
            pendingChoices.forget(id)
            statePump.forget(id)
        }
        beyonders.online.toList().forEach { beyonders.forget(it.playerId) }
        server = null
    }

    private const val PRUNE_PERIOD_TICKS = 600

    /** Ten seconds: a theft's terms are minutes long, so a finer sweep would buy nothing. */
    private const val THEFT_SWEEP_TICKS = 200
}
