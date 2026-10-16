package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.Targeting
import me.vangoo.mysteries.ability.runtime.AbilityModes
import me.vangoo.mysteries.ability.runtime.PendingChoices
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Everything an ability sees while it works.
 *
 * ## Why this is not a port of `IAbilityContext`
 *
 * In the plugin the context was a **facade over Bukkit**: 16 interfaces that took a `UUID` and
 * returned a `Location`, because the domain was not allowed to touch `org.bukkit` and the
 * plugin did not have the entity itself at hand — only its identifier and a server handle.
 * Hence the 27 methods of `IDataContext` shaped like `getHealth(UUID)`, `getFoodLevel(UUID)`,
 * `isSneaking(UUID)`.
 *
 * Here that reason is gone. Abilities live in the mod, so [caster] is a live [ServerPlayer],
 * and `caster.health`, `caster.isInWater`, `caster.addEffect(...)` are available directly.
 * **A wrapper around a single vanilla call is never added to the context** — the same rule
 * that applied in the plugin, only now it covers all the arithmetic of players and entities.
 *
 * What remains is what vanilla Minecraft does not own: **the mod's own services**.
 *
 * ## A cast is a [CastContext], not this interface
 *
 * A passive ticks without a ticket: there is nothing to pay and nothing to settle. So the
 * ticket lives not here but in [CastContext], which only [ActiveAbility.cast] ever sees. A
 * passive never has to write `cast!!` — it simply does not know about the ticket.
 *
 * ## Events are a service, not a hook
 *
 * [events] is not a list of vanilla events but a subscription **on behalf of this ability of
 * this player**: the owner is visible from the context, so someone else's subscription cannot
 * be removed, and the registry cancels one's own. This is the most visible difference from the
 * plugin, where abilities removed each other's subscriptions through `unsubscribeAll(casterId)`.
 *
 * ## What else will come here
 *
 * The context grows together with the mechanics, not ahead of them: an interface to a
 * subsystem that does not exist yet is an interface to nothing.
 *
 * | what | phase |
 * |---|---|
 * | `ui` — choice screens and input | 11 |
 * | `summons` — data-driven creatures | 12 |
 *
 * [pendingChoices] is that `ui`: an ability that must ask mid-cast (`Alteration`, `ThunderVoice`)
 * offers the options and the caster's client opens a screen. A choice known before the cast is
 * a mode instead ([me.vangoo.mysteries.ability.Ability.modes]).
 */
interface AbilityContext {

    // ── Caster ──────────────────────────────────────────────────────────────────

    val caster: ServerPlayer

    /** The caster's domain half: Sequence, spirituality, sanity, the list of abilities. */
    val beyonder: Beyonder

    /** The ability running right now. The key of its session and the source of its own prices. */
    val ability: AbilitySpec

    val level: ServerLevel get() = caster.level()

    val sequence: Sequence get() = beyonder.sequence

    val origin: Vec3 get() = caster.position()

    val eye: Vec3 get() = caster.eyePosition

    val look: Vec3 get() = caster.getViewVector(1.0f)

    // ── The mod's services ──────────────────────────────────────────────────────

    val beyonders: Beyonders
    val cooldowns: Cooldowns
    val targeting: Targeting
    val messaging: Messaging
    val scheduling: Scheduling
    val events: Events
    val effects: Effects
    val pendingChoices: PendingChoices
    val modes: AbilityModes
}

/**
 * The context of **one cast**: the same as [AbilityContext], plus the ticket.
 *
 * A narrower type exactly where it is needed — in [ActiveAbility.cast] and its guards.
 * Everything else (a passive's tick, switching a toggle on) works on [AbilityContext] and
 * never sees the ticket.
 */
interface CastContext : AbilityContext {

    /**
     * This cast's ticket. [PendingCast.complete] spends the spirituality, accrues the mastery
     * and sets the cooldown; never call it and the cast is free.
     *
     * An active ability that returned [AbilityOutcome.Done] does nothing with it:
     * [AbilityExecutor] settles it. An ability waiting for the player's choice holds **this
     * very object** (not the whole context — that carries the identity of a single cast) and
     * settles it when the effect actually happens.
     */
    val cast: PendingCast
}

// ── Sessions ────────────────────────────────────────────────────────────────────

/**
 * Opens this ability's session for this player, cancelling the previous one.
 *
 * The key is taken from the context, so someone else's session cannot be mistaken for one's
 * own. The context is **not** passed into the body: it carries a live `ServerPlayer` and lives
 * exactly one call, while a session outlives both a respawn and a dimension change — it asks
 * for the player anew on every tick.
 */
fun AbilityContext.session(block: suspend SessionScope.() -> Unit): Session =
    scheduling.session(caster.uuid, ability, block)

/** Whether this ability's session is ticking for this player right now. */
val AbilityContext.sessionRunning: Boolean
    get() = scheduling.isRunning(caster.uuid, ability.identity)

/** Cancels this ability's session. `true` — it was alive. */
fun AbilityContext.stopSession(): Boolean = scheduling.stop(caster.uuid, ability.identity)
