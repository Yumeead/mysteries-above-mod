package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Visionary, Seq. 5 (Dreamwalker): **Dream Traversal**.
 *
 * Slip into the nearest sleeping mind and, five seconds later, wake up beside its body. The
 * range grows sharply below Seq. 5 — 500 blocks at Seq. 5, doubling each Sequence closer to 0.
 *
 * ## Nearest, not chosen
 *
 * The plugin opened a chest menu of every sleeper found, each entry carrying their home, hours
 * played, kill count. This is the third of the pathway's three menu-shaped abilities carrying
 * the pending-choice seam elsewhere ([PsychicCue], `Alteration`) — but here the choice itself is
 * cut rather than deferred: at a 500-block search radius there is no gaze to pick a target with,
 * and "the nearest dreaming mind pulls strongest" reads as the ability's own logic, not a
 * shortcut. One sleeper found is all `Dream Traversal` ever needed to work.
 */
class DreamTraversal : ActiveAbility(AbilityIdentity.of("visionary_dream_traversal")) {

    override val displayName: Component = Component.literal("Блукання у снах")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Проникає у сон найближчого сплячого гравця в радіусі ${range(sequence)} бл. " +
            "Через $TELEPORT_DELAY_SECONDS с ви телепортуєтесь до нього.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val searchRange = range(context.sequence)
        val sleeper = context.targeting.playersNear(searchRange.toDouble()).filter { it.isSleeping }
            .minByOrNull { it.position().distanceTo(context.origin) }
            ?: return AbilityOutcome.failed("Немає сплячих гравців у радіусі $searchRange блоків")

        context.messaging.tell(context.caster, Component.literal("✦ Ви проникаєте у сон ${sleeper.name.string}..."))
        context.messaging.tell(sleeper, Component.literal("У вашому сні з'являється невідома присутність..."))
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
        context.level.sendParticles(
            ParticleTypes.PORTAL, context.origin.x, context.origin.y + 1.0, context.origin.z, 50, 0.5, 1.0, 0.5, 0.0,
        )

        val ticket = context.cast
        val messaging = context.messaging
        val caster = context.caster
        val targetId = sleeper.uuid
        val targetName = sleeper.name.string
        val server = context.level.server

        context.scheduling.after(TELEPORT_DELAY_SECONDS * TICKS_PER_SECOND) {
            val target = server.playerList.getPlayer(targetId)
            if (target == null) {
                messaging.tell(caster, Component.literal("✗ Ціль недоступна"))
                return@after
            }

            val destination = target.position().add((Math.random() * 4 - 2), 0.0, (Math.random() * 4 - 2))
            val level = target.level() as net.minecraft.server.level.ServerLevel

            level.sendParticles(ParticleTypes.PORTAL, caster.x, caster.y + 1.0, caster.z, 100, 0.5, 1.0, 0.5, 0.0)
            level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f)

            caster.teleportTo(level, destination.x, destination.y, destination.z, emptySet(), caster.yRot, caster.xRot, false)

            level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1.0, destination.z, 100, 0.5, 1.0, 0.5, 0.0)
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, destination.x, destination.y, destination.z, 30, 0.5, 0.5, 0.5, 0.0)
            level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f)

            caster.addEffect(MobEffectInstance(MobEffects.BLINDNESS, TICKS_PER_SECOND, 0))
            messaging.tell(caster, Component.literal("✓ Ви проникли у світ снів $targetName"))
            messaging.tell(targetId, Component.literal("✦ У ваш сон хтось увійшов..."))

            ticket.complete()
        }

        return AbilityOutcome.Awaiting(Component.literal("Ви занурюєтесь у сон... ($TELEPORT_DELAY_SECONDS с)"))
    }

    private fun range(sequence: Sequence): Int {
        if (sequence.level >= 5) return BASE_RANGE
        return BASE_RANGE * (1 shl (5 - sequence.level))
    }

    private companion object {
        const val BASE_RANGE = 500
        const val TELEPORT_DELAY_SECONDS = 5
        const val COST = 200
        const val COOLDOWN_SECONDS = 60
    }
}
