package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.AirShot
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 7 (Magician): **Air Bullet**.
 *
 * Compressed air, fired as a round — Klein Moretti's "finger gun". The round is never seen as a
 * thing, as in the novel: it is seen as the air it bends. The client draws it ([WorldEffectPayload.AirShot]);
 * the server decides where it goes and when it lands.
 *
 * It **flies** — [AirShot.SPEED] blocks a tick, so a shot across the whole range takes three — and
 * the blow lands when it gets there, on whoever is still in its path: a body that stepped out of the
 * line in those ticks is missed. A wall **stops** it: the old instant hit went through anything solid.
 *
 * Sneaking spends a little more on a heavier shot; the ticket already covers the cheap form, the extra
 * is paid in the cast itself the same way `Verdict` and `PowerProhibition` pay for their pricier
 * branches, so a caster who cannot afford the powered shot still gets the ordinary one.
 */
class AirBullet : ActiveAbility(AbilityIdentity.of("fool_air_bullet")) {

    override val displayName: Component = Component.literal("Повітряна куля")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стискає повітря в кулю й стріляє нею з пальця в ціль під прицілом. Куля летить, а стіна її зупиняє.\n" +
            "▪ Шкода ${damageFor(sequence)}, дальність ${MAX_RANGE.toInt()} бл.\n" +
            "▪ Присід — посилений постріл: x2 шкода й вартість",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        var damage = damageFor(context.sequence)
        val powered = context.caster.isShiftKeyDown && context.beyonder.payUpkeep(BASE_COST)
        if (powered) damage *= 2

        val level = context.level
        val caster = context.caster
        val eye = context.eye
        val look = context.look
        val reach = eye.add(look.scale(MAX_RANGE))
        val muzzle = eye.add(look.scale(MUZZLE)).add(0.0, MUZZLE_DROP, 0.0)

        // The first solid thing on the line, and the body under the crosshair if it stands before it.
        val wall = level.clip(ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
            .takeIf { it.type != HitResult.Type.MISS }
        val target = context.targeting.lookingAt(MAX_RANGE)
        val struck = target?.let { body -> body.boundingBox.inflate(BODY_SLACK).clip(eye, reach).orElse(body.boundingBox.center) }
            ?.takeIf { wall == null || eye.distanceTo(it) < eye.distanceTo(wall.location) }
        val end = when {
            struck != null -> AirShot.End.BODY
            wall != null -> AirShot.End.WALL
            else -> AirShot.End.AIR
        }
        val impact = struck ?: wall?.location ?: reach
        val ticks = AirShot.flight(muzzle.distanceTo(impact))
        ModChannels.WORLD_EFFECT.sendTo(
            PlayerLookup.around(level, muzzle.add(impact).scale(0.5), muzzle.distanceTo(impact) / 2 + AUDIENCE),
            AirShot(muzzle, impact, ticks, powered, end),
        )

        // A dry crack at the finger — twice for the heavier shot.
        crack(level, muzzle, powered)
        if (powered) context.scheduling.after(DOUBLE_CRACK_TICKS) { crack(level, muzzle, powered = false) }
        whistle(context, muzzle, impact, ticks)

        val source = level.damageSources().indirectMagic(caster, caster)
        val knockback = look.scale(if (powered) KNOCKBACK * 1.5 else KNOCKBACK)
        val wallAt = wall?.blockPos
        val breaker = -1 - caster.id
        val scheduling = context.scheduling
        scheduling.after(ticks) {
            when {
                target != null && struck != null -> strike(level, target, muzzle, impact, look, source, damage, knockback)
                wallAt != null -> stopped(level, wallAt, impact, breaker, scheduling)
                else -> level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.4f, 1.6f)
            }
        }
        return AbilityOutcome.Done()
    }

    private fun crack(level: ServerLevel, at: Vec3, powered: Boolean) =
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5f, if (powered) 1.1f else 1.5f)

    /**
     * Everyone the bullet flies past hears it go by — from the point of the track nearest to them, at
     * the tick it passes there. Only them: the shooter has the crack, the target has the blow.
     */
    private fun whistle(context: CastContext, from: Vec3, to: Vec3, ticks: Int) {
        val span = to.subtract(from)
        val length = span.length()
        if (length < 1e-6) return
        val dir = span.scale(1 / length)
        for (listener in context.level.players()) {
            if (listener === context.caster) continue
            val along = listener.eyePosition.subtract(from).dot(dir).coerceIn(0.0, length)
            val nearest = from.add(dir.scale(along))
            if (nearest.distanceTo(listener.eyePosition) > WHISTLE_RANGE) continue
            val id = listener.uuid
            val players = context.level.server.playerList
            context.scheduling.after((ticks * along / length).toInt()) {
                players.getPlayer(id)?.let { privateSound(it, nearest, SoundEvents.ARROW_SHOOT, 0.8f, 1.9f) }
            }
        }
    }

    /** The bullet arrives: whoever is still on its line takes it, and is thrown back. */
    private fun strike(
        level: ServerLevel,
        target: LivingEntity,
        from: Vec3,
        impact: Vec3,
        look: Vec3,
        source: DamageSource,
        damage: Int,
        knockback: Vec3,
    ) {
        val past = impact.add(look.scale(BODY_SLACK * 4))
        if (!target.isAlive || target.level() !== level || target.boundingBox.inflate(BODY_SLACK).clip(from, past).isEmpty) {
            // It stepped out of the line: the bullet spends itself where it was.
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 1.4f)
            return
        }
        target.hurtServer(level, source, damage.toFloat())
        target.setDeltaMovement(knockback.x, LIFT, knockback.z)
        target.hurtMarked = true
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.8f)
        level.sendParticles(ParticleTypes.POOF, impact.x, impact.y, impact.z, 6, 0.2, 0.2, 0.2, 0.02)
    }

    /** The bullet stops at a wall: dust off its face and a short crack in it, gone in a second. */
    private fun stopped(level: ServerLevel, pos: BlockPos, impact: Vec3, breaker: Int, scheduling: Scheduling) {
        val state = level.getBlockState(pos)
        if (state.isAir) return
        level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), impact.x, impact.y, impact.z, 12, 0.15, 0.15, 0.15, 0.1)
        level.playSound(null, impact.x, impact.y, impact.z, state.soundType.hitSound, SoundSource.BLOCKS, 1.0f, 0.7f)
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6f, 1.2f)
        level.destroyBlockProgress(breaker, pos, CRACK_STAGE)
        scheduling.after(CRACK_TICKS) { level.destroyBlockProgress(breaker, pos, -1) }
    }

    private fun damageFor(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_COST = 35
        const val BASE_COOLDOWN_SECONDS = 2
        const val BASE_DAMAGE = 5
        const val MAX_RANGE = 25.0
        const val KNOCKBACK = 0.8
        const val LIFT = 0.3
        const val MUZZLE = 0.7
        const val MUZZLE_DROP = -0.15

        /** How much wider than a body its hitbox counts for the bullet. */
        const val BODY_SLACK = 0.3
        const val AUDIENCE = 32.0
        const val WHISTLE_RANGE = 6.0
        const val DOUBLE_CRACK_TICKS = 2

        /** How deep the crack runs into the wall (of vanilla's 0–9), and how long it shows. */
        const val CRACK_STAGE = 3
        const val CRACK_TICKS = 20
    }
}
