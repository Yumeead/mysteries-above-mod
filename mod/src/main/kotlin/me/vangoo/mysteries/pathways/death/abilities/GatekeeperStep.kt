package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.GatekeeperLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.Retinue
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Death, Seq. 5 (Gatekeeper): **Gatekeeper's Step**.
 *
 * One step throws the Gatekeeper forward; whoever stands in the way is swept aside, whoever stands
 * where they come down is struck.
 *
 * ## The blow lands on landing, not on a timer
 *
 * The plugin struck six ticks after the push — wherever the Gatekeeper happened to be by then, in
 * mid-air over a ravine as often as on the ground. Here a short session follows the flight and
 * strikes the moment the feet touch down, and on the way it shoulders aside everything it passes.
 */
class GatekeeperStep : ActiveAbility(AbilityIdentity.of("death_gatekeeper_step")) {

    override val displayName: Component = Component.literal("Крок Вартового")

    override val spiritualityCost: Int = GatekeeperLore.STEP_COST

    override fun cooldownSeconds(sequence: Sequence): Int = GatekeeperLore.stepCooldownSeconds(sequence)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви кидаєтесь уперед на ${GatekeeperLore.stepDistance(sequence).toInt()} бл., відкидаючи все на шляху.\n" +
            "▪ Хто опиниться поруч у місці приземлення — ${GatekeeperLore.stepImpactDamage(sequence)} шкоди й відкидання",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val look = context.look
        val forward = Vec3(look.x, 0.0, look.z).normalize()
        val damage = GatekeeperLore.stepImpactDamage(context.sequence).toFloat()

        caster.deltaMovement = forward.scale(GatekeeperLore.stepDistance(context.sequence) * VELOCITY_PER_BLOCK).add(0.0, LIFT, 0.0)
        caster.hurtMarked = true
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f)

        context.session {
            var ticks = 0
            while (ticks < MAX_FLIGHT_TICKS) {
                waitTicks(1)
                ticks++
                val player = owner ?: return@session
                trail(player, forward)
                if (ticks >= MIN_FLIGHT_TICKS && player.onGround()) break
            }
            owner?.let { land(it, damage) }
        }
        return AbilityOutcome.Done()
    }

    /** The wind of the step: dust in the pathway's colour, and everyone it passes shoved aside. */
    private fun trail(player: ServerPlayer, forward: Vec3) {
        val level = player.level()
        level.sendParticles(ModParticles.mote(DEATH_COLOR), player.x, player.y + 0.8, player.z, 4, 0.4, 0.5, 0.4, 0.0)
        val side = Vec3(-forward.z, 0.0, forward.x)
        level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(SWEEP)) {
            it !== player && !Retinue.isOwnedBy(player.uuid, it)
        }.forEach { other ->
            val sign = if (other.position().subtract(player.position()).dot(side) >= 0) 1.0 else -1.0
            other.push(side.x * SHOVE * sign, 0.15, side.z * SHOVE * sign)
            other.hurtMarked = true
        }
    }

    private fun land(player: ServerPlayer, damage: Float) {
        val level = player.level()
        level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(IMPACT, 1.0, IMPACT)) {
            it !== player && !Retinue.isOwnedBy(player.uuid, it)
        }.forEach { target ->
            target.hurtServer(level, player.damageSources().playerAttack(player), damage)
            val away = target.position().subtract(player.position()).let { if (it.lengthSqr() < 1.0e-4) Vec3(0.0, 0.0, 0.1) else it }.normalize()
            target.push(away.x * KNOCKBACK, 0.3, away.z * KNOCKBACK)
            target.hurtMarked = true
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.x, player.y + 0.1, player.z, 16, IMPACT / 2, 0.1, IMPACT / 2, 0.02)
        level.playSound(null, player.x, player.y, player.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9f, 0.8f)
    }

    private companion object {
        // ponytail: an empirical calibration, not physics — friction eats the push unevenly by surface.
        const val VELOCITY_PER_BLOCK = 0.32
        const val LIFT = 0.2
        const val MIN_FLIGHT_TICKS = 3
        const val MAX_FLIGHT_TICKS = 30
        const val SWEEP = 1.2
        const val SHOVE = 0.6
        const val IMPACT = 2.5
        const val KNOCKBACK = 0.8
    }
}
