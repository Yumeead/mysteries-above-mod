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
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Visionary, Seq. 7 (Psychiatrist): **Scan Gaze (Passive)**.
 *
 * `ScanGaze` without the cast: whoever the caster looks at is read automatically, once per new
 * target. The plugin drew this on a hologram visible to the caster alone — the action bar
 * already **is** private to one viewer, so there is nothing a hologram would add here.
 */
class ScanGazePassive :
    ToggleablePassiveAbility(AbilityIdentity.of("visionary_scan_gaze_passive")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("[Пасивна] Сканування поглядом")

    private val lastTarget = HashMap<UUID, UUID>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Автоматично показує дані про гравця, на якого дивитесь.\n" +
            "▪ З Посл. $ADVANCED_SEQUENCE і вище — ще й насичення та ефекти\n" +
            "▪ Дальність $RANGE бл.",
    )

    override fun onEnable(context: AbilityContext) {
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f,
        )
    }

    override fun onDisable(context: AbilityContext) {
        lastTarget.remove(context.caster.uuid)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.0f,
        )
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val target = context.targeting.playerLookingAt(RANGE)
        if (target == null) {
            lastTarget.remove(owner)
            return
        }
        if (lastTarget[owner] == target.uuid) return
        lastTarget[owner] = target.uuid

        show(context, target)
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.3f, 2.0f,
        )
    }

    private fun show(context: AbilityContext, target: Player) {
        val hp = (target.health * 10).roundToInt() / 10.0
        val maxHp = (target.maxHealth * 10).roundToInt() / 10.0
        val line = StringBuilder("❤ $hp/$maxHp  🍖 ${target.foodData.foodLevel}/20")

        if (context.sequence.level < ADVANCED_SEQUENCE) {
            val saturation = (target.foodData.saturationLevel * 10).roundToInt() / 10.0
            line.append("  ✦ Sat: $saturation")
            target.activeEffects.take(2).forEach { effect ->
                line.append(" ${effect.effect.value().displayName.string} ${effect.amplifier + 1}")
            }
        }

        context.messaging.actionBar(context.caster, Component.literal(line.toString()))
    }

    private companion object {
        const val RANGE = 10.0
        const val ADVANCED_SEQUENCE = 7
    }
}
