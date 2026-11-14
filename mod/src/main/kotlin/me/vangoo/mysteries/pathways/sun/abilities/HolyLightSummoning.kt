package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.BEAM_REACH_TICKS
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Sun, Seq. 7 (Solar High Priest): **Holy Light**.
 *
 * A pillar of holy fire from the sky into a chosen point. The pathway's strongest single strike —
 * and the only one that **does not work under a roof**.
 *
 * ## The sky is a condition, not decoration
 *
 * The plugin's version drew a pillar of particles anywhere: in a cave, in a bunker, underwater.
 * Here the light really does come from above, so `canSeeSkyFromBelowWater` over the target is a
 * mandatory condition, and the refusal says so outright.
 *
 * That is what the pathway lacked: the Sun had five ways of dealing damage and not one reason to
 * think about **where** you are standing. Now the strongest of them requires going out under the
 * sky, and an opponent has somewhere to hide from it.
 *
 * ## The light comes from the sun
 *
 * By day the pillar falls from the sun's disc ([sunward]): in the morning it slants in from the
 * east, at noon it stands straight. So the condition is not "open sky overhead" but "the sun sees
 * the point" — the shadow of a wall or a tree shelters as a roof does, and in the long shadows of
 * morning and evening there are more places to hide. At night, with no sun to come from, it falls
 * from straight above, and an open sky is enough.
 *
 * ## The light announces itself
 *
 * The pillar does not fall at the moment of the cast. For eight ticks light draws together in the
 * sky over the point — a small sun kindling — and only then the shaft comes down, with a flash for
 * whoever looks and real light where it struck. The damage lands with the shaft, so the telegraph
 * is a promise kept: whoever steps out from under it in time is not struck.
 */
class HolyLightSummoning : ActiveAbility(AbilityIdentity.of("sun_holy_light_summoning")) {

    override val displayName: Component = Component.literal("Святе світло")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стовп святого вогню з неба в точку під прицілом.\n" +
            "▪ Шкода: ${damage(sequence)} по нечисті (радіус ${PILLAR_RADIUS} бл.), знижена по решті\n" +
            "▪ Нечисть горить ${FIRE_SECONDS} с\n" +
            "▪ Спершу світло збирається в небі — мить, за яку можна відійти\n" +
            "▪ Удень стовп падає від сонця, уночі — згори. Ціль у тіні чи під дахом він не дістане",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val aim = context.caster.pick(MAX_RANGE, 1.0f, false)
        // The pillar comes down onto something: aimed at the open air, it would end in nothing.
        if (aim.type == HitResult.Type.MISS) return AbilityOutcome.failed("Світлу нема куди впасти: наведіть приціл на землю")
        val hit = aim.location
        val ground = BlockPos.containing(hit)
        val level = context.level
        val toSun = sunward(level)

        if (!lightReaches(level, hit, toSun, SKY_CHECK)) {
            return AbilityOutcome.failed(
                if (toSun.y < 1.0) "Ця точка в тіні — сонце її не бачить" else "Небо над тією точкою закрите — світлу нема звідки прийти",
            )
        }

        gather(context.scheduling, level, hit.add(toSun.scale(PILLAR_HEIGHT)), ModParticles.light(context.effects.pathwayColor))
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 1.6f)

        // Services, not the context: the strike lands after the cast is over.
        val effects = context.effects
        val scheduling = context.scheduling
        val beyonders = context.beyonders
        val casterId = context.caster.uuid
        val damage = damage(context.sequence)

        scheduling.after(TELEGRAPH_TICKS) {
            effects.beam(level, hit.add(toSun.scale(SUN_REACH)), hit, PILLAR_TICKS, PILLAR_WIDTH, sourceWidth = SUN_WIDTH)
        }
        // Everything else waits for the shaft to land.
        scheduling.after(TELEGRAPH_TICKS + BEAM_REACH_TICKS) {
            effects.flash(level, hit, FLASH_RADIUS, FLASH_STRENGTH)
            effects.flare(level, ground, FLARE_TICKS)
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 0.7f)

            // ponytail: a caster gone from the game in those ticks leaves light with no hand
            // behind it — it falls, and strikes no one. A damage source without a player is the upgrade.
            val caster = level.server.playerList.getPlayer(casterId) ?: return@after
            val top = hit.add(toSun.scale(PILLAR_HEIGHT))
            level.getEntitiesOfClass(LivingEntity::class.java, AABB(hit, top).inflate(PILLAR_RADIUS + 1))
                .filter { inPillar(it, hit, toSun) }
                .forEach { target ->
                    smite(level, caster, beyonders, target, damage)
                    if (target.isProfane(beyonders)) target.igniteForSeconds(FIRE_SECONDS.toFloat())
                }
        }
        return AbilityOutcome.Done()
    }

    /** Whether [target]'s body stands in the shaft that lands at [hit] coming along [toSun] — upright or slanting. */
    private fun inPillar(target: LivingEntity, hit: Vec3, toSun: Vec3): Boolean {
        val body = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
        val along = body.subtract(hit).dot(toSun).coerceIn(0.0, PILLAR_HEIGHT)
        return body.distanceTo(hit.add(toSun.scale(along))) <= PILLAR_RADIUS + target.bbWidth / 2.0
    }

    /**
     * The telegraph: for [TELEGRAPH_TICKS] light draws together at the point in the sky the pillar
     * will fall from. Its core grows brighter tick by tick — each light stands and adds to the last —
     * and whoever looks up has those ticks to step out from under it.
     *
     * A job of its own, not a session: the light does not belong to the caster — once called, it comes.
     */
    private fun gather(scheduling: Scheduling, level: ServerLevel, at: Vec3, light: ParticleOptions) {
        val random = level.random
        var step = 0
        lateinit var handle: TaskHandle
        handle = scheduling.every(1) {
            if (step++ >= TELEGRAPH_TICKS) return@every handle.cancel()
            level.sendParticles(light, at.x, at.y, at.z, 0, 0.0, 0.0, 0.0, 0.0)
            repeat(GATHER_PER_TICK) {
                val dir = Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                val from = at.add(dir.scale(GATHER_RADIUS))
                // `count = 0`: an exact velocity, straight into the centre — the light is drawn in, not scattered.
                level.sendParticles(light, from.x, from.y, from.z, 0, -dir.x, -dir.y, -dir.z, GATHER_SPEED)
            }
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val MAX_RANGE = 20.0
        /** How high up the shaft the light gathers, and how much of it strikes. */
        const val PILLAR_HEIGHT = 15.0
        const val PILLAR_RADIUS = 1.6
        /** How far towards the sun the way must be clear: a hill on the horizon casts no shadow worth checking. */
        const val SKY_CHECK = 64.0
        const val BASE_DAMAGE = 9
        const val COOLDOWN_SECONDS = 18
        const val FIRE_SECONDS = 5

        /** The telegraph: long enough to see and step aside, short enough that standing still is a choice. */
        const val TELEGRAPH_TICKS = 8
        const val GATHER_PER_TICK = 6
        const val GATHER_RADIUS = 2.5
        /** The light brakes as it flies and carries about seven times this — the whole way in. */
        const val GATHER_SPEED = GATHER_RADIUS / 7
        const val PILLAR_TICKS = 10
        const val PILLAR_WIDTH = 2.0f
        const val FLASH_RADIUS = 14.0
        const val FLASH_STRENGTH = 0.8f
        const val FLARE_TICKS = 30
    }
}
