package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.roundToInt

/**
 * Visionary, Seq. 9 (Spectator): **Scan Gaze**.
 *
 * A glance that reads a player's vitals — health, hunger, armour — off the action bar. `caster`
 * being a live `ServerPlayer` means every one of these is a single vanilla read; there is
 * nothing here for a service to add.
 */
class ScanGaze : ActiveAbility(AbilityIdentity.of("visionary_scan_gaze")) {

    override val displayName: Component = Component.literal("Сканування поглядом")

    override val spiritualityCost: Int = 5

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Показує ХП, голод і броню гравця, на якого дивитесь.\n" +
            "▪ З Посл. $ADVANCED_SEQUENCE і вище — ще й насичення та ефекти\n" +
            "▪ Дальність $RANGE бл., відкат $COOLDOWN_SECONDS с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.playerLookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає цілі в радіусі $RANGE блоків")

        val hp = (target.health * 10).roundToInt() / 10.0
        val maxHp = (target.maxHealth * 10).roundToInt() / 10.0
        val armor = target.getAttributeValue(Attributes.ARMOR).roundToInt()

        val line = StringBuilder("🔍 ${target.name.string}  ❤ $hp/$maxHp  🍖 ${target.foodData.foodLevel}/20  🛡 $armor")

        if (context.sequence.level < ADVANCED_SEQUENCE) {
            val saturation = (target.foodData.saturationLevel * 10).roundToInt() / 10.0
            line.append("  ✦ Sat: $saturation")

            val effects = target.activeEffects.take(2)
            if (effects.isNotEmpty()) {
                line.append("  ✦ ")
                effects.forEach { effect ->
                    line.append("${effect.effect.value().displayName.string} ${effect.amplifier + 1} (${effect.duration / 20}с) ")
                }
            }
        }

        context.messaging.actionBar(context.caster, Component.literal(line.toString()))
        return AbilityOutcome.Done()
    }

    private companion object {
        const val RANGE = 5.0
        const val COOLDOWN_SECONDS = 10
        const val ADVANCED_SEQUENCE = 9
    }
}
