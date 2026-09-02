package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * Door, Посл. 5 (Мандрівник): **Блимання**.
 *
 * Найпростіша сила Мандрівника й найчастіша: п'ятнадцять блоків у бік погляду, миттєво.
 *
 * ## Безпечне місце шукається, а не гарантується
 *
 * Промінь дає точку, а точка може бути в лаві, в стелі або в повітрі над прірвою. Пошук
 * той самий, що в плагіні — до трьох блоків униз і вгору, — і причина його існування та
 * сама: без нього сила регулярно вбивала б власника, і гравець замість «блимнути» думав
 * би «чи не блимнути в лаву».
 *
 * Опори під ногами тут **не вимагається** навмисно: інакше блимнути з обриву стало б
 * неможливо, а це половина того, для чого сила потрібна.
 */
class Blink : ActiveAbility(AbilityIdentity.of("door_blink")) {

    override val displayName: Component = Component.literal("Блимання")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Миттєве переміщення в бік погляду.\n" +
            "▪ Дальність ${distance(sequence).toInt()} бл.; упреться в стіну — станете перед нею\n" +
            "▪ Перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val reach = distance(context.sequence)
        val look = context.look
        // Крок назад від стіни: рівно в її площину гравець не вміщається.
        val aim = context.caster.pick(reach, 1.0f, false).location.subtract(look.scale(CLEARANCE))

        val landing = safeSpot(context.level, aim)
            ?: return AbilityOutcome.failed("Там немає безпечного місця")

        val from = context.origin
        val mote = ModParticles.mote(context.effects.pathwayColor)

        silhouette(context.level, from, mote)
        context.level.playSound(
            null, from.x, from.y, from.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.8f,
        )

        context.caster.teleportTo(landing.x, landing.y, landing.z)

        silhouette(context.level, landing, mote)
        trail(context.level, from, landing)
        context.level.playSound(
            null, landing.x, landing.y, landing.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 1.2f,
        )

        return AbilityOutcome.done("Ви блимнули на ${from.distanceTo(landing).toInt()} бл.")
    }

    /**
     * Місце, де вміщається гравець: під ноги й під голову потрібне повітря.
     *
     * Перевіряється сама точка, далі вниз до [SEARCH] і, якщо не вийшло, вгору — бо
     * найчастіший промах це «влучив у підлогу», а не «влучив у стелю».
     */
    private fun safeSpot(level: ServerLevel, aim: Vec3): Vec3? {
        val base = BlockPos.containing(aim)
        for (dy in 0..SEARCH) {
            fits(level, base.below(dy))?.let { return it }
            if (dy > 0) fits(level, base.above(dy))?.let { return it }
        }
        return null
    }

    private fun fits(level: ServerLevel, pos: BlockPos): Vec3? {
        if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.above()).isAir) return null
        return Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
    }

    /** Постать, що лишається на місці зникнення й проявляється на місці появи. */
    private fun silhouette(level: ServerLevel, at: Vec3, mote: ParticleOptions) {
        var y = 0.0
        while (y <= BODY_HEIGHT) {
            val width = if (y > SHOULDERS) HEAD_WIDTH else BODY_WIDTH
            var angle = 0.0
            while (angle < TAU) {
                level.sendParticles(
                    mote,
                    at.x + width * Math.cos(angle),
                    at.y + y,
                    at.z + width * Math.sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
                angle += TAU / SILHOUETTE_POINTS
            }
            y += BODY_STEP
        }
    }

    private fun trail(level: ServerLevel, from: Vec3, to: Vec3) {
        val steps = (from.distanceTo(to) / TRAIL_STEP).toInt().coerceIn(1, MAX_TRAIL_STEPS)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps).add(0.0, 1.0, 0.0)
            level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z, 3, 0.1, 0.1, 0.1, 0.0)
        }
    }

    private fun distance(sequence: Sequence): Double =
        scale(BASE_DISTANCE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private companion object {
        const val BASE_DISTANCE = 15
        const val COOLDOWN_SECONDS = 8
        const val CLEARANCE = 0.6
        const val SEARCH = 3
        const val TRAIL_STEP = 0.5
        const val MAX_TRAIL_STEPS = 60
        const val BODY_HEIGHT = 1.8
        const val BODY_STEP = 0.2
        const val BODY_WIDTH = 0.35
        const val HEAD_WIDTH = 0.22
        const val SHOULDERS = 1.5
        const val SILHOUETTE_POINTS = 8
        const val TAU = 2.0 * Math.PI
    }
}
