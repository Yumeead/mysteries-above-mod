package me.vangoo.mysteries.pathways.fool

import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.fool.abilities.PaperThrower
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items

/**
 * "Hold plain paper, right-click, it flies" needs a trigger that fires on plain interaction —
 * there is no cast, no menu, nothing for [me.vangoo.mysteries.beyonder.AbilityRequests] to route.
 * `UseItemCallback` is the ready Fabric hook for exactly this (a right-click that is not aimed
 * at a block or entity); a Fabric callback cannot be unregistered, so — same as Error's
 * `ConceptualTheft` — it is hung once here and the Beyonder registry decides whether it bites.
 */
object PaperThrowTrigger {

    fun install() {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (player.mainHandItem.item != Items.PAPER) return@register InteractionResult.PASS

            val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return@register InteractionResult.PASS
            val spec = beyonder.abilities.firstOrNull { it is PaperThrower } ?: return@register InteractionResult.PASS

            val context = AbilityServices.contexts.of(player, beyonder, spec)
            if ((spec as PaperThrower).throwPaper(context)) InteractionResult.SUCCESS_SERVER else InteractionResult.PASS
        }
    }
}
