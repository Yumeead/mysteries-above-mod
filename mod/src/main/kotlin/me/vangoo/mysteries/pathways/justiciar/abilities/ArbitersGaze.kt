package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import me.vangoo.mysteries.effect.ward
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Justiciar, Seq. 9 (Arbiter): **Arbiter's Gaze**.
 *
 * A stare that presses down on whoever it settles on. The Arbiter's eyes show it: gold after a second of
 * holding one gaze, bone-white for the two ticks the weakness lands, and a target that is a player bows
 * their head for that moment. From Seq. 6 (Judge) the gaze grows sharp enough that others cannot hold eye
 * contact with the Arbiter at all: after ten seconds they lower their eyes.
 */
class ArbitersGaze : PermanentPassiveAbility(AbilityIdentity.of("justiciar_arbiters_gaze")), Ticking {

    override val displayName: Component = Component.literal("Погляд Судді")

    override fun description(sequence: Sequence): Component {
        val base = "Ваш пильний погляд тисне на ворогів.\n" +
            "▪ Очі світяться золотом, поки погляд тисне\n" +
            "▪ Погляд $STARE_SECONDS с поспіль: Слабкість на 10 с"
        return Component.literal(
            if (sequence.level <= AVERSION_UNLOCK_SEQUENCE) {
                "$base\n▪ Інші не можуть довго дивитись на вас: за ${AVERSION_THRESHOLD_TICKS / 20} с вони опускають очі"
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
        Eyes.dim(context.caster)
        stares.remove(context.caster.uuid)
        aversions.remove(context.caster.uuid)
    }

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val state = stares.getOrPut(caster.uuid) { StareState() }

        processStare(context, state)
        shineEyes(caster, state)
        if (context.sequence.level <= AVERSION_UNLOCK_SEQUENCE) {
            processAversion(context)
        }
    }

    /**
     * Gold once the stare has held a second; white for the two ticks the blow lands; dark otherwise. It yields
     * to other eyes: Authority and Piercing light them too, and what they lit is not this stare's to change.
     */
    private fun shineEyes(caster: ServerPlayer, state: StareState) {
        val current = caster.getAttached(Eyes.GLOW)
        if (current != state.shown) {
            state.shown = null
            if (current != null) return
        }
        val want = when {
            state.whiteTicks > 0 -> EyeGlow.WHITE.also { state.whiteTicks-- }
            state.ticksAccumulated >= EYES_KINDLE_TICKS -> EyeGlow.GOLD
            else -> null
        }
        when {
            want != null -> Eyes.light(caster, want)
            state.shown != null -> Eyes.dim(caster)
        }
        state.shown = want
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
                applyWeakness(context, state, target)
                state.reset()
            }
        } else {
            state.targetId = target.uuid
            state.ticksAccumulated = 0
        }
    }

    private fun applyWeakness(context: AbilityContext, state: StareState, target: LivingEntity) {
        val defender = context.beyonders.of(target.uuid)
        if (defender != null) {
            val chance = SequenceBasedSuccessChance(context.beyonder.sequenceLevel, defender.sequenceLevel)
            if (!chance.rollSuccess()) {
                ward(target, PathwayBranding.liquidOf(defender.pathway.name))
                return
            }
        }
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_DURATION_TICKS, 0))
        state.whiteTicks = WHITE_TICKS
        if (target is ServerPlayer) lowerEyes(target)
    }

    /** The head bows: the pitch is set, not turned, so it does not matter where it was looking. */
    private fun lowerEyes(player: ServerPlayer) {
        player.connection.send(
            ClientboundPlayerRotationPacket(player.yRot, false, (player.xRot + LOOK_DOWN_PITCH).coerceAtMost(MAX_PITCH), false),
        )
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
                    resolveAversion(context, other, acc)
                }
            } else {
                acc.ticksAccumulated = 0
            }
        }
        accumulators.keys.retainAll(nearby.map { it.uuid }.toSet())
    }

    private fun resolveAversion(context: AbilityContext, other: ServerPlayer, acc: AversionAccumulator) {
        acc.ticksAccumulated = 0
        val defender = context.beyonders.of(other.uuid)
        if (defender != null) {
            val chance = SequenceBasedSuccessChance(defender.sequenceLevel, context.beyonder.sequenceLevel)
            if (chance.rollSuccess()) {
                ward(other, PathwayBranding.liquidOf(defender.pathway.name))
                return
            }
        }
        lowerEyes(other)
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

        /** What this stare lit the eyes with last, if it did: anything else on them is someone else's. */
        var shown: EyeGlow? = null

        /** Ticks the eyes still burn white after the blow. */
        var whiteTicks: Int = 0

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
        const val EYES_KINDLE_TICKS = 20
        const val WHITE_TICKS = 2

        const val AVERSION_UNLOCK_SEQUENCE = 6
        const val AVERSION_DISTANCE = 20.0
        const val AVERSION_THRESHOLD_TICKS = 10 * 20
        const val LOOK_ANGLE_COS = 0.87 // ~30 degrees
        const val LOOK_DOWN_PITCH = 40f
        const val MAX_PITCH = 85f

        val stares = ConcurrentHashMap<UUID, StareState>()
        val aversions = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, AversionAccumulator>>()
    }
}
