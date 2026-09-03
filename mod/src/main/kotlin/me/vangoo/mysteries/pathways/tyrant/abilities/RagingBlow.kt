package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tyrant, Seq. 8 (Furious One): **Raging Blow**.
 *
 * A dreadful strike born of built-up rage. The Furious One first has to land hits in melee;
 * once rage has matured, a cast releases it as one frontal blow that shatters and shoves aside
 * everyone ahead. [Wrath] doubles how fast rage builds.
 *
 * ## Why a counter still lives here
 *
 * Rage has no vanilla counterpart to read off — unlike knockback resistance or a fall, it is not
 * an attribute anyone else provides. So a small in-memory ledger stays, exactly as
 * [me.vangoo.mysteries.pathways.error.beyonder.TheftRegistry] keeps one for a mechanic vanilla
 * has no notion of. What the mod's event bus removes is everything **around** it: the plugin
 * subscribed by hand and filtered every hit for `isValidUser`; here `context.events.on` is
 * subscribed once, and the registry itself drops it on logout, ability loss or re-activation.
 */
class RagingBlow : ActiveAbility(AbilityIdentity.of("tyrant_raging_blow")) {

    override val displayName: Component = Component.literal("Нищівний Удар")

    override val spiritualityCost: Int = 70

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Накопичуйте гнів, б'ючи ворогів у ближньому бою (${THRESHOLD} влучань), " +
            "а тоді вивільніть його фронтальним ударом.\n" +
            "▪ Шкода: ${damage(sequence)}, з потужним відкиданням\n" +
            "▪ Під час Люті гнів накопичується вдвічі швидше",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        ensureTracking(context)
        val owner = context.caster.uuid
        if (rage(owner) < THRESHOLD) {
            return AbilityOutcome.failed("Гнів ще накопичується: ${rage(owner)}/$THRESHOLD — бийте ворогів у ближньому бою")
        }
        rageByPlayer[owner] = 0

        val caster = context.caster
        val look = caster.lookAngle.normalize()
        val damage = damage(context.sequence)

        val box = caster.boundingBox.inflate(HIT_RADIUS, 2.5, HIT_RADIUS)
        for (target in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster }) {
            val toTarget = target.position().subtract(caster.position())
            if (toTarget.lengthSqr() < 1.0e-4 || look.dot(toTarget.normalize()) < FACING_DOT) continue

            target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), damage.toFloat())
            val push = look.scale(KNOCKBACK)
            target.deltaMovement = target.deltaMovement.add(push.x, LIFT, push.z)
            target.hurtMarked = true
        }

        boom(context)
        return AbilityOutcome.done("Нищівний Удар вивільнено")
    }

    /** Subscribed once per player: melee hits from the caster feed the counter, capped at the threshold. */
    private fun ensureTracking(context: CastContext) {
        val owner = context.caster.uuid
        if (!tracking.add(owner)) return

        context.events.on<IncomingDamage> { damage ->
            val attacker = damage.attacker ?: return@on
            if (attacker.uuid != owner) return@on

            val gain = if (attacker.hasEffect(MobEffects.STRENGTH)) 2 else 1
            rageByPlayer.merge(owner, gain, Int::plus)
            if (rage(owner) > THRESHOLD) rageByPlayer[owner] = THRESHOLD
        }
    }

    private fun rage(owner: UUID): Int = rageByPlayer.getOrDefault(owner, 0)

    private fun boom(context: CastContext) {
        val feet = context.caster.position()
        context.level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.2, feet.z, 20, HIT_RADIUS / 2, 0.3, HIT_RADIUS / 2, 0.05)
        context.level.playSound(
            null, feet.x, feet.y, feet.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.7f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val THRESHOLD = 8
        const val BASE_DAMAGE = 8
        const val KNOCKBACK = 1.8
        const val LIFT = 0.5
        const val HIT_RADIUS = 4.0
        const val FACING_DOT = 0.25
        const val COOLDOWN_SECONDS = 10

        /** Per-player rage and the guard against subscribing twice. Not backed by any vanilla state. */
        val rageByPlayer = ConcurrentHashMap<UUID, Int>()
        val tracking = ConcurrentHashMap.newKeySet<UUID>()
    }
}
