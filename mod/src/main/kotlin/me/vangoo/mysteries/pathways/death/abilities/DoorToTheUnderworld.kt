package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.GatekeeperLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.Underworld
import me.vangoo.mysteries.pathways.justiciar.rootInPlace
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.AreaEffectCloud
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Death, Seq. 5 (Gatekeeper): **Door to the Underworld**.
 *
 * One gate, four ways to use it. Crouch + cast turns the mode; a plain cast opens the gate where
 * the Gatekeeper looks, and it stands for [GATE_SECONDS]:
 *
 * | mode | what the gate does |
 * |---|---|
 * | **Drag** | pulls everything around towards itself; whoever reaches the threshold is **swallowed** |
 * | **Purge** | swallows hostile projectiles and poisonous clouds, washes harmful effects and fire off people |
 * | **Bind** | hands from under the earth hold everyone near the gate in place |
 * | **Descent** | the Underworld seeps into the world around the gate: darkness and withering for enemies, the Gatekeeper's dead strike harder, the Gatekeeper mends |
 *
 * ## Swallowed means taken
 *
 * The plugin's Drag set a weakened target's health to zero and called it swallowed. Here the gate
 * **leads somewhere**: a player who reaches the threshold is pulled through into the
 * [Underworld] — a real dimension of soul soil and darkness — spends the swallow there under its
 * withering, and is spat back out where the gate stood. A creature already half-dead goes through
 * and does not come back at all. Bosses do not fit through.
 */
class DoorToTheUnderworld : ActiveAbility(AbilityIdentity.of("death_door_to_the_underworld")) {

    private enum class Mode(val title: String) { DRAG("Волочіння"), PURGE("Очищення"), BIND("Скута"), DESCEND("Сходження") }

    override val displayName: Component = Component.literal("Двері в Загробний Світ")

    override val spiritualityCost: Int = GatekeeperLore.DOOR_COST

    override fun cooldownSeconds(sequence: Sequence): Int = GatekeeperLore.doorCooldownSeconds(sequence)

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Mode.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.title)) }

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви ставите двері до Загробного Світу туди, куди дивитесь (до ${GatekeeperLore.doorPlacementRange(sequence).toInt()} бл.), на $GATE_SECONDS с.\n" +
            "▪ Присід + застосування — змінити режим\n" +
            "▪ Волочіння: тягне все на ${GatekeeperLore.dragGateAoeRadius(sequence).toInt()} бл.; хто дійде до порогу — проковтнутий " +
            "(гравець — у Загробний Світ на ${SWALLOW_SECONDS} с, напівмертва істота — назавжди)\n" +
            "▪ Очищення: ковтає ворожі снаряди й отруйні хмари, змиває шкідливі ефекти й вогонь (${GatekeeperLore.purgeRadius(sequence).toInt()} бл.)\n" +
            "▪ Скута: руки з-під землі тримають усіх поруч (${GatekeeperLore.bindRadius(sequence).toInt()} бл.)\n" +
            "▪ Сходження: Загробний Світ проступає на ${GatekeeperLore.descendRadius(sequence).toInt()} бл. на ${GatekeeperLore.descendDurationSeconds(sequence)} с — " +
            "ворогам темрява й Тління, вашим слугам +${(GatekeeperLore.DESCEND_UNDEAD_DAMAGE_BONUS * 100).toInt()}% шкоди, вам лікування",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (caster.isShiftKeyDown) {
            val next = context.modes.cycle(caster.uuid, this, context.sequence)
            context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f)
            return AbilityOutcome.Awaiting(Component.literal("Двері: режим «${next?.name?.string}»"))
        }

        val mode = Mode.entries.firstOrNull { it.name.lowercase() == mode(context) } ?: Mode.DRAG
        val range = GatekeeperLore.doorPlacementRange(context.sequence)
        val hit = caster.pick(range, 1.0f, false)
        val base = if (hit.type == HitResult.Type.BLOCK) hit.location else context.eye.add(context.look.scale(range))
        val facing = Vec3(context.look.x, 0.0, context.look.z).normalize()
        open(context, mode, base, facing)

        context.level.playSound(null, base.x, base.y, base.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.4f)
        context.level.playSound(null, base.x, base.y, base.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 0.3f)
        return AbilityOutcome.done("☠ Двері відчинено: «${mode.title}»")
    }

    private fun open(context: CastContext, mode: Mode, base: Vec3, facing: Vec3) {
        val level = context.level
        val ownerId = context.caster.uuid
        val sequence = context.sequence
        val centre = base.add(0.0, HEIGHT / 2, 0.0)
        val messaging = context.messaging
        val scheduling = context.scheduling
        val seconds = if (mode == Mode.DESCEND) GatekeeperLore.descendDurationSeconds(sequence) else GATE_SECONDS
        val descendRadius = GatekeeperLore.descendRadius(sequence)

        // Descent: the Gatekeeper's own dead strike harder inside the zone.
        val empowered = if (mode == Mode.DESCEND) {
            context.events.on<IncomingDamage>(seconds * TICKS_PER_SECOND) { damage ->
                val attacker = damage.attacker ?: return@on
                if (Retinue.isOwnedBy(ownerId, attacker) && attacker.position().distanceToSqr(centre) <= descendRadius * descendRadius) {
                    damage.scale(1.0 + GatekeeperLore.DESCEND_UNDEAD_DAMAGE_BONUS)
                }
            }
        } else {
            null
        }

        val bound = HashSet<UUID>()
        val swallowed = HashSet<UUID>()
        context.session {
            try {
                var tick = 0
                while (tick < seconds * TICKS_PER_SECOND) {
                    if (tick % DRAW_TICKS == 0) drawGate(level, base, facing, tick)
                    when (mode) {
                        Mode.DRAG -> if (tick % PULL_TICKS == 0) drag(level, ownerId, centre, GatekeeperLore.dragGateAoeRadius(sequence), GatekeeperLore.dragDamage(sequence).toFloat(), swallowed, tick)
                        Mode.PURGE -> if (tick % PULL_TICKS == 0) purge(level, ownerId, centre, GatekeeperLore.purgeRadius(sequence))
                        Mode.BIND -> if (tick % TICKS_PER_SECOND == 0) bind(context, level, ownerId, centre, GatekeeperLore.bindRadius(sequence), bound)
                        Mode.DESCEND -> if (tick % TICKS_PER_SECOND == 0) descend(level, ownerId, centre, descendRadius)
                    }
                    waitTicks(1)
                    tick++
                }
            } finally {
                empowered?.cancel()
                level.sendParticles(ParticleTypes.SCULK_SOUL, centre.x, centre.y, centre.z, 20, 0.6, HEIGHT / 2, 0.6, 0.02)
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.5f, 0.5f)
                owner?.let { messaging.actionBar(it, Component.literal("☠ Двері зачинились")) }
            }
        }
        if (mode == Mode.BIND) scheduling.after(seconds * TICKS_PER_SECOND) { bound.clear() }
    }

    // ── Drag ────────────────────────────────────────────────────────────────────

    private fun drag(level: ServerLevel, ownerId: UUID, centre: Vec3, radius: Double, damage: Float, swallowed: MutableSet<UUID>, tick: Int) {
        val owner = level.server.playerList.getPlayer(ownerId)
        for (victim in near(level, centre, radius, ownerId)) {
            val dx = victim.x - centre.x
            val dz = victim.z - centre.z
            if (dx * dx + dz * dz <= SWALLOW * SWALLOW && victim.uuid !in swallowed) {
                swallow(level, owner, victim, damage)
                swallowed += victim.uuid
                continue
            }
            if (tick % TICKS_PER_SECOND == 0) victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, GatekeeperLore.DRAG_SLOWNESS_SECONDS * TICKS_PER_SECOND, 2))
            val pull = centre.subtract(victim.position())
            if (pull.lengthSqr() < 1.0e-4) continue
            val step = pull.normalize().scale(PULL)
            victim.deltaMovement = Vec3(step.x, maxOf(0.1, step.y), step.z)
            victim.hurtMarked = true
        }
        // The pull is drawn as what it is: soul flame streaming into the threshold.
        repeat(4) {
            val angle = level.random.nextDouble() * Math.PI * 2
            val from = centre.add(Math.cos(angle) * radius * 0.8, level.random.nextDouble() * 2 - 1, Math.sin(angle) * radius * 0.8)
            val toward = centre.subtract(from).scale(0.08)
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 0, toward.x, toward.y, toward.z, 1.0)
        }
    }

    private fun swallow(level: ServerLevel, owner: ServerPlayer?, victim: LivingEntity, damage: Float) {
        val source = owner?.let { level.damageSources().indirectMagic(it, it) } ?: level.damageSources().magic()
        victim.hurtServer(level, source, damage)
        victim.addEffect(MobEffectInstance(MobEffects.WITHER, GatekeeperLore.DRAG_WITHER_SECONDS * TICKS_PER_SECOND, 0))
        level.sendParticles(ParticleTypes.SCULK_SOUL, victim.x, victim.y + 1.0, victim.z, 20, 0.3, 0.6, 0.3, 0.05)
        level.playSound(null, victim.x, victim.y, victim.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.6f, 0.8f)
        when {
            victim is ServerPlayer -> Underworld.swallow(victim, SWALLOW_SECONDS * TICKS_PER_SECOND)
            victim is WitherBoss || victim is EnderDragon -> Unit
            victim.isAlive && victim.health / victim.maxHealth < GatekeeperLore.DRAG_EXECUTE_HP_FRACTION -> victim.discard()
        }
    }

    // ── Purge ───────────────────────────────────────────────────────────────────

    private fun purge(level: ServerLevel, ownerId: UUID, centre: Vec3, radius: Double) {
        val box = AABB.ofSize(centre, radius * 2, radius * 2, radius * 2)
        level.getEntitiesOfClass(AreaEffectCloud::class.java, box).forEach { it.discard() }
        // Whatever flies at the Gatekeeper's side is swallowed on the way; their own shots fly on.
        level.getEntitiesOfClass(Projectile::class.java, box) { it.owner?.uuid != ownerId && !Retinue.isOwnedBy(ownerId, it.owner ?: it) }
            .forEach { projectile ->
                level.sendParticles(ParticleTypes.SOUL, projectile.x, projectile.y, projectile.z, 3, 0.1, 0.1, 0.1, 0.01)
                projectile.discard()
            }
        level.getEntitiesOfClass(LivingEntity::class.java, box) { it is ServerPlayer || Retinue.isOwnedBy(ownerId, it) }.forEach { ally ->
            ally.clearFire()
            ally.activeEffects.filter { it.effect.value().category == MobEffectCategory.HARMFUL }.map { it.effect }.forEach(ally::removeEffect)
        }
        if (level.random.nextInt(4) == 0) level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.2f)
    }

    // ── Bind ────────────────────────────────────────────────────────────────────

    private fun bind(context: CastContext, level: ServerLevel, ownerId: UUID, centre: Vec3, radius: Double, bound: MutableSet<UUID>) {
        val ticks = GatekeeperLore.BIND_DURATION_SECONDS * TICKS_PER_SECOND
        for (victim in near(level, centre, radius, ownerId)) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, victim.x, victim.y + 0.2, victim.z, 4, 0.3, 0.2, 0.3, 0.01)
            level.sendParticles(ModParticles.glyph(DEATH_COLOR.shade(-0.2)), victim.x, victim.y + 0.5, victim.z, 3, 0.25, 0.3, 0.25, 0.0)
            if (!bound.add(victim.uuid)) continue
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, GatekeeperLore.BIND_SLOWNESS_AMPLIFIER))
            context.rootInPlace(victim, ticks)
            context.scheduling.after(ticks) { bound.remove(victim.uuid) }
        }
    }

    // ── Descent ─────────────────────────────────────────────────────────────────

    private fun descend(level: ServerLevel, ownerId: UUID, centre: Vec3, radius: Double) {
        val reach = radius * radius
        level.server.playerList.getPlayer(ownerId)?.takeIf { it.level() === level && it.position().distanceToSqr(centre) <= reach }?.let {
            it.heal(GatekeeperLore.DESCEND_OWNER_HEAL_PER_SECOND.toFloat())
        }
        for (victim in near(level, centre, radius, ownerId)) {
            victim.addEffect(MobEffectInstance(MobEffects.DARKNESS, REFRESH_TICKS, 0, false, false, true))
            victim.addEffect(MobEffectInstance(MobEffects.WITHER, REFRESH_TICKS, 0, false, true, true))
        }
        repeat(DESCENT_FLAMES) {
            val angle = level.random.nextDouble() * Math.PI * 2
            val r = radius * Math.sqrt(level.random.nextDouble())
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x + Math.cos(angle) * r, centre.y - HEIGHT / 2 + 0.1, centre.z + Math.sin(angle) * r, 1, 0.0, 0.05, 0.0, 0.0)
        }
        level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 6, radius / 3, 0.5, radius / 3, 0.0)
        if (level.random.nextInt(3) == 0) level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8f, 0.6f)
    }

    /** Everyone the gate may touch: not the Gatekeeper, not their dead, not the spirits. */
    private fun near(level: ServerLevel, centre: Vec3, radius: Double, ownerId: UUID): List<LivingEntity> {
        val reach = radius * radius
        return level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(centre, radius * 2, radius * 2, radius * 2)) {
            it.isAlive && it.uuid != ownerId && !Retinue.isOwnedBy(ownerId, it) && !Spirits.isSpirit(it) &&
                !(it is ServerPlayer && it.isSpectator) && it.position().distanceToSqr(centre) <= reach
        }
    }

    /**
     * The gate: a dark frame in the pathway's colour standing across the Gatekeeper's line of sight,
     * deeper at the threshold and paler at the lintel, with a slow wave running along it.
     */
    private fun drawGate(level: ServerLevel, base: Vec3, facing: Vec3, tick: Int) {
        val across = Vec3(-facing.z, 0.0, facing.x)
        val half = WIDTH / 2
        val outline = buildList {
            var y = 0.0
            while (y <= HEIGHT) { add(-half to y); add(half to y); y += SPACING }
            var x = -half
            while (x <= half) { add(x to HEIGHT); x += SPACING }
        }
        for ((side, up) in outline) {
            val wave = Math.sin(up * 1.4 - tick * 0.15) * 0.15
            val shade = -0.45 + 0.55 * (up / HEIGHT) + wave
            val point = base.add(across.scale(side)).add(0.0, up, 0.0)
            level.sendParticles(ModParticles.glyph(DEATH_COLOR.shade(shade)), point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
        // The dark inside the frame.
        repeat(PANE) {
            val point = base.add(across.scale((level.random.nextDouble() - 0.5) * WIDTH * 0.85)).add(0.0, level.random.nextDouble() * HEIGHT * 0.95, 0.0)
            level.sendParticles(ParticleTypes.SQUID_INK, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private companion object {
        const val GATE_SECONDS = 30
        const val SWALLOW_SECONDS = 8
        const val WIDTH = 2.2
        const val HEIGHT = 3.0
        const val SPACING = 0.12
        const val PANE = 6
        const val SWALLOW = 1.5
        const val PULL = 0.4
        const val DRAW_TICKS = 3
        const val PULL_TICKS = 4
        const val REFRESH_TICKS = 45
        const val DESCENT_FLAMES = 8
    }
}
