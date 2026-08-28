package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

/**
 * Sun, Посл. 8 (Прохач Світла): **Благословення**.
 *
 * Утримуване світло: поки Прохач платить, усі поруч зцілюються, а скверна поруч —
 * тліє. Гасне, коли скінчилась духовність або коли Прохач скастував удруге.
 *
 * ## Сила, що гасне сама, — це сесія, а не тогл
 *
 * `ToggleablePassiveAbility` тут не годиться: перемикач у ньому лежить **поза**
 * здібністю, і дотягнутись до нього зсередини — коли бак спорожнів — нічим. Сесія
 * навпаки сама вирішує, коли згаснути, і `sustain` повертає причину.
 *
 * ## Ціна вся в утриманні, а не в касті
 *
 * [spiritualityCost] нуль, а платить [periodicCost]. Інакше вимкнути благословення
 * коштувало б окремого касту — тобто гравець платив би за те, щоб **перестати**
 * платити. Перепочинок у три секунди лишається: він проти миготіння, не проти
 * використання.
 */
class Blessing : ActiveAbility(AbilityIdentity.of("sun_blessing")) {

    override val displayName: Component = Component.literal("Благословення")

    /** Каст безкоштовний: ціна цієї сили — утримання, і вимикати її має бути дарма. */
    override val spiritualityCost: Int = 0

    /**
     * Ціна утримання росте з Послідовністю — і мусить, бо разом із нею росте
     * **регенерація**: 2 очки/с на Посл. 8 проти 6 на Посл. 5 (і 9 при повному
     * засвоєнні). Пласка ціна, взята з плагіна, на Посл. 5 виявилась нижчою за
     * відновлення, тобто сила ставала безкоштовною, а смуга духовності повзла назад
     * до стелі просто під час утримання.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Утримуване світло навколо вас: союзники зцілюються, скверна тліє.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Скверні: ${undeadDamage(sequence)} шкоди щосекунди\n" +
            "▪ Утримання: ${periodicCost(sequence)} духовності/с; повторний каст гасить",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Благословення згасло")
        }

        val radius = range(context.sequence)
        val burn = undeadDamage(context.sequence)
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val messaging = context.messaging
        val beyonders = context.beyonders

        context.session {
            try {
                val end = sustain(periodTicks = TICKS_PER_SECOND, upkeep = periodicCost) { priest ->
                    radiate(priest, mote, radius)
                    reach(priest, radius).forEach { nearby ->
                        // Три категорії, не дві: скверні шкода, своїм зцілення, решті
                        // світу — нічого. Корова під благословенням не зцілюється.
                        if (nearby.isProfane(beyonders)) {
                            smite(priest.level() as ServerLevel, priest, beyonders, nearby, burn)
                        } else if (nearby.isFaithful(beyonders)) {
                            nearby.addEffect(
                                MobEffectInstance(MobEffects.REGENERATION, MERCY_TICKS, 0, true, false, true),
                            )
                        }
                    }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Благословення згасло: духовність скінчилась"))
                }
            } finally {
                owner?.let { extinguish(it) }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
        return AbilityOutcome.done("Благословення розгорнулось")
    }

    /** Хто в колі. Береться щотакту наново — коло рухається разом із Прохачем. */
    private fun reach(priest: ServerPlayer, radius: Double): List<LivingEntity> {
        val level = priest.level() as? ServerLevel ?: return emptyList()
        return level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(priest.position(), radius * 2, radius * 2, radius * 2),
        ).filter { it.position().distanceToSqr(priest.position()) <= radius * radius }
    }

    private fun radiate(priest: ServerPlayer, mote: ParticleOptions, radius: Double) {
        val level = priest.level() as? ServerLevel ?: return
        repeat(HALO_POINTS) { i ->
            val angle = TAU * i / HALO_POINTS
            level.sendParticles(
                mote,
                priest.x + radius * Math.cos(angle), priest.y + 0.3, priest.z + radius * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun extinguish(priest: ServerPlayer) {
        (priest.level() as? ServerLevel)?.playSound(
            null, priest.x, priest.y, priest.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.2f,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun undeadDamage(sequence: Sequence): Int =
        scale(BASE_UNDEAD_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RANGE = 10
        const val BASE_PERIODIC_COST = 18
        const val COOLDOWN_SECONDS = 3
        const val BASE_UNDEAD_DAMAGE = 3
        /** Трохи довше за такт, щоб між тактами не блимало. */
        const val MERCY_TICKS = 30
        const val HALO_POINTS = 20
        const val TAU = 2.0 * Math.PI
    }
}
