package me.vangoo.mysteries.event

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob

/**
 * The single place where the game turns into a [GameEvent].
 *
 * ## The hook search order
 *
 * This is the main rule of phase 06, and it is not about convenience but about who will pay for
 * the next Minecraft update:
 *
 * 1. **a ready Fabric API hook** — its breakage is dealt with by the Fabric maintainers, and
 *    they do it before the mod even sees the new version;
 * 2. **a `Block` or `Item` of our own** — vanilla overrides instead of intercepting other
 *    people's code;
 * 3. **the `PassiveTicker` tick** — movement and sneaking are polling dressed up as an event;
 * 4. **and only then a mixin.**
 *
 * Of the plugin's 99 handlers, ~11 fall to the fourth step, and this file holds two of them:
 * the damage number ([beforeDamage]) and a creature acquiring a target ([beforeTarget]).
 *
 * ## Why damage reached the mixin after all
 *
 * `ServerLivingEntityEvents.ALLOW_DAMAGE` exists and can **cancel** damage: its callback returns
 * a `boolean`. But sixteen of the plugin's files do not cancel damage, they **edit** it
 * (`event.setDamage(...)`) — the scales cut off a quarter, the fury adds a half. A `boolean`
 * carries no number, and there is no other hook on 26.2: vanilla has no general
 * damage-reduction attribute either (only `armor`, `armor_toughness` and
 * `explosion_knockback_resistance`).
 *
 * So one event is assembled from two sources:
 *
 * | half | from where | what it can do |
 * |---|---|---|
 * | the number | the mixin, `@ModifyVariable` on the `hurtServer` argument | change [IncomingDamage.amount] |
 * | cancellation | `ALLOW_DAMAGE`, a ready hook | stop the damage going further |
 *
 * ## Why this is not a race
 *
 * The order is guaranteed not by the mixin but by **the execution order of one method**: our
 * hook stands at `HEAD`, while Fabric fixes its `ALLOW_DAMAGE` on the `isSleeping()` call
 * already inside `hurtServer`. That is, [beforeDamage] always runs first, and [pending] is read
 * by the same call of the same method on the same thread.
 *
 * If vanilla leaves the method between those two points, a cancelled event would stay in
 * [pending] — and that is exactly why [beforeDamage] clears the field on its **first line**,
 * while `ALLOW_DAMAGE` reads it with removal. A stale value never survives into someone else's
 * damage.
 */
object GameEventBridge {

    /**
     * Where to put the events. `null` — the mod has not come up yet: the mixin is already in the
     * game because the class loader put it there, so a silent exit here is mandatory.
     *
     * This field **outlives** a server shutdown, just as the scheduler itself does: the seam is
     * placed once per process, not once per world. Clearing it on `SERVER_STOPPED` would mean
     * the second world of a single-player game is left with no damage.
     */
    private var sink: ((GameEvent) -> Unit)? = null

    /** Cancelled damage waiting for its `ALLOW_DAMAGE`. Lives a fraction of one call. */
    private var pending: IncomingDamage? = null

    fun install(sink: (GameEvent) -> Unit) {
        check(this.sink == null) { "Шов подій уже встановлено" }
        this.sink = sink

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            val parked = pending
            pending = null
            parked == null || parked.victim !== entity || !parked.isCancelled
        }
    }

    /**
     * Server shutdown. Only what belongs to the world is cleaned up — an unread cancellation;
     * `EventHub` clears the subscriptions itself, and the hooks stay in place until the process ends.
     */
    fun reset() {
        pending = null
    }

    /**
     * The mixin's landing point — the **only** one, and only the mixin calls it.
     *
     * All the logic lives here rather than in the mixin, deliberately: the injection body is one
     * call, so its breakage on a new game version costs five lines of Java rather than rewriting
     * the handlers.
     *
     * @return how much damage to let through; with no subscribers at all — exactly [amount]
     */
    @JvmStatic
    fun beforeDamage(
        victim: LivingEntity,
        level: ServerLevel,
        source: DamageSource,
        amount: Float,
    ): Float {
        pending = null

        val sink = sink ?: return amount
        if (amount <= 0f) return amount
        // Invulnerability (creative, `/damage` on an invulnerable entity, immunity to a damage
        // type) is filtered out by vanilla itself — but only after our HEAD. Abilities are
        // better off not knowing about such "damage": it goes nowhere and would skew hit counters.
        if (victim.isInvulnerableTo(level, source)) return amount

        val event = IncomingDamage(victim, source, amount)
        sink(event)
        if (event.isCancelled) pending = event
        return event.amount
    }

    /**
     * The second mixin landing point: a creature is about to acquire a target.
     *
     * The same discipline as [beforeDamage] — the injection body is one call, all the thinking
     * is here. `setTarget(null)` is not published at all: clearing aggro is not an acquisition,
     * and a "denied" clear would pin the mob onto its current victim for good.
     *
     * @return `true` — the acquisition must not happen
     */
    @JvmStatic
    fun beforeTarget(mob: Mob, target: LivingEntity?): Boolean {
        val sink = sink ?: return false
        if (target == null || mob.level().isClientSide) return false
        if (mob.target === target) return false

        val event = MobTargeting(mob, target)
        sink(event)
        return event.isCancelled
    }
}
