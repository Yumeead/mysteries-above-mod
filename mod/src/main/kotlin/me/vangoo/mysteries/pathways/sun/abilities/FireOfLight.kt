package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 7 (Solar High Priest): **Fire of Light**.
 *
 * A golden holy flame that **stays standing**: a patch on the ground burns for [BURN_SECONDS] s
 * and strikes everything inside it once a second.
 *
 * ## Why this is not a third way of dealing damage
 *
 * The High Priest already has `Сонячне сяйво` (a beam) and `Святе світло` (a pillar), so a third
 * area strike would be the same thing a third time. The difference here is in the **verb**: the
 * beam and the pillar are "to hit", the Fire is "to take ground". It does not kill whoever runs
 * through; it forbids a corridor, a cave mouth, a bridge.
 *
 * Hence the bigger radius, the smaller damage per trigger than the pillar, and the fact that it
 * burns where the High Priest is no longer looking: the flame lives in its own session and does
 * not ask where the caster is now.
 *
 * The flame is light in both senses: tongues of additive light, and a real light block in the
 * middle of the patch, so a cave mouth held by the Fire is lit for everyone as long as it burns.
 */
class FireOfLight : ActiveAbility(AbilityIdentity.of("sun_fire_of_light")) {

    override val displayName: Component = Component.literal("Вогонь Світла")

    override val spiritualityCost: Int = 110

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Золоте полум'я в точці під прицілом — і воно лишається горіти.\n" +
            "▪ Радіус: ${RADIUS} бл., горить $BURN_SECONDS с\n" +
            "▪ Щосекунди: ${tickDamage(sequence)} по нечисті, знижена по решті\n" +
            "▪ Нечисть у полум'ї спалахує",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // A second fire on top of the first would put the first out — a player has one session.
        if (context.sessionRunning) return AbilityOutcome.failed("Вогонь Світла уже горить")

        val aim = context.caster.pick(MAX_RANGE, 1.0f, false)
        // A fire takes ground: aimed at the open air, it would hang there burning nothing.
        if (aim.type == HitResult.Type.MISS) return AbilityOutcome.failed("Немає де розпалити: наведіть приціл на землю")
        val centre = aim.location
        val light = ModParticles.light(context.effects.pathwayColor)
        val hit = tickDamage(context.sequence)
        val beyonders = context.beyonders
        val level = context.level
        val caster = context.caster

        // The fire lights the place for real, all the while it burns.
        context.effects.flare(level, BlockPos.containing(centre), BURN_SECONDS * TICKS_PER_SECOND)

        context.session {
            repeat(BURN_SECONDS * FLICKERS_PER_SECOND) { flicker ->
                blaze(level, centre, light)
                if (flicker % FLICKERS_PER_SECOND == 0) {
                    level.getEntitiesOfClass(
                        LivingEntity::class.java,
                        AABB.ofSize(centre, RADIUS * 2, HEIGHT, RADIUS * 2),
                    )
                        .filter { it.position().distanceToSqr(centre.x, it.y, centre.z) <= RADIUS * RADIUS }
                        .forEach { target ->
                            smite(level, caster, beyonders, target, hit)
                            if (target.isProfane(beyonders)) target.igniteForSeconds(FIRE_SECONDS.toFloat())
                        }
                }
                waitTicks(TICKS_PER_SECOND / FLICKERS_PER_SECOND)
            }
        }

        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 0.8f,
        )
        return AbilityOutcome.Done()
    }

    /**
     * Tongues of light rising off the patch, scattered anew each flicker — a fire, not a carpet. The
     * light is additive, so where tongues cross the flame burns white at its heart.
     */
    private fun blaze(level: ServerLevel, centre: Vec3, light: ParticleOptions) {
        val random = level.random
        repeat(TONGUES) {
            // Thicker at the middle: a power of 0.5 would spread the tongues evenly over the disc,
            // a larger one pulls them inwards.
            val r = RADIUS * Math.pow(random.nextDouble(), 0.75)
            val angle = TAU * random.nextDouble()
            // `count = 0`: an exact velocity — the tongue rises straight up.
            level.sendParticles(
                light,
                centre.x + r * cos(angle), centre.y + 0.1, centre.z + r * sin(angle),
                0, 0.0, RISE_MIN + random.nextDouble() * RISE_SPREAD, 0.0, 1.0,
            )
        }
    }

    private fun tickDamage(sequence: Sequence): Int =
        scale(BASE_TICK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val MAX_RANGE = 18.0
        const val RADIUS = 4.5
        const val HEIGHT = 4.0
        const val BASE_TICK_DAMAGE = 4
        const val BURN_SECONDS = 7
        const val FIRE_SECONDS = 3
        const val COOLDOWN_SECONDS = 30
        /** Five times a second: one light lives up to fourteen ticks, so the fire never gaps. */
        const val FLICKERS_PER_SECOND = 5
        const val TONGUES = 30
        const val RISE_MIN = 0.08
        const val RISE_SPREAD = 0.1
        const val TAU = 2.0 * Math.PI
    }
}
