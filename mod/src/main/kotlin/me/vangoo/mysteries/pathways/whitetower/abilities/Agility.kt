package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.roundToInt

/**
 * WhiteTower, Seq. 7 (Detective): **Agility**.
 *
 * The body grows lighter: you walk faster and stop fearing a height that would break an ordinary
 * person's legs.
 *
 * ## 149 lines that came down to two calls
 *
 * The plugin's `Agility` had to look like this: a tick every five seconds refreshed
 * `PotionEffectType.SPEED` for six (so the effect would not blink between ticks), plus a
 * subscription to `EntityDamageEvent` of type `FALL` that recomputed the damage by a "height
 * minus twenty" formula, plus a `UUID → subscription key` map so that someone else's
 * `unsubscribeAll` would not wipe it, plus removing the effect in `onDeactivate`, which also
 * removed the speed potion the player had drunk.
 *
 * Both mechanics are attributes in vanilla:
 *
 * | what | attribute | what went away with it |
 * |---|---|---|
 * | speed | `MOVEMENT_SPEED`, `ADD_MULTIPLIED_BASE` | the tick, the refresh, the potion icon, the 20 % step |
 * | fall height | `SAFE_FALL_DISTANCE` | the subscription, the formula, the conflict with Feather Falling |
 *
 * `SAFE_FALL_DISTANCE` did **not exist at all** in Bukkit — which is exactly why the plugin
 * computed falls by hand. This is the cleanest example of the phase 08 rule: the shape of the
 * plugin's code was dictated by a missing tool, not by design.
 */
class Agility : PermanentPassiveAbility(AbilityIdentity.of("whitetower_agility")) {

    override val displayName: Component = Component.literal("Спритність")

    private val speedModifier = modifierId(identity.id, "movement_speed")
    private val fallModifier = modifierId(identity.id, "safe_fall_distance")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваше тіло стає легким і швидким.\n" +
            "▪ Швидкість: +${(speedFactor(sequence) * 100).roundToInt()}%\n" +
            "▪ Падіння: без шкоди до ${SAFE_FALL_BASE + safeFallBonus(sequence)} бл.",
    )

    override fun onActivate(context: AbilityContext) {
        val player = context.caster
        player.grantAttribute(
            Attributes.MOVEMENT_SPEED, speedModifier,
            speedFactor(context.sequence), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        )
        player.grantAttribute(
            Attributes.SAFE_FALL_DISTANCE, fallModifier,
            safeFallBonus(context.sequence).toDouble(),
        )
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.MOVEMENT_SPEED, speedModifier)
        context.caster.revokeAttribute(Attributes.SAFE_FALL_DISTANCE, fallModifier)
    }

    private fun speedFactor(sequence: Sequence): Double =
        scale(BASE_SPEED_PERCENT, sequence, SequenceScaler.ScalingStrategy.MODERATE) / 100.0

    private fun safeFallBonus(sequence: Sequence): Int =
        scale(BASE_SAFE_FALL_BONUS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        /** +20 % at Seq. 9 — the same as the vanilla `SPEED I` gave, only without the step. */
        const val BASE_SPEED_PERCENT = 20
        /** The vanilla safe fall distance the bonus is measured from. */
        const val SAFE_FALL_BASE = 3
        /** Seq. 7 reaches the plugin's 20 blocks; beyond that it grows. */
        const val BASE_SAFE_FALL_BONUS = 13
    }
}
