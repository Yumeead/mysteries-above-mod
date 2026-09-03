package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

/**
 * Visionary, Seq. 9 (Spectator): **Good Memory**.
 *
 * A toggle that locks a face into place: hold your gaze on someone for two seconds and they
 * glow for the next twenty — findable through walls the moment they step out of sight.
 *
 * ## The outline is public, by the precedent Sun's Evil Detection already set
 *
 * The plugin coloured the outline by the target's health and kept it visible to the Spectator
 * alone, through the `glowingentities` library. `MobEffects.GLOWING` cannot be tinted or aimed
 * at one viewer, and the same tradeoff already resolved the same way for `EvilDetection`:
 * everyone sees the mark, not only the caster. It also needs no ledger of its own — the effect
 * expires by itself.
 *
 * ## The gaze accumulates, and the counter has to live somewhere
 *
 * One instance serves the whole server, so per-owner progress sits in a map — the same shape
 * `SpiritualVision`'s `Gaze` already uses for the same reason.
 */
class GoodMemory :
    ToggleablePassiveAbility(AbilityIdentity.of("visionary_good_memory")),
    Ticking {

    override val displayName: Component = Component.literal("Хороша пам'ять")

    private class Watch(val target: UUID, var ticks: Int)

    private val watches = HashMap<UUID, Watch>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Дивіться на когось ${OBSERVE_TICKS / TICKS_PER_SECOND} с — і ціль засвітиться на " +
            "${GLOW_TICKS / TICKS_PER_SECOND} с, навіть крізь стіни.\n" +
            "▪ Дальність спостереження: ${RANGE.toInt()} бл.\n" +
            "▪ Відвели погляд — лічильник з нуля",
    )

    override fun onEnable(context: AbilityContext) {
        context.messaging.actionBar(context.caster, Component.literal("Ви пам'ятаєте кожне обличчя"))
    }

    override fun onDisable(context: AbilityContext) {
        watches.remove(context.caster.uuid)
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val target = context.targeting.lookingAt(RANGE)
        if (target == null) {
            watches.remove(owner)
            return
        }

        val watch = watches[owner]?.takeIf { it.target == target.uuid }
            ?: Watch(target.uuid, 0).also { watches[owner] = it }
        watch.ticks += tickPeriodTicks

        if (watch.ticks < OBSERVE_TICKS) {
            context.messaging.actionBar(
                context.caster,
                Component.literal("👁 Запам'ятовування: ${watch.ticks * 100 / OBSERVE_TICKS}%"),
            )
            return
        }

        mark(context, target)
        watches.remove(owner)
    }

    private fun mark(context: AbilityContext, target: LivingEntity) {
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, true))
        context.messaging.actionBar(context.caster, Component.literal("✓ Зафіксовано"))
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f,
        )
    }

    private companion object {
        const val RANGE = 30.0
        const val OBSERVE_TICKS = 2 * TICKS_PER_SECOND
        const val GLOW_TICKS = 20 * TICKS_PER_SECOND
    }
}
