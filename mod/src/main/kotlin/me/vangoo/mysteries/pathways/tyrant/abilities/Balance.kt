package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.roundToInt

/**
 * Tyrant, Seq. 9 (Sailor): **Balance**.
 *
 * A sea wolf's perfect sense of balance — the Sailor is harder to knock down.
 *
 * ## Two calls instead of a tick
 *
 * The plugin re-applied `KNOCKBACK_RESISTANCE` once a second, because a Bukkit attribute could
 * be reset from elsewhere. A transient modifier here needs no refresh: it stays until this
 * ability revokes it, so there is nothing to tick.
 *
 * Parameterised like [me.vangoo.mysteries.pathways.common.PhysicalEnhancement], not subclassed:
 * Seq. 6's Air Cushion shares this identity and is this same class instantiated with a stronger
 * [resistance] plus fall immunity, so the stronger version replaces the weaker one on
 * advancement rather than sitting beside it as a different class under the same key — the
 * project's own rule (`ModPathwaysTest`) requires a shared identity to mean a shared class.
 */
class Balance(
    identity: AbilityIdentity = AbilityIdentity.of(STANCE_IDENTITY),
    override val displayName: Component = Component.literal("Рівновага"),
    private val flavour: String = "Досконале чуття рівноваги морської сутності: вас важче збити з ніг.",
    private val resistance: Double = 0.25,
    private val fallImmune: Boolean = false,
) : PermanentPassiveAbility(identity) {

    private val knockbackModifier = modifierId(identity.id, "knockback_resistance")
    private val fallModifier = modifierId(identity.id, "fall_damage_multiplier")

    override fun description(sequence: Sequence): Component = Component.literal(
        "$flavour\n▪ Опір відкиданню: +${(resistance * 100).roundToInt()}%" +
            if (fallImmune) "\n▪ Шкода від падіння: відсутня" else "",
    )

    override fun onActivate(context: AbilityContext) {
        context.caster.grantAttribute(Attributes.KNOCKBACK_RESISTANCE, knockbackModifier, resistance)
        if (fallImmune) {
            // 1.0 (base) + (-1.0) == 0: the fall's own damage is zeroed out at its source,
            // rather than caught and cancelled after the fact.
            context.caster.grantAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier, -1.0)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.KNOCKBACK_RESISTANCE, knockbackModifier)
        if (fallImmune) context.caster.revokeAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier)
    }

    private companion object {
        /** Shared across Balance (Seq. 9) and AirCushion (Seq. 6): the stronger stance replaces the weaker. */
        const val STANCE_IDENTITY = "tyrant_stance"
    }
}
