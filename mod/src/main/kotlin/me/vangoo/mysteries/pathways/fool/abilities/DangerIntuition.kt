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
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.phys.AABB
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
 * Everything else — the armed-player scan, the incoming-projectile heading check, the
 * behind-the-door reveal, the occasional prediction — has no vanilla hook to lean on, so it stays
 * a half-second poll, same as the plugin and same as Justiciar's `Intuition`.
 *
 * Balance numbers live in [DangerPremonition] (`core`, phase 08 already ported it); this file is
 * effects only.
 *
 * ## The warning says where, not in words
 *
 * The first port wrote "a zombie took aim at you from the south-west" into the action bar. Now the
 * threat itself flares: an outline only the Seer sees, and a bell heard by the Seer alone, played
 * **from where the threat is** — the ear already knows the direction, so nothing has to name it. A
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
            "Передчуваєте загрозу: ворожих мобів, озброєних гравців і снаряди в радіусі ${SCAN_RADIUS.toInt()} бл.\n" +
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
            warn(context, event.mob.uuid, event.mob)
        }

        context.events.on<IncomingDamage> { damage -> tryLethalDodge(context, damage) }
    }

    override fun onDeactivate(context: AbilityContext) {
        val owner = context.caster.uuid
        lastWarnings.remove(owner)
        lastDoorReveal.remove(owner)
        lastPrediction.remove(owner)
        lastDodge.remove(owner)
    }

    override fun tick(context: AbilityContext) {
        scanArmedPlayers(context)
        scanProjectiles(context)
        revealBehindDoor(context)
        predictNextAction(context)
    }

    // ── Armed players looking at the caster ─────────────────────────────────────

    private fun scanArmedPlayers(context: AbilityContext) {
        for (player in context.targeting.playersNear(SCAN_RADIUS)) {
            if (!holdsWeapon(player) || !isLookingAt(player, context.caster)) continue
            warn(context, player.uuid, player)
        }
    }

    private fun holdsWeapon(player: ServerPlayer): Boolean =
        player.mainHandItem.typeHolder().`is`(ItemTags.WEAPON_ENCHANTABLE)

    private fun isLookingAt(observer: LivingEntity, target: LivingEntity): Boolean {
        val toTarget = target.eyePosition.subtract(observer.eyePosition).normalize()
        return observer.getViewVector(1.0f).normalize().dot(toTarget) > 0.5
    }

    // ── Incoming projectiles ─────────────────────────────────────────────────────

    private fun scanProjectiles(context: AbilityContext) {
        val caster = context.caster
        val box = AABB.ofSize(caster.position(), PROJECTILE_SCAN_RADIUS * 2, PROJECTILE_SCAN_RADIUS * 2, PROJECTILE_SCAN_RADIUS * 2)
        val projectiles = context.level.getEntitiesOfClass(Projectile::class.java, box) { it.deltaMovement.lengthSqr() > 0.01 }

        for (projectile in projectiles) {
            if (!isHeadingTowards(projectile.position(), projectile.deltaMovement, caster.position(), 3.0)) continue
            warn(context, projectile.uuid, projectile)
        }
    }

    private fun isHeadingTowards(from: Vec3, velocity: Vec3, target: Vec3, threshold: Double): Boolean {
        val toTarget = target.subtract(from)
        val heading = velocity.normalize()
        if (heading.dot(toTarget.normalize()) < 0.8) return false
        val closest = from.add(heading.scale(toTarget.dot(heading)))
        return closest.distanceTo(target) < threshold
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

        // Yanked aside with no wind-up, a beat before the blow: where the body was, a grey trace hangs.
        val look = caster.getViewVector(1.0f)
        val side = Vec3(-look.z, 0.0, look.x).normalize().scale(if (Random.nextBoolean()) DODGE_SPEED else -DODGE_SPEED)
        context.level.sendParticles(ModParticles.mote(TRACE), caster.x, caster.y + 1.0, caster.z, TRACE_MOTES, 0.25, 0.5, 0.25, 0.0)
        caster.setDeltaMovement(side.x, DODGE_LIFT, side.z)
        // The server's own push reaches the client only if it is marked: without this the dodge never moved anyone.
        caster.hurtMarked = true
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f)
    }

    // ── Shared warning throttle ──────────────────────────────────────────────────

    private fun warn(context: AbilityContext, sourceId: UUID, source: net.minecraft.world.entity.Entity) {
        val owner = context.caster.uuid
        val warnings = lastWarnings.getOrPut(owner) { HashMap() }
        val now = context.scheduling.tick
        val last = warnings[sourceId]
        if (last != null && now - last < WARNING_COOLDOWN_TICKS) return
        warnings[sourceId] = now

        val distance = context.caster.position().distanceTo(source.position())
        val volume = (1.0 - (distance / SCAN_RADIUS)).coerceAtLeast(0.3).toFloat()
        privateGlow(context.caster, listOf(source), WARN_GLOW_TICKS)
        // Played from the threat itself: the ear tells the direction, so no word has to.
        privateSound(context.caster, source.position(), SoundEvents.NOTE_BLOCK_BELL.value(), volume.coerceAtLeast(0.6f), 0.5f + volume)
    }

    private companion object {
        const val SCAN_RADIUS = 15.0
        const val PROJECTILE_SCAN_RADIUS = 25.0
        const val SCAN_PERIOD_TICKS = 10
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
