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
 * Sun, Посл. 7 (Сонячний Первосвященник): **Вогонь Світла**.
 *
 * Золоте святе полум'я, що **лишається стояти**: пляма на землі горить
 * [BURN_SECONDS] с і б'є все, що в ній, раз на секунду.
 *
 * ## Чому це не третій спосіб завдати шкоди
 *
 * У Первосвященника вже є `Сонячне сяйво` (промінь) і `Святе світло` (стовп), тож
 * третій удар по площі був би тим самим утретє. Різниця тут у **дієслові**: сяйво і
 * стовп — це «влучити», а Вогонь — це «зайняти місце». Він не вбиває того, хто
 * пробігає; він забороняє коридор, вихід із печери, міст.
 *
 * Тому й радіус більший, і шкода за одне спрацювання менша за стовп, і горить воно
 * там, куди Первосвященник уже не дивиться: полум'я живе власною сесією й не питає,
 * де зараз кастер.
 */
class FireOfLight : ActiveAbility(AbilityIdentity.of("sun_fire_of_light")) {

    override val displayName: Component = Component.literal("Вогонь Світла")

    override val spiritualityCost: Int = 110

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Золоте полум'я в точці під прицілом — і воно лишається горіти.\n" +
            "▪ Радіус: ${RADIUS} бл., горить $BURN_SECONDS с\n" +
            "▪ Щосекунди: ${tickDamage(sequence)} по скверні, знижена по решті\n" +
            "▪ Скверна в полум'ї спалахує",
    )

    /** Друге вогнище поверх першого гасить перше — сесія в гравця одна. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val centre = context.caster.pick(MAX_RANGE, 1.0f, false).location
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val hit = tickDamage(context.sequence)
        val beyonders = context.beyonders
        val level = context.level
        val caster = context.caster

        context.session {
            repeat(BURN_SECONDS) {
                blaze(level, centre, mote)
                level.getEntitiesOfClass(
                    LivingEntity::class.java,
                    AABB.ofSize(centre, RADIUS * 2, HEIGHT, RADIUS * 2),
                )
                    .filter { it.position().distanceToSqr(centre.x, it.y, centre.z) <= RADIUS * RADIUS }
                    .forEach { target ->
                        smite(level, caster, beyonders, target, hit)
                        if (target.isProfane(beyonders)) target.igniteForSeconds(FIRE_SECONDS.toFloat())
                    }
                waitTicks(TICKS_PER_SECOND)
            }
        }

        level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 0.8f,
        )
        return AbilityOutcome.done("Вогонь Світла зайнявся на $BURN_SECONDS с")
    }

    /** Пляма горить нерівно: щосекунди інший малюнок, інакше це килим, а не вогонь. */
    private fun blaze(level: ServerLevel, centre: Vec3, mote: ParticleOptions) {
        repeat(TONGUES) { i ->
            // Годинник — тік сервера: `Level.gameTime` на 26.2 більше немає.
            val angle = TAU * i / TONGUES + level.server.tickCount * 0.05
            val r = RADIUS * ((i % 3) + 1) / 3.0
            level.sendParticles(
                mote,
                centre.x + r * cos(angle), centre.y + 0.2, centre.z + r * sin(angle),
                2, 0.1, 0.5, 0.1, 0.02,
            )
        }
        level.sendParticles(
            ParticleTypes.END_ROD,
            centre.x, centre.y + 0.5, centre.z,
            6, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.01,
        )
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
        const val TONGUES = 18
        const val TAU = 2.0 * Math.PI
    }
}
