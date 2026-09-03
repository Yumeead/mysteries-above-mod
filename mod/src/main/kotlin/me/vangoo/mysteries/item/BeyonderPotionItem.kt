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
 * A bottle of Beyonder potion.
 *
 * The animation, the sound and the turning into an empty bottle are done by the vanilla
 * `CONSUMABLE` component declared in [ModItems] — there are exactly two hooks of our own code
 * here: check **before** the sip and act **after**.
 *
 * In the plugin this was one `PlayerItemConsumeEvent` listener that cancelled the event halfway
 * through. Here there is nothing to cancel: a potion that must not be drunk is never started on
 * — [use] refuses before the hand goes up.
 */
class BeyonderPotionItem(properties: Properties) : Item(properties) {

    /**
     * The server makes the decision: the Beyonder registry lives only there, and on a dedicated
     * server the client knows nothing about it. The client starts the animation optimistically —
     * if the server refuses, it will send a correction.
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

        // The state could have changed between the sip and its end. If it did, the bottle stays
        // full: losing a rare potion to a race is worse than not drinking it.
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
