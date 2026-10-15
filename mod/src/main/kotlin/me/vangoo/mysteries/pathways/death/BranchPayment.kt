package me.vangoo.mysteries.pathways.death

import me.vangoo.mysteries.ability.CastContext

/**
 * Pays for one branch of a many-branched power: its own price and its own cooldown.
 *
 * Several Death powers are one cast with very different effects behind it — which spirit is
 * channelled, which occupant is asked, whether a spirit is recruited or summoned. The plugin gave
 * them all one cooldown (the harshest) because `getCooldown` could not see the branch, and charged
 * the difference in price by hand next to a comment.
 *
 * Here the ticket already covers the cheapest form (`spiritualityCost`), the surplus is taken by
 * `payUpkeep` — all or nothing — and the ticket is settled **right here**, so the cooldown can be
 * overwritten with the branch's own. The cast then returns `Done`, and the executor, seeing a
 * settled ticket, touches nothing.
 *
 * @return `false` — there is not enough spirituality for this branch; nothing was taken.
 */
internal fun CastContext.payBranch(total: Int, cooldownSeconds: Int): Boolean {
    if (!beyonder.spirituality.hasSufficient(total)) return false
    val surplus = (total - ability.spiritualityCost).coerceAtLeast(0)
    if (!beyonder.payUpkeep(surplus)) return false
    cast.complete()
    cooldowns.set(caster.uuid, ability.identity, cooldownSeconds)
    return true
}
