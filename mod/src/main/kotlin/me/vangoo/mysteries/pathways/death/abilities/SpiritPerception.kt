package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritMediumLore
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.pathways.death.DeathTraces
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isDeadSoul
import me.vangoo.mysteries.pathways.death.seesCorpse
import me.vangoo.mysteries.pathways.death.seesSoul
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID

/**
 * Death, Seq. 7 (Spirit Medium): **Spirit Perception**.
 *
 * Three entries of the tier in one passive: spiritual perception so strong spirits are felt "even
 * with eyes closed" (through walls), the lingering dead seen where they fell, and the affinity by
 * which "the deceased and natural Spirits take the initiative to pass on information" — read here
 * as a whisper about the enemy behind one's back.
 *
 * ## What changed against the plugin
 *
 * - **The dead are remembered by the world, not by this passive.** The plugin's passive *recorded*
 *   deaths, so nothing died unseen while no medium was online. [DeathTraces] records every death;
 *   this only reads.
 * - **The whisper answers the moment, not a poll.** The plugin scanned for hostiles behind the
 *   medium once a second. A creature taking the medium as its target is the mod's own
 *   [MobTargeting] event — the spirits speak exactly when someone chooses them as prey.
 * - **Souls appear, then fade.** A sight that is always on stops being information and becomes
 *   clutter: each soul or body shows for a few seconds after the medium first comes near, and
 *   crouching calls them all back.
 *
 * From Seq. 5 (Gatekeeper) the same passive also feels the undead and spirits that **arrive** from
 * afar — newcomers only, not those already standing near.
 */
class SpiritPerception :
    PermanentPassiveAbility(AbilityIdentity.of("death_spirit_perception")),
    Ticking {

    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Сприйняття духів")

    /** viewer → (trace → the tick it was first seen). */
    private val noticed = HashMap<UUID, HashMap<String, Long>>()

    /** Gatekeepers: which dead are already known to be near. */
    private val known = HashMap<UUID, MutableSet<UUID>>()

    private val whisperSilentUntil = HashMap<UUID, Long>()
    private val approachSilentUntil = HashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Духи для вас свої: ви відчуваєте їх навіть із заплющеними очима, а вони самі підказують про небезпеку.\n" +
            "▪ Духи світяться крізь стіни в радіусі ${SpiritMediumLore.perceptionRadius(sequence).toInt()} бл.\n" +
            "▪ Душі загиблих гравців видно на місці смерті ще ${DeathTraces.SOUL_TICKS / 1200} хв, тіла — ${DeathTraces.CORPSE_TICKS / 1200} хв (присядьте, щоб побачити знову)\n" +
            "▪ Духи шепочуть, коли вас обирає ворог" +
            if (sequence.level <= GATEKEEPER) "\n▪ Ви відчуваєте нежить і духів, що наближаються здалеку ($APPROACH_RADIUS бл.)" else "",
    )

    override fun onActivate(context: AbilityContext) {
        val casterId = context.caster.uuid
        val messaging = context.messaging
        val scheduling = context.scheduling
        context.events.on<MobTargeting> { event ->
            val medium = event.target
            if (medium.uuid != casterId) return@on
            val now = scheduling.tick
            if (now < (whisperSilentUntil[casterId] ?: 0L)) return@on
            whisperSilentUntil[casterId] = now + WHISPER_SILENCE_TICKS
            val toMob = event.mob.position().subtract(medium.position()).normalize()
            val behind = medium.getViewVector(1f).dot(toMob) < 0
            val where = if (behind) "за спиною" else "поруч"
            messaging.actionBar(casterId, Component.literal("Духи шепочуть: $where — ${event.mob.name.string}"))
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        val id = context.caster.uuid
        noticed.remove(id)
        known.remove(id)
        whisperSilentUntil.remove(id)
        approachSilentUntil.remove(id)
    }

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val radius = SpiritMediumLore.perceptionRadius(context.sequence)
        val now = context.scheduling.tick
        val seen = noticed.getOrPut(caster.uuid) { HashMap() }
        if (caster.isShiftKeyDown) seen.clear()

        val dimension = context.level.dimension()
        val inRange = HashSet<String>()
        for (soul in DeathTraces.souls(dimension, caster.position(), radius, caster.uuid)) {
            val key = "s${soul.victimId}${soul.atTick}".also(inRange::add)
            if (fresh(seen, key, now)) caster.seesSoul(soul.pos)
        }
        for (body in DeathTraces.corpses(dimension, caster.position(), radius, caster.uuid)) {
            val key = "c${body.victimId}${body.atTick}".also(inRange::add)
            if (fresh(seen, key, now)) caster.seesCorpse(body.pos)
        }
        // Walk away and come back — the dead show themselves again.
        seen.keys.retainAll(inRange)

        for (spirit in context.targeting.livingNear(radius)) {
            if (Spirits.isSpirit(spirit)) spirit.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, false))
        }

        if (context.sequence.level <= GATEKEEPER) senseApproach(context, now)
    }

    /** Shown for [VISIBLE_TICKS] after the first sighting; forgotten once out of range for good. */
    private fun fresh(seen: HashMap<String, Long>, key: String, now: Long): Boolean {
        val first = seen.getOrPut(key) { now }
        return now - first < VISIBLE_TICKS
    }

    private fun senseApproach(context: AbilityContext, now: Long) {
        val caster = context.caster
        val set = known.getOrPut(caster.uuid) { HashSet() }
        val near = context.targeting.livingNear(APPROACH_RADIUS.toDouble()).filter { it.isDeadSoul() }
        val newcomer = near.firstOrNull { it.uuid !in set }
        set.retainAll(near.map { it.uuid }.toSet())
        set += near.map { it.uuid }
        if (newcomer == null || now < (approachSilentUntil[caster.uuid] ?: 0L)) return
        approachSilentUntil[caster.uuid] = now + APPROACH_SILENCE_TICKS
        context.messaging.actionBar(caster, Component.literal("Загробний Світ ворушиться: наближається ${newcomer.name.string}"))
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.6f)
    }

    private companion object {
        const val GATEKEEPER = 5
        const val APPROACH_RADIUS = 40
        const val GLOW_TICKS = 25
        const val VISIBLE_TICKS = 4 * TICKS_PER_SECOND
        const val WHISPER_SILENCE_TICKS = 10L * TICKS_PER_SECOND
        const val APPROACH_SILENCE_TICKS = 10L * TICKS_PER_SECOND
    }
}
