package me.vangoo.mysteries.pathways.fool

import me.vangoo.domain.valueobjects.RecentDamageHeal
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.event.IncomingDamage
import java.util.UUID

/**
 * How much damage everyone alive has taken recently — the pool `DamageTransfer` (Seq. 7) heals
 * from, for the caster or for whoever they Shift+click.
 *
 * `DamageTransfer` needs an answer about **any** entity, not only its own caster, so this cannot
 * live on a per-player [me.vangoo.mysteries.ability.context.Events] subscription — that scope is
 * torn down on logout and wiped whole on every server stop. Instead [record] is called straight
 * from [me.vangoo.mysteries.ability.runtime.AbilityServices]'s own publish sink, the same way
 * [me.vangoo.mysteries.event.EvidenceLog] already watches [me.vangoo.mysteries.event.AbilityCast]
 * from outside the per-ability bus entirely.
 */
object RecentDamageLedger {

    private val WINDOW_TICKS = RecentDamageHeal.DAMAGE_WINDOW_MS / 50

    private class Entry(var amount: Double, var atTick: Long)

    private val recent = HashMap<UUID, Entry>()

    fun record(damage: IncomingDamage) {
        if (damage.isCancelled || damage.amount <= 0f) return
        val now = AbilityServices.scheduler.tick
        val existing = recent[damage.victim.uuid]
        if (existing != null && now - existing.atTick < WINDOW_TICKS) {
            existing.amount += damage.amount
            existing.atTick = now
        } else {
            recent[damage.victim.uuid] = Entry(damage.amount.toDouble(), now)
        }
    }

    /** The warm damage pool for [id], consumed whole — a heal spends what it read. */
    fun take(id: UUID): Double {
        val now = AbilityServices.scheduler.tick
        val entry = recent.remove(id) ?: return 0.0
        if (now - entry.atTick > WINDOW_TICKS) return 0.0
        return entry.amount
    }

    /** Server shutdown: the pool belongs to the world, and this world is over. */
    fun reset() {
        recent.clear()
    }
}
