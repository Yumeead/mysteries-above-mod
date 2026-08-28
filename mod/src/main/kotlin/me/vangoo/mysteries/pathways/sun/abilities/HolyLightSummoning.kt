package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Посл. 7 (Сонячний Первосвященник): **Святе світло**.
 *
 * Стовп святого вогню з неба в обрану точку. Найсильніший одиночний удар шляху — і
 * єдиний, який **не працює під дахом**.
 *
 * ## Небо — це умова, а не оформлення
 *
 * Плагінна версія малювала стовп із частинок будь-де: у печері, у бункері, під водою.
 * Тут світло справді приходить згори, тож `canSeeSkyFromBelowWater` над ціллю —
 * обов'язкова умова, і відмова каже про це прямо.
 *
 * Це те, чого шляху бракувало: у Сонця було п'ять способів завдати шкоди й жодного
 * приводу подумати, **де** ти стоїш. Тепер найсильніший з них вимагає вийти під небо,
 * а супротивник має куди від нього сховатись.
 *
 * На 26.2 `Level.canSeeSky` більше немає — лишився `LevelReader.canSeeSkyFromBelowWater`.
 */
class HolyLightSummoning : ActiveAbility(AbilityIdentity.of("sun_holy_light_summoning")) {

    override val displayName: Component = Component.literal("Святе світло")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Стовп святого вогню з неба в точку під прицілом.\n" +
            "▪ Шкода: ${damage(sequence)} по скверні (радіус ${PILLAR_RADIUS} бл.), знижена по решті\n" +
            "▪ Скверна горить ${FIRE_SECONDS} с\n" +
            "▪ Потрібне відкрите небо над ціллю — під дахом не спрацює",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val hit = context.caster.pick(MAX_RANGE, 1.0f, false).location
        val ground = BlockPos.containing(hit)

        if (!context.level.canSeeSkyFromBelowWater(ground.above())) {
            return AbilityOutcome.failed("Небо над тією точкою закрите — світлу нема звідки прийти")
        }

        val mote = ModParticles.mote(context.effects.pathwayColor)
        descend(context, hit, mote)

        val struck = context.level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(hit, PILLAR_RADIUS * 2, PILLAR_HEIGHT, PILLAR_RADIUS * 2),
        ).filter { it.position().distanceToSqr(hit.x, it.y, hit.z) <= PILLAR_RADIUS * PILLAR_RADIUS }

        var burned = 0
        struck.forEach { target ->
            smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
            if (target.isProfane(context.beyonders)) {
                target.igniteForSeconds(FIRE_SECONDS.toFloat())
                burned++
            }
        }

        context.level.playSound(
            null, hit.x, hit.y, hit.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 0.7f,
        )
        return AbilityOutcome.done("Стовп світла впав: ${struck.size} під ним, $burned горить")
    }

    /**
     * Стовп опускається згори вниз за [FALL_TICKS], а не з'являється цілим.
     *
     * Малюється власним завданням у планувальнику, не сесією: стовп не належить
     * кастеру — він уже впав і догорить сам, навіть якщо той вийде з гри.
     */
    private fun descend(context: CastContext, at: Vec3, mote: ParticleOptions) {
        val level = context.level
        var step = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(1) {
            if (step >= FALL_TICKS) return@every handle.cancel()
            val top = at.y + PILLAR_HEIGHT * (1.0 - step.toDouble() / FALL_TICKS)
            repeat(RING_POINTS) { i ->
                val angle = TAU * i / RING_POINTS + step * SWIRL
                level.sendParticles(
                    mote,
                    at.x + PILLAR_RADIUS * cos(angle), top, at.z + PILLAR_RADIUS * sin(angle),
                    1, 0.0, 0.1, 0.0, 0.0,
                )
            }
            level.sendParticles(ParticleTypes.END_ROD, at.x, top, at.z, 2, 0.1, 0.1, 0.1, 0.01)
            step++
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val MAX_RANGE = 20.0
        const val PILLAR_HEIGHT = 15.0
        const val PILLAR_RADIUS = 1.6
        const val BASE_DAMAGE = 9
        const val COOLDOWN_SECONDS = 18
        const val FIRE_SECONDS = 5
        const val FALL_TICKS = 12
        const val RING_POINTS = 10
        const val SWIRL = 0.25
        const val TAU = 2.0 * Math.PI
    }
}
