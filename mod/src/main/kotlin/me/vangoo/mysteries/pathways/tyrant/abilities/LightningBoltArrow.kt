package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Lightning Bolt**.
 *
 * A bolt loosed in a straight line, piercing everyone it crosses rather than stopping at the
 * first body — the facet [LightningStrike] does not cover: that one picks a point and forks
 * outward from it, this one draws a line through the battlefield.
 *
 * ## Why this is not "arm my next five arrows"
 *
 * The plugin's version hooked `EntityShootBowEvent` to charge whatever the player next fired
 * from a real bow. Fabric has no ready hook for a bow release — vanilla does not publish one
 * either — so catching it would cost a mixin written for this one Sequence-5 power alone, against
 * the search order's own rule (a Fabric hook, then an Item, then a tick, and only then a mixin).
 * The lore survives the swap: a bolt that flies straight and pierces is still an arrow, cast
 * directly instead of riding on a real one.
 */
class LightningBoltArrow : ActiveAbility(AbilityIdentity.of("tyrant_lightning_bolt_arrow")) {

    override val displayName: Component = Component.literal("Стріла-Блискавка")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Блискавка зривається лінією вперед, наскрізь пронизуючи все на шляху.\n" +
            "▪ Шкода: ${damage(sequence)} кожній цілі, ×2 по нежиті, параліч\n" +
            "▪ Дальність ${MAX_RANGE.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val eye = caster.eyePosition
        val direction = caster.lookAngle.normalize()
        val end = eye.add(direction.scale(MAX_RANGE))
        val damage = damage(context.sequence)

        trail(context, eye, end)
        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.3f)

        var hits = 0
        for (target in context.level
            .getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(MAX_RANGE)) {
                it !== caster && it.eyePosition.subtract(eye).let { toIt ->
                    toIt.length() <= MAX_RANGE && toIt.normalize().dot(direction) >= BOLT_COS
                }
            }
            .sortedBy { it.eyePosition.distanceToSqr(eye) }) {
            LightningStrike.smite(context, target, damage)
            hits++
        }

        if (hits == 0) return AbilityOutcome.failed("Блискавка пройшла крізь порожнечу — ніхто не на лінії")
        return AbilityOutcome.done("Блискавка пронизала цілей: $hits")
    }

    private fun trail(context: CastContext, from: Vec3, to: Vec3) {
        val steps = (from.distanceTo(to) / 0.5).toInt().coerceAtLeast(1)
        val step = to.subtract(from).scale(1.0 / steps)
        var point = from
        repeat(steps) {
            point = point.add(step)
            context.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_DAMAGE = 22
        const val MAX_RANGE = 30.0
        const val COOLDOWN_SECONDS = 25
        /** ~3deg half-angle around the ray — a bolt, not a cone. */
        const val BOLT_COS = 0.9986
    }
}
