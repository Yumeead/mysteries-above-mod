package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.services.ProjectileForecast
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.privateGlow
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.event.ProjectileLaunched
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.DangerPayload
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import java.util.UUID

/**
 * A sense of harm coming, shared by every pathway that has one: the Fool's Seer (Danger Intuition, Seq. 9) and the
 * Justiciar's Sheriff (Danger Intuition, Seq. 8).
 *
 * - **A projectile is foreseen the moment it leaves the string** ([ProjectileLaunched]): its whole flight is forecast
 *   with gravity ([ProjectileForecast]), and if it will pass by the owner the warning comes at once, with the time to
 *   the blow.
 * - **A mob taking aim is an event** ([MobTargeting]), not a scan: it fires at the moment a mob acquires its target.
 * - What has no moment to wait for is a state, polled by the owning ability's tick ([scan]): a bow drawn or a crossbow
 *   loaded and aimed, an armed player close and looking straight at the owner, a mob closing in, a creeper hissing, a
 *   lit fuse.
 *
 * The warning says where, not in words: the threat flares with an outline only the owner sees, a bell is heard by the
 * owner alone **from where the threat is**, and a pixel arrow by the crosshair turns towards it ([DangerPayload]) —
 * pale while it is a presence, red and pulsing under a second from the blow.
 *
 * One instance per ability (abilities are one per server), its throttles kept per owner.
 */
class DangerSense {

    private val lastWarnings = HashMap<UUID, HashMap<UUID, Long>>()

    /** Subscribes to the events of harm; call from the ability's activation (or enabling). */
    fun activate(context: AbilityContext) {
        lastWarnings[context.caster.uuid] = HashMap()
        context.events.on<MobTargeting> { event ->
            if (event.target !== context.caster) return@on
            warn(context, event.mob)
            point(context, event.mob, PRESENCE_TICKS, urgent = false)
        }
        context.events.on<ProjectileLaunched> { event -> forecast(context, event.projectile) }
    }

    fun deactivate(owner: UUID) {
        lastWarnings.remove(owner)
    }

    /** The states of harm, polled every [SCAN_PERIOD_TICKS] by the owning ability. */
    fun scan(context: AbilityContext) {
        scanArmed(context)
        scanChasers(context)
        scanExplosives(context)
    }

    /** The arrow by the crosshair: sent on every sighting, it stays as long as [ticks] say. */
    fun point(context: AbilityContext, source: Entity, ticks: Int, urgent: Boolean) {
        ModChannels.DANGER.sendTo(context.caster, DangerPayload(source.id, source.boundingBox.center, ticks.coerceAtLeast(0), urgent))
    }

    /** The threat flares for the owner alone, and a bell rings from its side; at most once in a while per threat. */
    fun warn(context: AbilityContext, source: Entity) {
        if (!fresh(context, source)) return
        val distance = context.caster.position().distanceTo(source.position())
        val volume = (1.0 - (distance / SCAN_RADIUS)).coerceAtLeast(0.3).toFloat()
        privateGlow(context.caster, listOf(source), WARN_GLOW_TICKS)
        // Played from the threat itself: the ear tells the direction, so no word has to.
        privateSound(context.caster, source.position(), SoundEvents.NOTE_BLOCK_BELL.value(), volume.coerceAtLeast(0.6f), 0.5f + volume)
    }

    /** Whether [source] has not been warned of lately; marks it warned now if so. */
    fun fresh(context: AbilityContext, source: Entity): Boolean {
        val warnings = lastWarnings.getOrPut(context.caster.uuid) { HashMap() }
        val now = context.scheduling.tick
        val last = warnings[source.uuid]
        if (last != null && now - last < WARNING_COOLDOWN_TICKS) return false
        warnings[source.uuid] = now
        return true
    }

    // ── A projectile on its way ─────────────────────────────────────────────────

    /** The flight forecast from the moment of launch: a warning only if it will pass by the owner, and when. */
    private fun forecast(context: AbilityContext, projectile: Projectile) {
        val caster = context.caster
        if (projectile.owner === caster || projectile.level() !== caster.level()) return
        if (projectile.distanceToSqr(caster) > PROJECTILE_RADIUS * PROJECTILE_RADIUS) return
        val from = projectile.position()
        val velocity = projectile.deltaMovement
        val body = caster.boundingBox.center
        val ticks = ProjectileForecast.ticksToReach(
            from.x, from.y, from.z, velocity.x, velocity.y, velocity.z, projectile.gravity,
            body.x, body.y, body.z, HIT_RADIUS, FORECAST_TICKS,
        ) ?: return
        warn(context, projectile)
        point(context, projectile, ticks, urgent = true)
    }

    // ── Weapons turned on the owner ─────────────────────────────────────────────

    /**
     * A bow drawn or a crossbow loaded and aimed at the owner, from anywhere in range; a melee weapon only close by and
     * looking straight at them — holding a sword in their direction is not a threat.
     */
    private fun scanArmed(context: AbilityContext) {
        val caster = context.caster
        for (body in context.targeting.livingNear(SCAN_RADIUS)) {
            if (body === caster) continue
            val aimed = aimsRanged(body) && isLookingAt(body, caster, AIM_CONE)
            val swinging = body is ServerPlayer && holdsMelee(body) &&
                body.distanceTo(caster) <= MELEE_RANGE && isLookingAt(body, caster, MELEE_CONE)
            if (!aimed && !swinging) continue
            warn(context, body)
            point(context, body, PRESENCE_TICKS, urgent = false)
        }
    }

    private fun aimsRanged(body: LivingEntity): Boolean =
        (body.isUsingItem && body.useItem.item is BowItem) ||
            CrossbowItem.isCharged(body.mainHandItem) || CrossbowItem.isCharged(body.offhandItem)

    private fun holdsMelee(player: ServerPlayer): Boolean =
        player.mainHandItem.typeHolder().`is`(ItemTags.WEAPON_ENCHANTABLE)

    // ── A mob closing in ─────────────────────────────────────────────────────────

    /** Not only the moment it took aim: a mob already on the owner's trail keeps being felt while it closes in. */
    private fun scanChasers(context: AbilityContext) {
        val caster = context.caster
        for (body in context.targeting.livingNear(SCAN_RADIUS)) {
            if (body !is Mob || body.target !== caster) continue
            val toward = caster.position().subtract(body.position()).normalize()
            if (body.deltaMovement.dot(toward) < CLOSING_SPEED && body.distanceTo(caster) > CLOSE_RANGE) continue
            warn(context, body)
            point(context, body, PRESENCE_TICKS, urgent = false)
        }
    }

    // ── A fuse burning ───────────────────────────────────────────────────────────

    /** A hissing creeper and a lit TNT near enough to hurt: the arrow counts down to the blast. */
    private fun scanExplosives(context: AbilityContext) {
        val caster = context.caster
        val around = caster.boundingBox.inflate(BLAST_RANGE)
        for (creeper in context.level.getEntitiesOfClass(Creeper::class.java, around) { it.swellDir > 0 }) {
            warn(context, creeper)
            point(context, creeper, ((1f - creeper.getSwelling(1f)) * CREEPER_FUSE).toInt(), urgent = true)
        }
        for (tnt in context.level.getEntitiesOfClass(PrimedTnt::class.java, around)) {
            warn(context, tnt)
            point(context, tnt, tnt.fuse, urgent = true)
        }
    }

    companion object {
        const val SCAN_RADIUS = 15.0
        const val SCAN_PERIOD_TICKS = 10

        /** A threat that is a presence rather than a timed blow is felt until the next scan, and a little after. */
        const val PRESENCE_TICKS = SCAN_PERIOD_TICKS + 5

        /** An arrow is felt from as far as a bow reaches, and its flight forecast three seconds ahead. */
        private const val PROJECTILE_RADIUS = 48.0
        private const val FORECAST_TICKS = 60

        /** How near the owner's middle a forecast path must pass to count: a body, with a little to spare. */
        private const val HIT_RADIUS = 1.4

        /** How narrow "looking at me" is: an aimed bow (~15 degrees) and a blade at arm's length (~25). */
        private const val AIM_CONE = 0.965
        private const val MELEE_CONE = 0.9
        private const val MELEE_RANGE = 5.0

        /** A mob counts as closing in at this speed towards the owner, or once it is this near. */
        private const val CLOSING_SPEED = 0.03
        private const val CLOSE_RANGE = 4.0

        /** A creeper's or TNT's blast hurts this far; a creeper takes 30 ticks to blow once it hisses. */
        private const val BLAST_RANGE = 8.0
        private const val CREEPER_FUSE = 30f

        private val WARNING_COOLDOWN_TICKS = 3L * TICKS_PER_SECOND
        private val WARN_GLOW_TICKS = 3 * TICKS_PER_SECOND / 2

        fun isLookingAt(observer: LivingEntity, target: LivingEntity, cone: Double): Boolean {
            val toTarget = target.boundingBox.center.subtract(observer.eyePosition).normalize()
            return observer.getViewVector(1.0f).normalize().dot(toTarget) > cone
        }
    }
}
