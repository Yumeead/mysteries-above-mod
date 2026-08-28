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
 * WhiteTower, Посл. 7 (Детектив): **Спритність**.
 *
 * Тіло легшає: ви ходите швидше й перестаєте боятись висоти, з якої звичайна людина
 * ламає ноги.
 *
 * ## 149 рядків, від яких лишилось два виклики
 *
 * Плагінна `Agility` мусила виглядати так: такт що п'ять секунд поновлював
 * `PotionEffectType.SPEED` на шість (щоб ефект не блимав між тактами), плюс підписка
 * на `EntityDamageEvent` типу `FALL`, яка перераховувала шкоду за формулою «висота
 * мінус двадцять», плюс мапа `UUID → ключ підписки`, щоб чужий `unsubscribeAll` її не
 * зніс, плюс зняття ефекту в `onDeactivate`, яке заразом знімало випите гравцем зілля
 * швидкості.
 *
 * Обидві механіки у ванільці — атрибути:
 *
 * | що | атрибут | що зникло разом із ним |
 * |---|---|---|
 * | швидкість | `MOVEMENT_SPEED`, `ADD_MULTIPLIED_BASE` | такт, поновлення, іконка зілля, крок у 20 % |
 * | висота падіння | `SAFE_FALL_DISTANCE` | підписка, формула, конфлікт із Пір'їстим падінням |
 *
 * `SAFE_FALL_DISTANCE` в Bukkit не існувало **взагалі** — саме тому плагін рахував
 * падіння руками. Це найчистіший приклад правила фази 08: форма плагінного коду була
 * продиктована відсутністю інструмента, а не дизайном.
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
        /** +20 % на Посл. 9 — те саме, що давало ванільне `SPEED I`, лише без сходинки. */
        const val BASE_SPEED_PERCENT = 20
        /** Ванільна межа безпечного падіння, від якої рахується бонус. */
        const val SAFE_FALL_BASE = 3
        /** Посл. 7 виходить на плагінні 20 бл.; далі росте. */
        const val BASE_SAFE_FALL_BONUS = 13
    }
}
