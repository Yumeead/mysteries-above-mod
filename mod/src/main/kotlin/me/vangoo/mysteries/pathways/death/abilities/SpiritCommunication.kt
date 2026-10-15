package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.GravediggerLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.DeathTraces
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.payBranch
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Death, Seq. 8 (Gravedigger): **Spirit Communication**.
 *
 * "Able to communicate with nearby Spirits, allowing them to provide help… can command them to
 * tightly grasp onto a target to immobilize them." The Gravedigger does not wield spirits yet (that
 * is Seq. 7) — they negotiate: a spirit circles above, never fights on its own, and when the
 * Gravedigger strikes someone it goes and holds that one down.
 *
 * ## Where the spirit comes from — and why that is cheaper near the dead
 *
 * The plugin recruited a wandering spirit if one happened to float nearby and otherwise summoned one
 * from the Spirit World, dearer. Wandering spirits do not exist in the mod's world on their own,
 * but **death does**, and [DeathTraces] remembers where: a spirit rises from a fresh death within
 * reach at the recruiting price. A graveyard after a battle is where a Gravedigger is strongest,
 * which is exactly what a Gravedigger should feel like. Any vex counts too ([Spirits]) — an
 * evoker's spirit can be talked over.
 *
 * Sneak + cast lets the whole company go.
 */
class SpiritCommunication : ActiveAbility(AbilityIdentity.of("death_spirit_communication")) {

    override val displayName: Component = Component.literal("Призив простого духа")

    override val spiritualityCost: Int = GravediggerLore.SPIRIT_RECRUIT_COST

    override fun cooldownSeconds(sequence: Sequence): Int = GravediggerLore.spiritCooldownSeconds(sequence)

    private class Bound(val vex: Vex, val phase: Double) {
        var victimId: UUID? = null
        var until: Long = 0L
    }

    private val companions = HashMap<UUID, MutableList<Bound>>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Дух кружляє над вами й сам не б'ється, але коли ви когось ударили — летить і тримає ціль.\n" +
            "▪ Духів: до ${GravediggerLore.spiritLimit(sequence)}; хват — Повільність IV на ${GravediggerLore.graspSeconds(sequence)} с, кожен ваш удар поновлює\n" +
            "▪ Дух із місця недавньої смерті чи вільний дух поруч (${GravediggerLore.spiritRecruitRadius(sequence).toInt()} бл.): " +
            "${GravediggerLore.SPIRIT_RECRUIT_COST} духовності\n" +
            "▪ Дух зі Світу Духів: ${GravediggerLore.SPIRIT_SUMMON_COST} духовності\n" +
            "▪ Присід + застосування відпускає всіх",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (caster.isShiftKeyDown) {
            if (!context.sessionRunning) return AbilityOutcome.failed("Вас ніхто не супроводжує")
            context.stopSession()
            return AbilityOutcome.Awaiting(Component.literal("✦ Духи розсіялись"))
        }

        val sequence = context.sequence
        val limit = GravediggerLore.spiritLimit(sequence)
        val company = companions[caster.uuid].orEmpty()
        if (context.sessionRunning && company.size >= limit) {
            return AbilityOutcome.failed("Більше духів вас не слухає ($limit/$limit)")
        }

        val level = context.level
        val radius = GravediggerLore.spiritRecruitRadius(sequence)
        val freeVex = level.getEntitiesOfClass(Vex::class.java, caster.boundingBox.inflate(radius)) { vex ->
            vex.isAlive && !Retinue.isServant(vex) && companions.values.none { list -> list.any { it.vex === vex } }
        }.minByOrNull { it.distanceToSqr(caster) }
        val grave = if (freeVex == null) DeathTraces.near(level.dimension(), context.origin, radius, DeathTraces.SOUL_TICKS).firstOrNull() else null

        val price = if (freeVex != null || grave != null) GravediggerLore.SPIRIT_RECRUIT_COST else GravediggerLore.SPIRIT_SUMMON_COST
        if (!context.payBranch(price, cooldownSeconds(sequence))) {
            return AbilityOutcome.failed("Недостатньо духовності: потрібно $price")
        }

        val (vex, how) = when {
            freeVex != null -> freeVex.also(Spirits::bind) to "Вільний дух пристав до вас"
            grave != null -> Spirits.summon(level, grave.pos.add(0.0, 1.0, 0.0)) to "Дух піднявся з місця, де загинув ${grave.name}"
            else -> Spirits.summon(level, context.origin.add(0.0, 2.0, 0.0)) to "Дух прийшов зі Світу Духів"
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, vex.x, vex.y + 0.4, vex.z, 14, 0.3, 0.5, 0.3, 0.03)
        level.playSound(null, vex.x, vex.y, vex.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.9f, 0.7f)

        val list = companions.getOrPut(caster.uuid) { mutableListOf() }
        list += Bound(vex, caster.random.nextDouble() * 2 * PI)
        if (!context.sessionRunning) lead(context, list)
        return AbilityOutcome.done("✦ $how (${list.size}/$limit)")
    }

    /** The company's life: one session for all of it, ending when the last spirit is gone. */
    private fun lead(context: CastContext, list: MutableList<Bound>) {
        val ownerId = context.caster.uuid
        val scheduling = context.scheduling
        val beyonders = context.beyonders
        val messaging = context.messaging

        // Every blow of the owner is an order: the spirit already holding that victim keeps holding
        // it longer, otherwise the first free spirit takes it.
        val trigger = context.events.on<IncomingDamage> { damage ->
            if (damage.attacker?.uuid != ownerId) return@on
            val victim = damage.victim
            if (victim.uuid == ownerId || list.any { it.vex === victim }) return@on
            val sequence = beyonders.of(ownerId)?.sequence ?: return@on
            val until = scheduling.tick + GravediggerLore.graspSeconds(sequence) * TICKS_PER_SECOND
            list.firstOrNull { it.victimId == victim.uuid }?.let { it.until = until; return@on }
            val free = list.firstOrNull { it.victimId == null || it.until <= scheduling.tick } ?: return@on
            free.victimId = victim.uuid
            free.until = until
            free.vex.level().playSound(null, free.vex.x, free.vex.y, free.vex.z, SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 0.7f, 0.6f)
        }

        context.session {
            var tick = 0L
            try {
                while (true) {
                    val player = owner ?: break
                    list.removeIf { !it.vex.isAlive || it.vex.isRemoved || it.vex.level() !== player.level() }
                    if (list.isEmpty()) break
                    for (bound in list) {
                        Spirits.hold(bound.vex)
                        val victim = victimOf(bound, scheduling)
                        if (victim != null) grasp(bound, victim, tick) else follow(bound, player, tick)
                    }
                    tick++
                    waitTicks(1)
                }
            } finally {
                trigger.cancel()
                list.forEach { bound ->
                    val vex = bound.vex
                    (vex.level() as? ServerLevel)?.sendParticles(ParticleTypes.SCULK_SOUL, vex.x, vex.y, vex.z, 16, 0.3, 0.5, 0.3, 0.02)
                    vex.discard()
                }
                list.clear()
                companions.remove(ownerId)
                owner?.let { messaging.actionBar(it, Component.literal("✦ Духи розсіялись")) }
            }
        }
    }

    private fun victimOf(bound: Bound, scheduling: Scheduling): LivingEntity? {
        val id = bound.victimId ?: return null
        val victim = (bound.vex.level() as ServerLevel).getEntity(id) as? LivingEntity
        if (victim == null || !victim.isAlive || bound.until <= scheduling.tick || victim.level() !== bound.vex.level()) {
            bound.victimId = null
            return null
        }
        return victim
    }

    /** At rest a spirit circles slowly above its owner's head — out of reach of the owner's own swings. */
    private fun follow(bound: Bound, owner: ServerPlayer, tick: Long) {
        if (!leash(bound.vex, owner.position(), GravediggerLore.SPIRIT_TELEPORT_DISTANCE)) return
        steer(bound.vex, orbit(owner.position(), GravediggerLore.SPIRIT_FOLLOW_DISTANCE, owner.bbHeight + CLEARANCE, bound, FOLLOW_STEP, tick))
    }

    /** The grasp: a fast circle over the victim's head, holding it down from above. */
    private fun grasp(bound: Bound, victim: LivingEntity, tick: Long) {
        val vex = bound.vex
        if (!leash(vex, victim.position(), GRASP_LEASH)) return
        steer(vex, orbit(victim.position(), GRASP_ORBIT, victim.bbHeight + CLEARANCE, bound, GRASP_STEP, tick))
        // The hands reach down from above, so only the horizontal distance counts.
        if (hypot(vex.x - victim.x, vex.z - victim.z) > GravediggerLore.GRASP_REACH) return
        victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, HOLD_REFRESH_TICKS, GravediggerLore.GRASP_SLOWNESS_AMPLIFIER, false, true, true))
        if (tick % HANDS_EVERY == 0L) {
            val level = victim.level() as ServerLevel
            level.sendParticles(ParticleTypes.SCULK_SOUL, victim.x, victim.y + 0.2, victim.z, 6, 0.35, 0.3, 0.35, 0.01)
            level.sendParticles(ModParticles.glyph(DEATH_COLOR), victim.x, victim.y + 0.6, victim.z, 4, 0.3, 0.4, 0.3, 0.0)
        }
    }

    private fun orbit(center: Vec3, radius: Double, height: Double, bound: Bound, step: Double, tick: Long): Vec3 {
        val angle = tick * step + bound.phase
        return center.add(cos(angle) * radius, height + sin(angle * 2) * BOB, sin(angle) * radius)
    }

    private fun steer(vex: Vex, point: Vec3) {
        val delta = point.subtract(vex.position())
        val distance = delta.length()
        if (distance < 0.25) {
            vex.deltaMovement = vex.deltaMovement.scale(0.4)
            return
        }
        vex.deltaMovement = delta.scale(min(MAX_SPEED, 0.25 + distance * 0.25) / distance)
        vex.yRot = (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0).toFloat()
        vex.yBodyRot = vex.yRot
    }

    /** Too far behind — it jumps rather than flies: walls and water would lose it otherwise. */
    private fun leash(vex: Vex, anchor: Vec3, leash: Double): Boolean {
        if (vex.position().distanceToSqr(anchor) <= leash * leash) return true
        vex.teleportTo(anchor.x, anchor.y + 2.0, anchor.z)
        return false
    }

    private companion object {
        const val CLEARANCE = 1.2
        const val BOB = 0.35
        const val MAX_SPEED = 0.9
        const val GRASP_ORBIT = 1.4
        const val GRASP_LEASH = 24.0
        const val FOLLOW_STEP = 2 * PI / 160
        const val GRASP_STEP = 2 * PI / 50
        const val HOLD_REFRESH_TICKS = 30
        const val HANDS_EVERY = 10L
    }
}
