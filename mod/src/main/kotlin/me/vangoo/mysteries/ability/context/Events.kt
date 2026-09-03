package me.vangoo.mysteries.ability.context

import me.vangoo.mysteries.event.GameEvent
import kotlin.reflect.KClass

/** A subscription lives until it is cancelled or until its declared term expires. */
const val EVENT_FOREVER: Int = Int.MAX_VALUE

/**
 * What the ability subscribed to and how to cancel that subscription.
 *
 * Keeping it is **not required**: a subscription dies on its own together with the ability
 * (see [Events]). The object is only needed by someone who wants to unsubscribe earlier: a
 * one-shot reaction cancels itself on its first firing.
 */
interface Subscription {

    /** `false` — the subscription has already been cancelled or its term has expired. */
    val isActive: Boolean

    /** Idempotent. */
    fun cancel()
}

/**
 * Subscribing to game events — **on behalf of this ability of this player**.
 *
 * ## How this differs from the plugin's `IEventContext`
 *
 * The plugin's interface took a `UUID playerId` as a separate argument and was cancelled in
 * bulk — `unsubscribeAll(casterId)`. The consequence: an ability that cleaned up after itself
 * removed the subscriptions of **every other ability of the same player**. Twenty abilities
 * behaving correctly broke each other precisely by behaving correctly.
 *
 * Here the owner is not passed in — the service knows it: the context was issued for a specific
 * "player + ability" pair, and there is no way to cancel someone else's subscription. The same
 * move already made for sessions in `Scheduling.session` — the key comes from the context, not
 * from an argument.
 *
 * ## Who cancels subscriptions
 *
 * The ability need do nothing. The registry removes them itself when:
 *
 * | when | where |
 * |---|---|
 * | the ability is activated anew (joining a world, ascension) | `PassiveTicker.activate` |
 * | the ability is lost (demotion, pathway change, `forget`) | the same place |
 * | the player left the world | `AbilityServices` |
 * | the server stopped | `AbilityServices.reset` |
 *
 * The first row matters on its own: **activation replaces subscriptions**, exactly as a recast
 * replaces a session. A passive that subscribes in `onActivate` will not double up after death
 * and respawn, and it need write nothing to get that.
 */
interface Events {

    /**
     * Subscribe to events of type [type].
     *
     * @param durationTicks how many ticks to live; [EVENT_FOREVER] — as long as the ability itself
     */
    fun <E : GameEvent> subscribe(
        type: KClass<E>,
        durationTicks: Int,
        handler: (E) -> Unit,
    ): Subscription
}

/**
 * The same thing, but with the event type visible to the eye:
 *
 * ```kotlin
 * context.events.on<IncomingDamage> { damage ->
 *     if (damage.victim === context.caster) damage.scale(0.75)
 * }
 * ```
 */
inline fun <reified E : GameEvent> Events.on(
    durationTicks: Int = EVENT_FOREVER,
    noinline handler: (E) -> Unit,
): Subscription = subscribe(E::class, durationTicks, handler)
