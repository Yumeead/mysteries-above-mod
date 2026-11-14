package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.burst
import me.vangoo.mysteries.effect.wave
import me.vangoo.mysteries.effect.waveArrival
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 5 (Priest of Light): **Wave of Holiness**.
 *
 * One wave spreading out from the Priest that **treats everyone according to their nature**: it
 * strikes and weakens the unclean, heals everyone else and clears everything harmful off them.
 *
 * ## The pathway's most expensive power, and it is not about damage
 *
 * The damage here is smaller than the High Priest's pillar — because that is not what the wave is
 * for. It saves the party: it clears poison, weakness, bleeding and fire off everyone, tops up
 * health — and at the same time sweeps the surroundings. One button that turns a fight already
 * lost.
 *
 * ## The wave really does spread
 *
 * The effect is applied instantly, and it is visible for under half a second: a ring of light
 * travels from the centre to the edge, lighting the ground as it crosses, and by it one reads who
 * was caught — one's own break into golden sparks as the front reaches them, the unclean into a
 * white flash and smoke. It is drawn by its own jobs in the scheduler, not by a session: the wave
 * no longer belongs to the caster.
 */
class HolyWave : ActiveAbility(AbilityIdentity.of("sun_holy_wave")) {

    override val displayName: Component = Component.literal("Хвиля святості")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Хвиля святого світла на ${RADIUS.toInt()} бл. навколо вас.\n" +
            "▪ Своїм: +${heal(sequence)} здоров'я і знято все шкідливе\n" +
            "▪ Нечисті: ${damage(sequence)} шкоди й Слабкість на " +
            "${WEAKNESS_TICKS / TICKS_PER_SECOND} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val level = context.level
        val centre = context.origin
        val color = context.effects.pathwayColor
        spread(context, centre)

        // Everyone is treated at once — the wave only shows who was caught; the marks on them wait
        // for its front, so a find flares the moment the light reaches it.
        val sparks = ModParticles.mote(color)
        context.targeting.livingNear(RADIUS).forEach { target ->
            val reached = waveArrival(target.distanceTo(context.caster).toDouble(), RADIUS).coerceAtLeast(1)
            when {
                target !== context.caster && target.isProfane(context.beyonders) -> {
                    smite(level, context.caster, context.beyonders, target, damage(context.sequence))
                    target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0))
                    context.scheduling.after(reached) { scorch(target) }
                }
                // The wave saves a party, not a menagerie: a creeper nearby is not healed just
                // because it is not undead.
                target === context.caster || target.isFaithful(context.beyonders) -> {
                    target.heal(heal(context.sequence).toFloat())
                    cleanse(target)
                    context.scheduling.after(reached) { bless(target, sparks) }
                }
            }
        }

        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.8f, 0.6f,
        )
        return AbilityOutcome.Done()
    }

    /**
     * The wave itself: a ring of light running out over the radius at chest height, and real light
     * kindling along its front and going out behind it — the ground lights up as the wave crosses it.
     */
    private fun spread(context: CastContext, centre: Vec3) {
        val level = context.level
        val color = context.effects.pathwayColor
        wave(level, centre.add(0.0, CHEST, 0.0), RADIUS, color)
        burst(level, centre.add(0.0, CHEST, 0.0), 0.4, color, CORE_POINTS, CORE_SPEED)

        val effects = context.effects
        for (ring in 1..FRONT_RINGS) {
            val r = RADIUS * ring / FRONT_RINGS
            context.scheduling.after(waveArrival(r, RADIUS).coerceAtLeast(1)) {
                repeat(FRONT_LIGHTS) { i ->
                    val angle = TAU * (i + ring * 0.5) / FRONT_LIGHTS
                    effects.flare(level, BlockPos.containing(centre.x + r * cos(angle), centre.y + 1, centre.z + r * sin(angle)), FRONT_TICKS, FRONT_BRIGHTNESS)
                }
            }
        }
    }

    /** One of one's own, reached: golden sparks rise off them. */
    private fun bless(target: LivingEntity, sparks: ParticleOptions) {
        val level = target.level() as? ServerLevel ?: return
        level.sendParticles(
            sparks, target.x, target.y + target.bbHeight * 0.5, target.z,
            SPARKS, target.bbWidth * 0.4, target.bbHeight * 0.4, target.bbWidth * 0.4, 0.02,
        )
    }

    /** The unclean, reached: a white flash on them, and smoke. */
    private fun scorch(target: LivingEntity) {
        val level = target.level() as? ServerLevel ?: return
        val chest = target.position().add(0.0, target.bbHeight * 0.6, 0.0)
        burst(level, chest, 0.3, WHITE, SCORCH_POINTS, SCORCH_SPEED)
        level.sendParticles(
            ParticleTypes.LARGE_SMOKE, chest.x, chest.y, chest.z,
            SMOKE, target.bbWidth * 0.3, target.bbHeight * 0.3, target.bbWidth * 0.3, 0.02,
        )
    }

    private fun heal(sequence: Sequence): Int =
        scale(BASE_HEAL, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val RADIUS = 10.0
        const val BASE_HEAL = 6
        const val BASE_DAMAGE = 6
        const val COOLDOWN_SECONDS = 50
        const val WEAKNESS_TICKS = 3 * TICKS_PER_SECOND
        const val CHEST = 1.2
        const val CORE_POINTS = 24
        const val CORE_SPEED = 0.15
        /** Real light along the front at a few distances out, each spot for a moment. */
        const val FRONT_RINGS = 4
        const val FRONT_LIGHTS = 6
        const val FRONT_TICKS = 8
        const val FRONT_BRIGHTNESS = 12
        const val SPARKS = 10
        val WHITE = RgbColor(0xFFFFFF)
        const val SCORCH_POINTS = 14
        const val SCORCH_SPEED = 0.12
        const val SMOKE = 6
        const val TAU = 2.0 * Math.PI
    }
}
