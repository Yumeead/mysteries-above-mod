package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Justiciar, Seq. 8 (Sheriff): **Recognition**.
 *
 * A couple of seconds of study mark a creature as identified — a plain vanilla
 * [MobEffects.GLOWING], visible to everyone, not just the Sheriff. The plugin coloured the
 * outline by the target's remaining health through a scoreboard team per viewer; the mod has no
 * such per-viewer colour channel yet (only [me.vangoo.mysteries.ability.SelectiveVisibility]'s
 * per-viewer packets, built for a very different problem — see `Eavesdrop`), so the outline stays
 * the vanilla white and the health reading moves into the action-bar message instead.
 */
class Recognition : ToggleablePassiveAbility(AbilityIdentity.of("justiciar_recognition")), Ticking {

    override val displayName: Component = Component.literal("Ідентифікація")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вмикається/вимикається окремою клавішею. Поки увімкнено: подивіться на ціль ${OBSERVE_SECONDS} с — " +
            "вона підсвітиться на ${GLOW_DURATION_TICKS / 20} с у радіусі $RANGE бл.",
    )

    override fun onEnable(context: AbilityContext) {
        observations[context.caster.uuid] = ObservationState()
        context.messaging.actionBar(context.caster, Component.literal("Ідентифікацію увімкнено"))
    }

    override fun onDisable(context: AbilityContext) {
        observations.remove(context.caster.uuid)
        context.messaging.actionBar(context.caster, Component.literal("Ідентифікацію вимкнено"))
    }

    override fun tick(context: AbilityContext) {
        val state = observations.getOrPut(context.caster.uuid) { ObservationState() }
        val target = context.targeting.lookingAt(RANGE)

        if (target == null) {
            state.reset()
            return
        }

        if (target.uuid == state.targetId) {
            state.ticksAccumulated++
            if (state.ticksAccumulated >= OBSERVE_THRESHOLD_TICKS) {
                mark(context, target)
                state.reset()
            }
        } else {
            state.targetId = target.uuid
            state.ticksAccumulated = 0
        }
    }

    private fun mark(context: AbilityContext, target: LivingEntity) {
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0, true, false, true))

        val maxHealth = target.maxHealth
        val percent = if (maxHealth > 0f) ((target.health / maxHealth) * 100).toInt() else 0
        context.messaging.actionBar(
            context.caster,
            Component.literal("Зафіксовано: ${target.name.string} [HP: $percent%]"),
        )
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f,
        )
    }

    private class ObservationState {
        var targetId: UUID? = null
        var ticksAccumulated: Int = 0

        fun reset() {
            targetId = null
            ticksAccumulated = 0
        }
    }

    private companion object {
        const val OBSERVE_SECONDS = 2
        const val OBSERVE_THRESHOLD_TICKS = OBSERVE_SECONDS * 20
        const val GLOW_DURATION_TICKS = 20 * 20
        const val RANGE = 30.0

        val observations = ConcurrentHashMap<UUID, ObservationState>()
    }
}
