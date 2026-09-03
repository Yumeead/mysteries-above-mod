package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.ceil

/**
 * An ability: what it **is** (inherited from [AbilitySpec], living in `core`) plus
 * what it **does** (everything else here).
 *
 * The order of a cast is held by [AbilityExecutor], not by the base class. In the plugin this
 * was a `final Ability.execute()` with a chain of hooks `canExecute → preExecution → roll →
 * performExecution → postExecution`; half of them were orchestration that does not belong to
 * an ability. Here an ability answers only two questions: "can I right now" and "what to do".
 */
abstract class Ability(
    override val identity: AbilityIdentity,
    override val type: AbilityType,
) : AbilitySpec {

    /** The name shown to the player — in Ukrainian. */
    abstract val displayName: Component

    /** The description for the menu; it depends on the Sequence, because the numbers do. */
    abstract fun description(sequence: Sequence): Component

    /**
     * The menu icon — an ordinary [ItemStack].
     *
     * The cheapest thing that leaves the door open: the mod still carries no texture of its
     * own (the same line already drawn by the potion — a vanilla bottle with a tint instead of
     * 220 models), the stack travels the wire with a ready-made `ItemStack.STREAM_CODEC`, and
     * it is drawn by a single vanilla call.
     *
     * The default icon comes from the ability type; it is overridden in one line. The roster
     * builder applies the pathway colour unless the ability declared its own `DYED_COLOR`.
     *
     * Every call must return **its own** stack: the roster builder dyes it.
     */
    open val icon: ItemStack get() = when (type) {
        AbilityType.ACTIVE -> ItemStack(Items.AMETHYST_SHARD)
        AbilityType.TOGGLEABLE_PASSIVE -> ItemStack(Items.ECHO_SHARD)
        AbilityType.PERMANENT_PASSIVE -> ItemStack(Items.PRISMARINE_SHARD)
    }

    override val spiritualityCost: Int get() = 0

    override fun cooldownSeconds(sequence: Sequence): Int = 0

    /** An extra cast condition on top of ownership, cooldown and spirituality. */
    open fun canCast(context: CastContext): Boolean = true

    /**
     * The target whose Sequence-based resistance must be overcome before the effect. `null` —
     * the ability is not contested (most are not). Resistance is only rolled against other
     * Beyonders.
     */
    open fun contestedTarget(context: CastContext): LivingEntity? = null

    /**
     * Losing the ability or shutting the mod down: clean up whatever is left in the world.
     *
     * **Sessions need no cancelling** — the registry cancels them itself (the player leaving,
     * the server stopping, a recast), and cleanup after a session lives in its `finally`. Only
     * what would outlive a cancelled session goes here: summoned creatures, changed blocks,
     * sealed items.
     */
    open fun cleanUp() {}

    /** Power scaling by Sequence. Do not write multipliers of your own. */
    protected fun scale(
        base: Int,
        sequence: Sequence,
        strategy: SequenceScaler.ScalingStrategy,
    ): Int = ceil(base * SequenceScaler.calculateMultiplier(sequence.level, strategy)).toInt()

    final override fun equals(other: Any?): Boolean =
        other is Ability && other.identity == identity

    final override fun hashCode(): Int = identity.hashCode()

    override fun toString(): String = "${this::class.simpleName}($identity)"
}

/** An ability the player switches on explicitly: it costs spirituality and leaves a cooldown. */
abstract class ActiveAbility(identity: AbilityIdentity) : Ability(identity, AbilityType.ACTIVE) {

    abstract fun cast(context: CastContext): AbilityOutcome
}

/**
 * Something that ticks on a clock of its own.
 *
 * Not `sealed`: ever since ticking became a passive's choice rather than its duty, `Ticking`
 * has to be declared in the ability's own package — and `sealed` only allows implementations
 * inside its own.
 *
 * [tickPeriodTicks] is the main difference from the plugin. There `PassiveAbilityScheduler`
 * called `tick()` on everything every tick, and a passive that only needed a second's cadence
 * counted `if (++runs % 20 == 0)` itself. A counter in an ability field is state that goes out
 * of sync when the player relogs; a declared period is just a number.
 *
 * **A passive does not inherit this.** Ever since it learned to wait for an event (phase 06),
 * ticking is a choice for it, not a duty: a resistance that fires on a hit need not poll the
 * world twenty times a second. Whoever wants to tick adds `, Ticking` to their own header.
 */
interface Ticking {

    /** The tick period in ticks. 1 — every tick, 20 — once a second. */
    val tickPeriodTicks: Int get() = 1

    fun tick(context: AbilityContext)
}

/** A passive that works at all times, as long as the Beyonder has the ability. */
abstract class PermanentPassiveAbility(
    identity: AbilityIdentity,
) : Ability(identity, AbilityType.PERMANENT_PASSIVE) {

    /**
     * Joining the game, respawning, or gaining the ability on ascension.
     *
     * The place to subscribe to events: the registry removes this ability's previous
     * subscriptions **before** every call, so activating twice doubles nothing.
     */
    open fun onActivate(context: AbilityContext) {}

    /** Leaving the game or losing the ability. The registry removes subscriptions, not the ability. */
    open fun onDeactivate(context: AbilityContext) {}
}

/**
 * A passive with a switch the player holds.
 *
 * If a power must go out **on its own** — spirituality ran out, the time expired — this is not
 * the place: the switch lives outside the ability and cannot be reached from inside. Such
 * powers are an [ActiveAbility] plus a session: the session body decides when to go out, and
 * its `finally` removes whatever it applied.
 */
abstract class ToggleablePassiveAbility(
    identity: AbilityIdentity,
) : Ability(identity, AbilityType.TOGGLEABLE_PASSIVE) {

    abstract fun onEnable(context: AbilityContext)

    abstract fun onDisable(context: AbilityContext)
}
