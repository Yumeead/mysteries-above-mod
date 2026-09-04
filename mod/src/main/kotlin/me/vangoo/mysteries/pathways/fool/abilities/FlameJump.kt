package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.FlameJumpRange
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.math.min

/**
 * Fool, Seq. 7 (Magician): **Flame Jump**.
 *
 * Steps through any flame in a cone ahead — a torch, a campfire, a jack-o'-lantern — and steps
 * out next to it, unburned for as long as they stay standing in it.
 */
class FlameJump : ActiveAbility(AbilityIdentity.of("fool_flame_jump")) {

    override val displayName: Component = Component.literal("Стрибок крізь полум'я")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Телепортує до найближчого джерела вогню в напрямку погляду (${FlameJumpRange.rangeFor(sequence)} бл.).\n" +
            "▪ Факели, вогнища, ліхтарі, гарбузові ліхтарі\n" +
            "▪ Полум'я, куди ви стрибнули, не пече, поки ви з нього не вийшли",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val level = context.level
        val radius = FlameJumpRange.rangeFor(context.sequence)
        val origin = context.caster.position()
        val look = context.look

        val fire = nearestFireInCone(level, origin, look, radius)
            ?: run {
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.5f)
                return AbilityOutcome.failed("Немає джерел вогню в напрямку погляду!")
            }

        val safe = safeSpotNear(level, fire) ?: return AbilityOutcome.failed("Немає безпечного місця біля вогню")

        level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + 1.0, origin.z, 30, 0.3, 0.5, 0.3, 0.02)
        level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 1.0, origin.z, 20, 0.4, 0.6, 0.4, 0.02)
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 1.2f)

        context.caster.teleportTo(safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5)

        level.sendParticles(ParticleTypes.FLAME, safe.x + 0.5, safe.y + 0.5, safe.z + 0.5, 25, 0.3, 0.3, 0.3, 0.02)
        level.sendParticles(ParticleTypes.LAVA, safe.x + 0.5, safe.y + 1.0, safe.z + 0.5, 10, 0.2, 0.3, 0.2, 0.0)
        level.playSound(null, safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.8f)

        context.session {
            sustain(periodTicks = 2, durationTicks = FIRE_IMMUNITY_MAX_TICKS) { player ->
                if (standingInFire(player.level(), player.blockPosition())) {
                    player.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0))
                    player.clearFire()
                }
            }
        }

        return AbilityOutcome.done("Стрибок крізь полум'я")
    }

    private fun nearestFireInCone(level: ServerLevel, from: Vec3, look: Vec3, radius: Int): BlockPos? {
        val corner = BlockPos.containing(from.x - radius, from.y - radius / 2.0, from.z - radius)
        val opposite = BlockPos.containing(from.x + radius, from.y + radius / 2.0, from.z + radius)

        var best: BlockPos? = null
        var bestScore = Double.MAX_VALUE
        for (pos in BlockPos.betweenClosed(corner, opposite)) {
            if (level.getBlockState(pos).block !in FIRE_SOURCES) continue
            val center = Vec3.atCenterOf(pos)
            val distance = from.distanceTo(center)
            if (distance < 2.0 || distance > radius) continue

            val toBlock = center.subtract(from).normalize()
            val angle = Math.toDegrees(acos(min(1.0, look.dot(toBlock).coerceAtLeast(-1.0))))
            if (angle > CONE_ANGLE_DEGREES) continue

            val score = distance * (1.0 + angle / CONE_ANGLE_DEGREES)
            if (score < bestScore) {
                bestScore = score
                best = pos.immutable()
            }
        }
        return best
    }

    private fun safeSpotNear(level: ServerLevel, fire: BlockPos): BlockPos? {
        if (isSafe(level, fire)) return fire
        for ((dx, dz) in NEIGHBOR_OFFSETS) {
            val at = fire.offset(dx, 0, dz)
            if (isSafe(level, at)) return at
            val above = at.above()
            if (isSafe(level, above)) return above
        }
        return null
    }

    private fun isSafe(level: ServerLevel, pos: BlockPos): Boolean {
        val feet = level.getBlockState(pos)
        val head = level.getBlockState(pos.above())
        val ground = level.getBlockState(pos.below())
        return !feet.blocksMotion() && !head.blocksMotion() && ground.blocksMotion() &&
            feet.block != Blocks.LAVA && head.block != Blocks.LAVA
    }

    private fun standingInFire(level: ServerLevel, pos: BlockPos): Boolean {
        val at = level.getBlockState(pos).block
        val below = level.getBlockState(pos.below()).block
        return at in STANDING_FIRE || below in STANDING_FIRE
    }

    private companion object {
        const val BASE_COST = 70
        const val BASE_COOLDOWN_SECONDS = 6
        const val CONE_ANGLE_DEGREES = 60.0
        const val FIRE_IMMUNITY_MAX_TICKS = 300

        val FIRE_SOURCES: Set<Block> = setOf(
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.CANDLE, Blocks.JACK_O_LANTERN,
        )

        val STANDING_FIRE: Set<Block> = setOf(
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.MAGMA_BLOCK,
        )

        val NEIGHBOR_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
    }
}
