package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Спільна пасивка «сильне тіло»: більше здоров'я плюс, за потреби, кілька ванільних
 * ефектів. Одна на всі шляхи — параметризується назвою, описом і числом сердець.
 *
 * ## Здоров'я — атрибут, а не `HEALTH_BOOST`
 *
 * У плагіні це був `AttributeInstance.setBaseValue(20 + bonus)` плюс ручне відновлення
 * бази в `onDeactivate` — тобто **правка базового значення гравця**, яка запікається у
 * файл і переживає все, включно з видаленням мода. Досить одного падіння сервера між
 * `onActivate` і `onDeactivate`, щоб база лишилась піднятою назавжди.
 *
 * Транзієнтний модифікатор такої властивості не має: його немає у файлі гравця взагалі,
 * тож «прибирання» після падіння сервера відбувається саме собою.
 *
 * Ефекти лишились ефектами — для `WATER_BREATHING` чи `NIGHT_VISION` атрибута не існує.
 * Але вони нескінченні й приховані, тож у списку зілль місця не займають.
 */
open class PhysicalEnhancement(
    identity: AbilityIdentity,
    override val displayName: Component,
    private val flavour: String,
    /** Скільки сердець додає на Посл. 9; далі росте з Послідовністю. */
    private val hearts: Int,
    private vararg val effects: Holder<MobEffect>,
) : PermanentPassiveAbility(identity) {

    private val modifier = modifierId(identity.id, "max_health")

    override fun description(sequence: Sequence): Component = Component.literal(
        "$flavour\n▪ Здоров'я: +${bonusHealth(sequence) / 2.0} ❤" +
            if (effects.isEmpty()) "" else "\n▪ Постійно: ${effects.size} ефект(и)",
    )

    override fun onActivate(context: AbilityContext) {
        val player = context.caster
        player.grantAttribute(Attributes.MAX_HEALTH, modifier, bonusHealth(context.sequence).toDouble())
        effects.forEach { effect ->
            player.forceAddEffect(MobEffectInstance(effect, INFINITE, 0, true, false, false), null)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        val player = context.caster
        player.revokeAttribute(Attributes.MAX_HEALTH, modifier)
        effects.forEach { player.removeEffect(it) }
        // Максимум щойно впав — здоров'я вище за нього ванілька сама не підтягне.
        if (player.health > player.maxHealth) player.health = player.maxHealth
    }

    private fun bonusHealth(sequence: Sequence): Int =
        scale(hearts * 2, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        /** Ванільна позначка нескінченної тривалості. */
        const val INFINITE = -1
    }
}
