package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DangerPremonition
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.privateGlow
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.event.ProjectileLaunched
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.DangerPayload
import me.vangoo.domain.services.ProjectileForecast
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fool, Seq. 9 (Seer): **Danger Intuition**.
 *
 * The pathway's richest Seq. 9 power in the plugin, and the one that ports almost as-is: every
 * sub-mechanic here was already answering something the game can tell it directly, not a Spigot
 * workaround. Two changes from the plugin's shape:
 *
 * - **The hostile-mob warning is an event, not a poll.** The plugin re-scanned every mob in
 *   range every half-second asking "is its target me". [MobTargeting] already exists on the bus
 *   (built for Error's Sequence 8 charms) and fires at the exact moment a mob acquires a target —
 *   using it here costs one subscription, not a scan.
 * - **The lethal dodge cancels through [IncomingDamage], not `EntityDamageEvent.setCancelled`.**
 *   Same mechanism `PowerProhibition`'s kill-ban already uses.
 *
 * - **A projectile is foreseen the moment it leaves the string** ([ProjectileLaunched]): its whole
 *   flight is forecast with gravity ([ProjectileForecast]), and if it will pass by the Seer the
 *   warning comes at once, with the time to the blow. The half-second poll it replaces let an arrow
 *   cross thirty blocks unseen.
 *
 * What stays a half-second poll has no moment to wait for — it is a state, not an event: a bow drawn
 * or a crossbow loaded and aimed at the Seer, an armed player close and looking straight at them, a
 * mob closing in, a creeper hissing, a lit fuse; and the behind-the-door reveal and the prediction.
 *
 * Balance numbers live in [DangerPremonition] (`core`, phase 08 already ported it); this file is
 * effects only.
 *
 * ## The warning says where, not in words
 *
 * The first port wrote "a zombie took aim at you from the south-west" into the action bar. Now the
 * threat itself flares: an outline only the Seer sees, and a bell heard by the Seer alone, played
 * **from where the threat is**. And because a bell in a fight is easily missed and an outline behind
 * the back is not seen at all, a pixel arrow by the crosshair turns towards each threat
 * ([DangerPayload]) — pale while it is a presence, red and pulsing under a second from the blow. A
 * glimpse of what the target will do next hangs over its head as a single word.
 */
class DangerIntuition :
    PermanentPassiveAbility(AbilityIdentity.of("fool_danger_intuition")),
    Ticking {

    override val tickPeriodTicks: Int = SCAN_PERIOD_TICKS

    override val displayName: Component = Component.literal("Інтуїція небезпеки")

    override fun description(sequence: Sequence): Component {
        val dodge = (DangerPremonition.lethalDodgeChance(sequence) * 100).roundToInt()
        return Component.literal(
            "Передчуваєте загрозу до удару: снаряд, що летить у вас, натягнутий лук, озброєного ворога поруч, крипера, підпалений TNT.\n" +
                "▪ Стрілка біля прицілу показує, звідки загроза; червона — удар менш ніж за секунду\n" +
                "▪ Загроза спалахує обрисом, і дзвін лунає з її боку — бачите й чуєте лише ви\n" +
                "▪ Бачите крізь двері, на які дивитесь\n" +
                "▪ Зрідка вгадуєте наступну дію цілі під прицілом\n" +
                "▪ $dodge% шанс автоматично ухилитись від смертельного удару",
        )
    }

    private val lastWarnings = HashMap<UUID, HashMap<UUID, Long>>()
    private val lastDoorReveal = HashMap<UUID, Long>()
    private val lastPrediction = HashMap<UUID, Long>()
    private val lastDodge = HashMap<UUID, Long>()

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid
        lastWarnings[owner] = HashMap()

        context.events.on<MobTargeting> { event ->
            if (event.target !== context.caster) return@on
            warn(context, event.mob)
            point(context, event.mob, PRESENCE_TICKS, urgent = false)
        }

        context.events.on<IncomingDamage> { damage -> tryLethalDodge(context, damage) }

        context.events.on<ProjectileLaunched> { event -> forecast(context, event.projectile) }
    }

    override fun onDeactivate(context: AbilityContext) {
        val owner = context.caster.uuid
        lastWarnings.remove(owner)
        lastDoorReveal.remove(owner)
        lastPrediction.remove(owner)
        lastDodge.remove(owner)
    }

    override fun tick(context: AbilityContext) {
        scanArmed(context)
        scanChasers(context)
        scanExplosives(context)
        revealBehindDoor(context)
        predictNextAction(context)
    }

    // ── A projectile on its way ─────────────────────────────────────────────────

    /** The flight forecast from the moment of launch: a warning only if it will pass by the Seer, and when. */
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

    // ── Weapons turned on the caster ────────────────────────────────────────────

    /**
     * A bow drawn or a crossbow loaded and aimed at the Seer, from anywhere in range; a melee weapon
     * only close by and looking straight at them — holding a sword in their direction is not a threat.
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

    private fun isLookingAt(observer: LivingEntity, target: LivingEntity, cone: Double): Boolean {
        val toTarget = target.boundingBox.center.subtract(observer.eyePosition).normalize()
        return observer.getViewVector(1.0f).normalize().dot(toTarget) > cone
    }

    // ── A mob closing in ─────────────────────────────────────────────────────────

    /** Not only the moment it took aim: a mob already on the Seer's trail keeps being felt while it closes in. */
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

    // ── Behind the door ──────────────────────────────────────────────────────────

    private fun revealBehindDoor(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if ((lastDoorReveal[owner] ?: 0L) + DOOR_THROTTLE_TICKS > now) return

        val caster = context.caster
        val hit = caster.pick(DOOR_LOOK_RANGE, 1.0f, false) as? BlockHitResult ?: return
        val block = context.level.getBlockState(hit.blockPos).block
        if (block !is DoorBlock && block !is TrapDoorBlock && block !is FenceGateBlock) return

        val center = Vec3.atCenterOf(hit.blockPos)
        val revealed = context.targeting.livingNear(DOOR_REVEAL_RADIUS + DOOR_LOOK_RANGE)
            .filter { it.position().distanceTo(center) <= DOOR_REVEAL_RADIUS }
        if (revealed.isEmpty()) return

        // Seen through the door, for the Seer alone: whoever stands behind it is outlined.
        privateGlow(caster, revealed, DOOR_GLOW_TICKS)
        lastDoorReveal[owner] = now
        privateSound(caster, center, SoundEvents.SCULK_SENSOR_HIT, 0.7f, 1.4f)
    }

    // ── The occasional hint ──────────────────────────────────────────────────────

    private fun predictNextAction(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if ((lastPrediction[owner] ?: 0L) + PREDICT_THROTTLE_TICKS > now) return

        val target = context.targeting.lookingAt(PREDICT_RANGE) ?: return
        if (Random.nextDouble() > DangerPremonition.actionPredictionChance(context.sequence)) return

        val glimpse = when {
            target is Mob && target.target === context.caster -> "атакує"
            target.deltaMovement.lengthSqr() > 0.05 -> "рушить"
            else -> "вичікує"
        }
        lastPrediction[owner] = now
        privateText(context.caster, target.position(), Component.literal(glimpse), GLIMPSE_TICKS, GLIMPSE, anchor = target)
    }

    // ── Lethal dodge ─────────────────────────────────────────────────────────────

    private fun tryLethalDodge(context: AbilityContext, damage: IncomingDamage) {
        val caster = context.caster
        if (damage.victim !== caster) return
        if (damage.amount < caster.health) return

        val owner = caster.uuid
        val now = context.scheduling.tick
        val cooldownTicks = DangerPremonition.lethalDodgeCooldownMillis(context.sequence) / MILLIS_PER_TICK
        if ((lastDodge[owner] ?: 0L) + cooldownTicks > now) return
        if (Random.nextDouble() > DangerPremonition.lethalDodgeChance(context.sequence)) return

        damage.cancel()
        lastDodge[owner] = now

        // Yanked away from the blow with no wind-up, a beat before it lands: where the body was, a grey
        // trace hangs. Away from whoever struck — or aside, if the blow came from nowhere in particular.
        val from = (damage.directCause ?: damage.attacker)?.position()
        val away = from?.let { caster.position().subtract(it) }?.let { Vec3(it.x, 0.0, it.z) }?.takeIf { it.lengthSqr() > 1e-4 }
        val push = away?.normalize()?.scale(DODGE_SPEED) ?: caster.getViewVector(1.0f).let { look ->
            Vec3(-look.z, 0.0, look.x).normalize().scale(if (Random.nextBoolean()) DODGE_SPEED else -DODGE_SPEED)
        }
        context.level.sendParticles(ModParticles.mote(TRACE), caster.x, caster.y + 1.0, caster.z, TRACE_MOTES, 0.25, 0.5, 0.25, 0.0)
        caster.setDeltaMovement(push.x, DODGE_LIFT, push.z)
        // The server's own push reaches the client only if it is marked: without this the dodge never moved anyone.
        caster.hurtMarked = true
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f)
    }

    // ── Shared warning throttle ──────────────────────────────────────────────────

    /** The arrow by the crosshair: sent on every sighting, it stays as long as [ticks] say. */
    private fun point(context: AbilityContext, source: Entity, ticks: Int, urgent: Boolean) {
        ModChannels.DANGER.sendTo(context.caster, DangerPayload(source.id, source.boundingBox.center, ticks.coerceAtLeast(0), urgent))
    }

    private fun warn(context: AbilityContext, source: Entity) {
        val owner = context.caster.uuid
        val warnings = lastWarnings.getOrPut(owner) { HashMap() }
        val now = context.scheduling.tick
        val last = warnings[source.uuid]
        if (last != null && now - last < WARNING_COOLDOWN_TICKS) return
        warnings[source.uuid] = now

        val distance = context.caster.position().distanceTo(source.position())
        val volume = (1.0 - (distance / SCAN_RADIUS)).coerceAtLeast(0.3).toFloat()
        privateGlow(context.caster, listOf(source), WARN_GLOW_TICKS)
        // Played from the threat itself: the ear tells the direction, so no word has to.
        privateSound(context.caster, source.position(), SoundEvents.NOTE_BLOCK_BELL.value(), volume.coerceAtLeast(0.6f), 0.5f + volume)
    }

    private companion object {
        const val SCAN_RADIUS = 15.0
        const val SCAN_PERIOD_TICKS = 10

        /** An arrow is felt from as far as a bow reaches, and its flight forecast three seconds ahead. */
        const val PROJECTILE_RADIUS = 48.0
        const val FORECAST_TICKS = 60
        /** How near the Seer's middle a forecast path must pass to count: a body, with a little to spare. */
        const val HIT_RADIUS = 1.4

        /** How narrow "looking at me" is: an aimed bow (~15 degrees) and a blade at arm's length (~25). */
        const val AIM_CONE = 0.965
        const val MELEE_CONE = 0.9
        const val MELEE_RANGE = 5.0

        /** A mob counts as closing in at this speed towards the Seer, or once it is this near. */
        const val CLOSING_SPEED = 0.03
        const val CLOSE_RANGE = 4.0

        /** A creeper's or TNT's blast hurts this far; a creeper takes 30 ticks to blow once it hisses. */
        const val BLAST_RANGE = 8.0
        const val CREEPER_FUSE = 30f

        /** A threat that is a presence rather than a timed blow is felt until the next scan, and a little after. */
        const val PRESENCE_TICKS = SCAN_PERIOD_TICKS + 5
        val WARNING_COOLDOWN_TICKS = 3L * TICKS_PER_SECOND

        const val DOOR_LOOK_RANGE = 3.0
        const val DOOR_REVEAL_RADIUS = 4.5
        val DOOR_GLOW_TICKS = 2 * TICKS_PER_SECOND
        val DOOR_THROTTLE_TICKS = 2500L / 50

        const val PREDICT_RANGE = 20.0
        val PREDICT_THROTTLE_TICKS = 4000L / 50

        const val MILLIS_PER_TICK = 50L

        val WARN_GLOW_TICKS = 3 * TICKS_PER_SECOND / 2
        val GLIMPSE_TICKS = 2 * TICKS_PER_SECOND
        val GLIMPSE: RgbColor = RgbColor.of(214, 196, 240)
        val TRACE: RgbColor = RgbColor.of(170, 170, 182)
        const val TRACE_MOTES = 14
        const val DODGE_SPEED = 1.2
        const val DODGE_LIFT = 0.3
    }
}
