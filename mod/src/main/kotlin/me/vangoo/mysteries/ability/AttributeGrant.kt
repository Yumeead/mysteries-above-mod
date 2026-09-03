package me.vangoo.mysteries.ability

import me.vangoo.mysteries.modId
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier

/**
 * An attribute modifier applied by an ability.
 *
 * ## Why this replaces half the plugin's passives
 *
 * In Bukkit a "strong body" had to be acted out with a potion effect: `SPEED`, `STRENGTH`,
 * `HEALTH_BOOST` at infinite duration, refreshed by a tick. That gave three flaws at once — an
 * icon in the potion list next to real potions, removal by someone else's milk, and a power
 * step nailed to whole effect levels (`SPEED I` is exactly +20 %, vanilla has no other numbers).
 *
 * An attribute has none of them: the number is arbitrary, there is no icon, milk does not take
 * it, and a damage bonus **shows up in the item tooltip by itself** — feedback the plugin could
 * not give at all. The same thing that already happened to `IllusoryScales`: the mechanics
 * changed, the balance did not.
 *
 * ## Transient, not permanent
 *
 * `addPermanentModifier` bakes the modifier into the player's file — and it would outlive
 * losing the ability, changing the pathway and removing the mod. An ability one no longer has
 * must not leave +4 health behind. Hence **only** transient ones: the registry applies them
 * anew on every activation (join, respawn, ascension), and `addOrUpdate` makes a repeated
 * activation idempotent.
 */
fun LivingEntity.grantAttribute(
    attribute: Holder<Attribute>,
    id: Identifier,
    amount: Double,
    operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_VALUE,
) {
    getAttribute(attribute)?.addOrUpdateTransientModifier(AttributeModifier(id, amount, operation))
}

/** Removes the modifier [id]. Silent if it was not there: losing an ability is not an error. */
fun LivingEntity.revokeAttribute(attribute: Holder<Attribute>, id: Identifier) {
    getAttribute(attribute)?.removeModifier(id)
}

/** The modifier's identifier: `mysteries_above:<ability>/<attribute>`. */
fun modifierId(ability: String, attribute: String): Identifier = modId("$ability/$attribute")
