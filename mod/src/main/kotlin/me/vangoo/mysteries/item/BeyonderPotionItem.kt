package me.vangoo.mysteries.item

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.Ascension
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Пляшка потойбічного зілля.
 *
 * Анімацію, звук і перетворення на порожню пляшку робить ванільний компонент
 * `CONSUMABLE`, оголошений у [ModItems] — власного коду тут рівно два хуки: перевірити
 * **до** ковтка й подіяти **після**.
 *
 * У плагіні це був один слухач `PlayerItemConsumeEvent`, який на половині шляху
 * скасовував подію. Тут скасовувати нема чого: зілля, яке пити не можна, і не
 * починають пити — [use] відмовляє до того, як рука підніметься.
 */
class BeyonderPotionItem(properties: Properties) : Item(properties) {

    /**
     * Рішення ухвалює сервер: реєстр Потойбічних живе тільки там, і на виділеному
     * сервері клієнт про нього нічого не знає. Клієнт починає анімацію оптимістично —
     * якщо сервер відмовив, він же надішле поправку.
     */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide || player !is ServerPlayer) return super.use(level, player, hand)

        val stack = player.getItemInHand(hand)
        val brew = stack.get(ModComponents.POTION_BREW)
            ?: return refuse(player, Component.literal("Пошкоджене зілля"))

        Ascension.refusal(player, brew)?.let { return refuse(player, it) }

        return super.use(level, player, hand)
    }

    override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
        if (level.isClientSide || entity !is ServerPlayer) return super.finishUsingItem(stack, level, entity)

        val brew = stack.get(ModComponents.POTION_BREW)
            ?: return super.finishUsingItem(stack, level, entity)

        // Стан міг змінитись між ковтком і його кінцем. Якщо змінився — пляшка лишається
        // повною: втратити рідкісне зілля через гонку гірше, ніж не випити його.
        Ascension.refusal(entity, brew)?.let {
            refuse(entity, it)
            return stack
        }

        Ascension.ascend(entity, brew)
        return super.finishUsingItem(stack, level, entity)
    }

    private fun refuse(player: ServerPlayer, reason: Component): InteractionResult {
        AbilityServices.messaging.actionBar(player, reason)
        return InteractionResult.FAIL
    }
}
