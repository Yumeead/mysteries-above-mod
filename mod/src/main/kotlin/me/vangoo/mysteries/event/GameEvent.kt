package me.vangoo.mysteries.event

import me.vangoo.domain.abilities.AbilitySpec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob

/**
 * A mod event: something that happened in the world and that an ability has the right to answer.
 *
 * ## Why this is a type of our own rather than a vanilla hook
 *
 * In the plugin an ability subscribed to a `Class<? extends org.bukkit.event.Event>` — to a
 * **vanilla** event directly — and wrote the filter itself. That gave 46 abilities, each with
 * its own `event.getEntity().getUniqueId().equals(casterId)`, and no single place showing what
 * the mod reacts to at all.
 *
 * Here a type of our own stands between the game and the ability. The gain is not abstraction
 * for abstraction's sake but three concrete things:
 *
 * 1. **An event's source is not its shape.** [IncomingDamage] arrives from a mixin,
 *    `ALLOW_DAMAGE` mixes cancellation into it — the ability neither sees that nor has to.
 * 2. **What is mutable is what is really mutable.** The vanilla `hurtServer` hands the damage
 *    over as an argument; here it is [IncomingDamage.amount], and it cannot be confused with
 *    what cannot be changed.
 * 3. **The mod's list of events reads in one file.** What is not here, the mod does not catch.
 *
 * ## What will come here
 *
 * There are exactly as many events as someone listens to: an event nothing is subscribed to is
 * the same interface to a non-existent subsystem the context turned down. The next one costs
 * one class here and one line in [GameEventBridge].
 *
 * Death and respawn are deliberately **not** here: only the runtime listens to them (sessions
 * are cancelled, passives restarted), not abilities, so they stayed as regular Fabric hooks in
 * `AbilityServices`. The bus exists for abilities — anything only the mod needs subscribes to
 * Fabric directly.
 */
interface GameEvent

/**
 * Someone alive is **about to take damage** — before armour, enchantments and the rest of the
 * mitigations.
 *
 * The most expensive event of phase 06 and the only one a mixin had to be written for:
 * `ALLOW_DAMAGE` in the Fabric API can **cancel** damage but not change it — its callback
 * returns a `boolean`. In the plugin, though, sixteen files edit the damage
 * (`event.setDamage(...)`), so without changing the number half the combat abilities would not
 * have moved over at all.
 *
 * ## The moment
 *
 * The event is an **intent** to deal damage, not the fact. It arrives from the start of
 * `hurtServer`, so:
 *
 * - invulnerability (`isInvulnerableTo`) has already been filtered out — [GameEventBridge] does
 *   that;
 * - invulnerability frames (`invulnerableTime`) have **not** yet: they are checked later in the
 *   method body. An ability counting hits will count the ones vanilla discards too;
 * - armour, enchantments, a shield, absorption — not yet either. [amount] is "raw" damage.
 *
 * The same caveat applies to the vanilla `ALLOW_DAMAGE`: it is fixed on `isSleeping()`, i.e.
 * also before the invulnerability frames.
 *
 * @property victim who takes the damage; not necessarily a player and not necessarily a Beyonder
 * @property source the vanilla source — the damage type, the culprit, the weapon
 * @property original how much damage the intent held before the abilities saw it
 */
class IncomingDamage(
    val victim: LivingEntity,
    val source: DamageSource,
    val original: Float,
) : GameEvent {

    /**
     * How much damage goes on. Never drops below zero: negative damage in `hurtServer` heals,
     * and an ability that "cut off more than there was" must not do that silently.
     */
    var amount: Float = original
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    /** Cancelled damage is never dealt by vanilla at all — no sound, no invulnerability frames. */
    var isCancelled: Boolean = false
        private set

    /**
     * Who struck, if it was anything alive at all. An arrow, a fireball and a trident hand over
     * the **shooter**, not the projectile — the projectile lies in [directCause].
     */
    val attacker: LivingEntity? get() = source.entity as? LivingEntity

    /** What exactly arrived: an arrow, a projectile, the entity itself. */
    val directCause: Entity? get() = source.directEntity

    fun cancel() {
        isCancelled = true
    }

    /** Multiply the damage: `0.75` — cut off a quarter, `1.5` — add a half. */
    fun scale(factor: Double) {
        amount = (amount * factor).toFloat()
    }

    override fun toString(): String =
        "IncomingDamage(${victim.name.string}, ${source.msgId}, $original → $amount" +
            if (isCancelled) ", скасовано)" else ")"
}

/**
 * A Beyonder **has just used a power** — and it is noticeable from the outside.
 *
 * The mod's second event and the first whose source is not the game but the mod itself:
 * [me.vangoo.mysteries.ability.AbilityExecutor] publishes it after the ability has run.
 * A mixin has nothing to do with it — the point is already ours.
 *
 * The event exists because two Door abilities subscribe to it: `DecryptPatterns` reads other
 * people's patterns within a radius, `Record` waits for the very power it records. The rule is
 * the same as for the rest of the bus: an event nothing is subscribed to is an interface to
 * emptiness.
 *
 * Published for [me.vangoo.mysteries.ability.AbilityOutcome.Done] and
 * [me.vangoo.mysteries.ability.AbilityOutcome.Awaiting]: a power that failed on a missing target
 * is invisible from the outside.
 *
 * @property caster who cast it; a live object, because the event is dispatched synchronously with the cast
 * @property spec what exactly was cast — the `core` half, so the subscriber fetches the name
 */
class AbilityCast(
    val caster: ServerPlayer,
    val spec: AbilitySpec,
) : GameEvent {

    override fun toString(): String = "AbilityCast(${caster.name.string}, ${spec.identity})"
}

/**
 * A Justiciar's `PowerProhibition` was broken — published by the ability itself through
 * [me.vangoo.mysteries.ability.context.Events.publish], not by a mixin.
 *
 * The plugin coupled `PowerProhibition` and `Punishment` directly: a private static method call
 * over a private static map, so the enforcer had to know the punisher's class by name. Here
 * `Punishment` merely subscribes like any other ability — it could vanish from the roster and
 * `PowerProhibition` would not need to change a line.
 *
 * @property enforcerId the Beyonder whose zone was broken
 * @property violatorId who broke it
 * @property action a short, already-Ukrainian description for the enforcer's action bar
 */
class ProhibitionViolated(
    val enforcerId: java.util.UUID,
    val violatorId: java.util.UUID,
    val action: String,
) : GameEvent

/**
 * A creature is **taking someone as its target** — before the target is actually assigned.
 *
 * The mod's third event and the second to need a mixin. There is no ready hook: the Fabric API
 * has nothing for aggro, vanilla has no attribute for it, and a tick loop asking "is anyone
 * aiming at me right now" is exactly the polling the porting rule calls a crutch — the plugin
 * paid for it by re-pointing every mob back at a false target every ten ticks.
 *
 * Three Error powers of Sequence 8 ask for this one moment: the Swindler's charm denies the
 * acquisition outright, `Eloquence` keeps one talked-down creature off the caster, and
 * `ThoughtMisdirection` holds the lie without a single scheduled task.
 *
 * Only a real acquisition is published: `setTarget(null)` clears aggro, and denying **that**
 * would pin a mob onto its victim forever.
 *
 * @property mob who is acquiring
 * @property target whom it is acquiring — never `null`
 */
class MobTargeting(
    val mob: Mob,
    val target: LivingEntity,
) : GameEvent {

    /** Denied acquisition never happens: the creature keeps whatever target it already had. */
    var isCancelled: Boolean = false
        private set

    fun deny() {
        isCancelled = true
    }

    override fun toString(): String =
        "MobTargeting(${mob.name.string} → ${target.name.string}" +
            if (isCancelled) ", denied)" else ")"
}
