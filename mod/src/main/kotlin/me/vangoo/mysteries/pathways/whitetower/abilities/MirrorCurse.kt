package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * WhiteTower, Seq. 5 (Master of Mysticism): **Mirror Curse**.
 *
 * A brass mirror stands between you and the target — and shows it what it has already done to
 * itself. The more the target has managed to lose, the harder it is to look at its own reflection.
 *
 * ## An ability that punishes being wounded rather than being tough
 *
 * The damage here is [BASE_DAMAGE] plus [MISSING_HEALTH_FACTOR] of the health **already lost**.
 * Against full health it is almost nothing; against someone just knocked down it is a finisher.
 * The mechanic is deliberately not universal: the Master does not strike first, they finish.
 *
 * ## The mirror is a real plane, not a puff
 *
 * The plugin could send a particle to a point, so the "mirror" there was a scatter of droplets
 * around the target. Here the plane is computed from the look direction: the normal is the line
 * to the target, and the grid of motes stands **facing** the caster. Walking around the mirror
 * means seeing it edge-on — exactly how a mirror should look.
 */
class MirrorCurse : ActiveAbility(AbilityIdentity.of("whitetower_mirror_curse")) {

    override val displayName: Component = Component.literal("Дзеркальне прокляття")

    override val spiritualityCost: Int = 150

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Латунне дзеркало показує цілі її власні рани — і вони відкриваються знову.\n" +
            "▪ Шкода: ${damageOf(sequence, 0f)} + ${(MISSING_HEALTH_FACTOR * 100).roundToInt()}% " +
            "від утраченого ціллю здоров'я\n" +
            "▪ Умова: ціль мусить бути поранена, у межах ${RANGE.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Дзеркалу нема кого показувати")

        val missing = target.maxHealth - target.health
        if (missing < MIN_WOUND) {
            return AbilityOutcome.failed("${target.name.string} неушкоджений — дзеркало порожнє")
        }

        val damage = damageOf(context.sequence, missing)
        mirror(context, target)
        target.hurtServer(
            context.level,
            context.level.damageSources().indirectMagic(context.caster, context.caster),
            damage.toFloat(),
        )

        context.messaging.actionBar(target.uuid, Component.literal("Ви бачите себе"))
        return AbilityOutcome.done("Дзеркало показало ${target.name.string} його рани (${damage} шкоди)")
    }

    /** A grid of motes halfway to the target, turned to face the caster. Then it cracks. */
    private fun mirror(context: CastContext, target: LivingEntity) {
        val level = context.level
        val from = context.eye
        val to = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        val normal = to.subtract(from).normalize()
        // The world's vertical as the anchor: the mirror's horizontal edge is always horizontal,
        // even when the Master looks upwards. A gaze exactly along the Y axis is impossible here —
        // the target stands on the ground and the caster looks at it.
        val right = normal.cross(UP).normalize()
        val up = right.cross(normal).normalize()
        val centre = from.lerp(to, 0.5)
        val mote = ModParticles.mote(context.effects.pathwayColor)

        for (u in -HALF..HALF) {
            for (v in -HALF..HALF) {
                val point = centre
                    .add(right.scale(u * CELL))
                    .add(up.scale(v * CELL))
                level.sendParticles(mote, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        shatter(level, centre, mote)
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f)
    }

    private fun shatter(level: ServerLevel, centre: Vec3, mote: ParticleOptions) {
        level.sendParticles(mote, centre.x, centre.y, centre.z, 24, 0.4, 0.4, 0.4, 0.18)
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 8, 0.2, 0.2, 0.2, 0.05)
    }

    private fun damageOf(sequence: Sequence, missing: Float): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG) +
            (missing * MISSING_HEALTH_FACTOR).roundToInt()

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 25
        /** 2 hearts at Seq. 9 — but the ability itself only appears at the 5th. */
        const val BASE_DAMAGE = 4
        const val MISSING_HEALTH_FACTOR = 0.35
        /** Half a heart: otherwise "wounded" would mean any scratch. */
        const val MIN_WOUND = 1.0f

        val UP: Vec3 = Vec3(0.0, 1.0, 0.0)
        /** A 5×5 mirror of motes with a 0.22-block step — roughly a player's height. */
        const val HALF = 2
        const val CELL = 0.22
    }
}
