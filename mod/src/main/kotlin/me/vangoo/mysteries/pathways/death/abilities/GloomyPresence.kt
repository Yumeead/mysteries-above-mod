package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import java.util.UUID

/**
 * Death, Seq. 9 (Corpse Collector): **Gloomy Presence**.
 *
 * "Their gloomy presence and lower body temperature will prevent a Corpse Collector from being
 * attacked by Undead creatures and Spirits."
 *
 * ## Refused at the moment of choosing, not unpicked every second
 *
 * The plugin cancelled Bukkit's targeting event *and* swept every mob nearby once a second to drop
 * targets taken before the potion. The mod's [MobTargeting] fires on every acquisition, so the
 * undead simply never pick the Corpse Collector; the sweep is left only where the event cannot
 * reach — the targets already held at the moment of activation, and the chase that outlives a
 * provocation.
 *
 * Retribution stays as in the plugin: strike the dead first and they remember it for
 * [CorpseCollectorLore.PROVOKE_MEMORY_SECONDS]. Spirits join the undead from Seq. 8 — they are not
 * dead, they only stop seeing a stranger once the Gravedigger can speak with them.
 */
class GloomyPresence : PermanentPassiveAbility(AbilityIdentity.of("death_gloomy_presence")) {

    override val displayName: Component = Component.literal("Похмура присутність")

    /** Who provoked the dead, and until which tick they remember it. */
    private val provokedUntil = HashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Нежить не відчуває у вас живого й не нападає першою.\n" +
            (if (sequence.level <= SPIRIT_PEACE_SEQUENCE) "▪ Духи теж бачать у вас свого\n" else "") +
            "▪ Ударите першим — пам'ятатиме ${CorpseCollectorLore.PROVOKE_MEMORY_SECONDS} с",
    )

    override fun onActivate(context: AbilityContext) {
        val caster = context.caster
        val casterId = caster.uuid
        val beyonders = context.beyonders
        val scheduling = context.scheduling
        provokedUntil.remove(casterId)

        context.events.on<MobTargeting> { event ->
            if (event.target.uuid != casterId) return@on
            val sequence = beyonders.of(casterId)?.sequence ?: return@on
            if (ignores(event.mob, sequence) && !isProvoked(casterId, scheduling.tick)) event.deny()
        }
        context.events.on<IncomingDamage> { damage ->
            val attacker = damage.attacker ?: return@on
            if (attacker.uuid != casterId || !(damage.victim.isUndead() || Spirits.isSpirit(damage.victim))) return@on
            val memory = CorpseCollectorLore.PROVOKE_MEMORY_SECONDS * TICKS_PER_SECOND
            provokedUntil[casterId] = scheduling.tick + memory
            // When the grudge runs out, the chase does too.
            scheduling.after(memory + 1) {
                if (!isProvoked(casterId, scheduling.tick) && attacker.isAlive) {
                    val sequence = beyonders.of(casterId)?.sequence ?: return@after
                    releaseLockedOn(attacker, sequence)
                }
            }
        }
        releaseLockedOn(caster, context.sequence)
    }

    override fun onDeactivate(context: AbilityContext) {
        provokedUntil.remove(context.caster.uuid)
    }

    private fun releaseLockedOn(caster: LivingEntity, sequence: Sequence) {
        val radius = CorpseCollectorLore.gloomRadius(sequence)
        val level = caster.level() as ServerLevel
        level.getEntitiesOfClass(Mob::class.java, caster.boundingBox.inflate(radius)) { it.target === caster && ignores(it, sequence) }
            .forEach { mob ->
                mob.target = null
                level.sendParticles(ParticleTypes.SOUL, mob.x, mob.eyeY, mob.z, 4, 0.2, 0.2, 0.2, 0.01)
            }
    }

    private fun ignores(entity: Entity, sequence: Sequence): Boolean =
        entity.isUndead() || (Spirits.isSpirit(entity) && sequence.level <= SPIRIT_PEACE_SEQUENCE)

    private fun isProvoked(casterId: UUID, now: Long): Boolean = (provokedUntil[casterId] ?: 0L) > now

    private companion object {
        const val SPIRIT_PEACE_SEQUENCE = 8
    }
}
