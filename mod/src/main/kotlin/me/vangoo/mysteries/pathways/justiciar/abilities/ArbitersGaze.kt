package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Justiciar, Seq. 9 (Arbiter): **Arbiter's Gaze**.
 *
 * A stare that presses down on whoever it settles on. Below Seq. 6 (Judge and above) the gaze
 * grows sharp enough that others cannot hold eye contact with the Arbiter at all.
 */
class ArbitersGaze : PermanentPassiveAbility(AbilityIdentity.of("justiciar_arbiters_gaze")), Ticking {

    override val displayName: Component = Component.literal("Погляд Судді")

    override fun description(sequence: Sequence): Component {
        val base = "Ваш пильний погляд тисне на ворогів.\n" +
            "▪ Погляд ${STARE_SECONDS} с поспіль → Слабкість на 10 с"
        return Component.literal(
            if (sequence.level <= AVERSION_UNLOCK_SEQUENCE) {
                "$base\n▪ Інші не можуть довго дивитись на вас: погляд змушує їх відвернутись"
            } else {
                base
            },
        )
    }

    override fun onActivate(context: AbilityContext) {
        stares[context.caster.uuid] = StareState()
        aversions[context.caster.uuid] = ConcurrentHashMap()
    }

    override fun onDeactivate(context: AbilityContext) {
        stares.remove(context.caster.uuid)
        aversions.remove(context.caster.uuid)
    }

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val state = stares.getOrPut(caster.uuid) { StareState() }

        processStare(context, state)
        if (context.sequence.level <= AVERSION_UNLOCK_SEQUENCE) {
            processAversion(context)
        }
    }

    private fun processStare(context: AbilityContext, state: StareState) {
        val target = context.targeting.lookingAt(MAX_STARE_DISTANCE)
        if (target == null) {
            state.reset()
            return
        }

        if (target.uuid == state.targetId) {
            state.ticksAccumulated++
            if (state.ticksAccumulated >= STARE_THRESHOLD_TICKS) {
                applyWeakness(context, target)
                state.reset()
            }
        } else {
            state.targetId = target.uuid
            state.ticksAccumulated = 0
        }
    }

    private fun applyWeakness(context: AbilityContext, target: LivingEntity) {
        val defender = context.beyonders.of(target.uuid)
        if (defender != null) {
            val chance = SequenceBasedSuccessChance(context.beyonder.sequenceLevel, defender.sequenceLevel)
            if (!chance.rollSuccess()) {
                context.messaging.actionBar(context.caster, Component.literal("Ціль опирається вашому погляду"))
                return
            }
        }
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_DURATION_TICKS, 0))
        val pos = target.position()
        context.level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.5, pos.z, 15, 0.3, 0.5, 0.3, 0.1)
        context.level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 0.8f)
        context.messaging.actionBar(context.caster, Component.literal("Придушили волю ${target.name.string}"))
    }

    private fun processAversion(context: AbilityContext) {
        val caster = context.caster
        val accumulators = aversions.getOrPut(caster.uuid) { ConcurrentHashMap() }
        val nearby = context.targeting.playersNear(AVERSION_DISTANCE)

        for (other in nearby) {
            val acc = accumulators.getOrPut(other.uuid) { AversionAccumulator() }
            if (isLookingAt(other, caster)) {
                acc.ticksAccumulated++
                if (acc.ticksAccumulated >= AVERSION_THRESHOLD_TICKS) {
                    resolveAversion(context, caster, other, acc)
                }
            } else {
                acc.ticksAccumulated = 0
            }
        }
        accumulators.keys.retainAll(nearby.map { it.uuid }.toSet())
    }

    private fun resolveAversion(context: AbilityContext, arbiter: ServerPlayer, other: ServerPlayer, acc: AversionAccumulator) {
        acc.ticksAccumulated = 0
        val defender = context.beyonders.of(other.uuid)
        if (defender != null) {
            val chance = SequenceBasedSuccessChance(defender.sequenceLevel, context.beyonder.sequenceLevel)
            if (chance.rollSuccess()) {
                context.messaging.actionBar(other, Component.literal("Ви опирались Погляду Судді!"))
                return
            }
        }
        other.connection.send(ClientboundPlayerRotationPacket(other.yRot + LOOK_AWAY_YAW, false, other.xRot, false))
        context.messaging.actionBar(other, Component.literal("Ви не можете дивитись на ${arbiter.name.string}"))
    }

    private fun isLookingAt(observer: ServerPlayer, target: ServerPlayer): Boolean {
        val toTarget = target.eyePosition.subtract(observer.eyePosition).normalize()
        val dot = observer.getViewVector(1.0f).normalize().dot(toTarget)
        if (dot < LOOK_ANGLE_COS) return false
        return observer.hasLineOfSight(target)
    }

    private class StareState {
        var targetId: UUID? = null
        var ticksAccumulated: Int = 0

        fun reset() {
            targetId = null
            ticksAccumulated = 0
        }
    }

    private class AversionAccumulator {
        var ticksAccumulated: Int = 0
    }

    private companion object {
        const val STARE_SECONDS = 4
        const val STARE_THRESHOLD_TICKS = STARE_SECONDS * 20
        const val WEAKNESS_DURATION_TICKS = 10 * 20
        const val MAX_STARE_DISTANCE = 15.0

        const val AVERSION_UNLOCK_SEQUENCE = 6
        const val AVERSION_DISTANCE = 20.0
        const val AVERSION_THRESHOLD_TICKS = 10 * 20
        const val LOOK_ANGLE_COS = 0.87 // ~30 degrees
        const val LOOK_AWAY_YAW = 100.0f

        val stares = ConcurrentHashMap<UUID, StareState>()
        val aversions = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, AversionAccumulator>>()
    }
}
