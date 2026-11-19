package me.vangoo.mysteries.gametest

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.CastReport
import me.vangoo.mysteries.ability.Restraints
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.Ascension
import me.vangoo.mysteries.pathways.ModPathways
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Full restraint ([Restraints]) as the server enforces it. That a bound player's own keys move nothing is the
 * client's half, and `RestraintClientGameTest` shows it.
 */
class RestraintGameTests {

    /** A bound zombie, a player in reach: it neither steps towards them nor strikes, and walks once let go. */
    @GameTest(maxTicks = 140, skyAccess = true)
    fun boundMobDoesNothing(helper: GameTestHelper) {
        helper.floor()
        val player = helper.player(6, 4)
        // Relative to the test, unlike `at`: absolute coordinates here land the body a world away.
        val zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos(2, 1, 4))
        zombie.setTarget(player)
        Restraints.hold(zombie, 60)
        val from = zombie.position()
        val health = player.health
        helper.runAfterDelay(55) {
            val moved = zombie.position().subtract(from).horizontalDistance()
            if (moved > 0.05) helper.fail("скутий зомбі зрушив на $moved бл.")
            if (player.health < health) helper.fail("скутий зомбі вдарив гравця")
        }
        // Let go, it goes after its target again.
        helper.runAfterDelay(120) {
            val moved = zombie.position().subtract(from).horizontalDistance()
            helper.level.server.playerList.remove(player)
            if (moved < 0.5) helper.fail("відпущений зомбі стоїть: $moved бл.") else helper.succeed()
        }
    }

    /** A bound player calls on no power, active or toggle, and uses no item; let go, they can again. */
    @GameTest(maxTicks = 60, skyAccess = true)
    fun boundPlayerUsesNothing(helper: GameTestHelper) {
        helper.floor()
        val player = helper.player(3, 3)
        Ascension.bestow(player, ModPathways.byName("Justiciar")!!, Sequence(8))
        Restraints.hold(player, 20)
        val active = AbilityRequests.cast(player, AbilityIdentity.of("justiciar_area_of_jurisdiction"))
        val toggle = AbilityRequests.cast(player, AbilityIdentity.of("justiciar_recognition"))
        if (active != CastReport.Restrained) helper.fail("скутий скастував активну: $active")
        if (toggle != CastReport.Restrained) helper.fail("скутий перемкнув тогл: $toggle")
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BREAD))
        val use = UseItemCallback.EVENT.invoker().interact(player, helper.level, InteractionHand.MAIN_HAND)
        if (use != InteractionResult.FAIL) helper.fail("скутий може їсти: $use")
        helper.runAfterDelay(25) {
            val after = AbilityRequests.cast(player, AbilityIdentity.of("justiciar_recognition"))
            Ascension.forget(player)
            helper.level.server.playerList.remove(player)
            if (after == CastReport.Restrained || Restraints.isRestrained(player)) helper.fail("пута не спали") else helper.succeed()
        }
    }
}
