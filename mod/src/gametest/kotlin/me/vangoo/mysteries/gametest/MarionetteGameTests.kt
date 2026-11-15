package me.vangoo.mysteries.gametest

import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.entity.MarionetteEntity.Order
import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * Puppets on the threads, in a real world: what each order makes a body do.
 *
 * Seized mobs are taken straight onto the threads ([Marionettes.seize]): breaking a will takes a
 * real cast of fifteen seconds, and that is Marionette Control's own scene in `FoolClientGameTest`.
 * A puppet made from a player needs a second player to kill, so its fighting is checked on a puppet
 * built by hand with a victim's pockets.
 *
 * Every scene keeps to the test's own 8×8. Past it only a player keeps the chunks ticking, and the
 * test's players never tick themselves, so the chunks do not follow them when they are moved: a
 * puppet walking out there, or an arrow flying out there, simply stops.
 */
class MarionetteGameTests {

    @GameTest(maxTicks = 100, skyAccess = true, padding = PADDING)
    fun aSeizedBeastHasNoWillOfItsOwn(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(1, 1)
        val pig = helper.mob(EntityTypes.PIG, 4, 4)
        val zombie = helper.mob(EntityTypes.ZOMBIE, 6, 2)
        Marionettes.seize(owner, pig)
        Marionettes.seize(owner, zombie)
        // A stranger the zombie would go for at once on its own.
        helper.player(6, 6, "stranger")
        val pigAt = pig.position()
        val zombieAt = zombie.position()
        helper.failIfEver {
            helper.expect(pig.position().distanceTo(pigAt) < STILL, "свиня-маріонетка пішла сама: ${pig.position().distanceTo(pigAt)}")
            helper.expect(zombie.position().distanceTo(zombieAt) < STILL, "зомбі-маріонетка пішов сам")
            helper.expect(zombie.target == null, "зомбі-маріонетка сам обрав ціль: ${zombie.target?.name?.string}")
        }
        helper.runAtTickTime(80) { helper.succeed() }
    }

    @GameTest(maxTicks = 300, skyAccess = true, padding = PADDING)
    fun aFollowerWalksAfterItsMarionettistAndNeverJumpsAhead(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(1, 1)
        val pig = helper.mob(EntityTypes.PIG, 2, 2)
        Marionettes.seize(owner, pig)
        Marionettes.command(pig, Order.Follow)
        owner.teleportTo(helper.at(7, 7).x, owner.y, helper.at(7, 7).z)
        var last = pig.position()
        helper.failIfEver {
            helper.expect(pig.position().distanceTo(last) < STEP, "маріонетку перенесло: ${pig.position().distanceTo(last)} бл. за тік")
            last = pig.position()
        }
        helper.succeedWhen { helper.expect(pig.distanceTo(owner) < NEAR, "маріонетка не дійшла: ${pig.distanceTo(owner)} бл.") }
    }

    @GameTest(maxTicks = 300, skyAccess = true, padding = PADDING)
    fun comeToMeStandsWhereTheMarionettistStood(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(7, 7)
        val pig = helper.mob(EntityTypes.PIG, 0, 0)
        Marionettes.seize(owner, pig)
        val spot = owner.position()
        Marionettes.command(pig, Order.GoTo(spot))
        // The Marionettist walks off: the puppet goes where they were, not after them.
        owner.teleportTo(helper.at(7, 0).x, owner.y, helper.at(7, 0).z)
        helper.succeedWhen {
            helper.expect(Marionettes.orderOf(pig) == Order.Stand, "маріонетка ще йде: ${Marionettes.orderOf(pig)}")
            helper.expect(pig.position().distanceTo(spot) < NEAR, "маріонетка стала не там: ${pig.position().distanceTo(spot)} бл.")
        }
    }

    @GameTest(maxTicks = 260, skyAccess = true, padding = PADDING)
    fun aGuardStrikesBackOnlyForItsMarionettist(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(2, 2)
        val guard = helper.mob(EntityTypes.PIG, 3, 3)
        val bystander = helper.mob(EntityTypes.SHEEP, 6, 2).also { it.isNoAi = true }
        val attacker = helper.mob(EntityTypes.COW, 2, 7).also { it.isNoAi = true }
        Marionettes.seize(owner, guard)
        Marionettes.command(guard, Order.Guard)
        // A blow on the guard itself is not a blow on its Marionettist.
        guard.hurtServer(helper.level, helper.level.damageSources().mobAttack(bystander), 1f)
        // A player who has just joined cannot be hurt for three seconds.
        helper.runAtTickTime(SPAWN_SHIELD) { owner.hurtServer(helper.level, helper.level.damageSources().mobAttack(attacker), 1f) }
        helper.failIfEver { helper.expect(bystander.lastHurtByMob !== guard, "охоронець ударив того, хто бив не власника") }
        helper.succeedWhen { helper.expect(attacker.lastHurtByMob === guard, "охоронець не відповів за власника: ${Marionettes.orderOf(guard)}") }
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = PADDING)
    fun anAssistantStrikesWhatItsMarionettistStrikes(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(2, 2)
        // An iron golem fights with its own body: the threads only name the enemy.
        val golem = helper.mob(EntityTypes.IRON_GOLEM, 4, 2)
        val cow = helper.mob(EntityTypes.COW, 2, 7).also { it.isNoAi = true }
        Marionettes.seize(owner, golem)
        Marionettes.command(golem, Order.Assist)
        helper.runAtTickTime(20) {
            helper.expect(cow.lastHurtByMob == null, "голем ударив до наказу")
            cow.hurtServer(helper.level, helper.level.damageSources().playerAttack(owner), 1f)
        }
        helper.succeedWhen { helper.expect(cow.lastHurtByMob === golem, "голем не підтримав удар власника: ціль ${golem.target?.name?.string}") }
    }

    @GameTest(maxTicks = 100, skyAccess = true, padding = PADDING)
    fun aPlayerPuppetFightsWithTheBestBladeInItsPockets(helper: GameTestHelper) {
        helper.floor()
        val pig = helper.mob(EntityTypes.PIG, 4, 2).also { it.isNoAi = true }
        val puppet = helper.puppet(2, 2, listOf(ItemStack(Items.STICK), ItemStack(Items.BREAD), ItemStack(Items.IRON_SWORD)))
        puppet.order = Order.Attack(pig.uuid)
        helper.succeedWhen {
            helper.expect(puppet.mainHandItem.`is`(Items.IRON_SWORD), "маріонетка не взяла меч: ${puppet.mainHandItem}")
            // Ten hit points, and an iron sword in its hand takes most of them in one blow.
            helper.expect(pig.health <= SWORD_LEFT, "удар не той, що мечем: у свині ${pig.health}")
        }
    }

    @GameTest(maxTicks = 300, skyAccess = true, padding = PADDING)
    fun aPlayerPuppetShootsWhatStandsOff(helper: GameTestHelper) {
        helper.floor()
        // Corner to corner: as far apart as the test's own ground allows.
        val pig = helper.mob(EntityTypes.PIG, 7, 7).also { it.isNoAi = true }
        val puppet = helper.puppet(0, 0, listOf(ItemStack(Items.IRON_SWORD), ItemStack(Items.BOW), ItemStack(Items.ARROW, 16)))
        puppet.order = Order.Attack(pig.uuid)
        helper.succeedWhen {
            helper.expect(pig.lastHurtByMob === puppet, "стріли не влучили: у руці ${puppet.mainHandItem}, стріл ${puppet.capture?.inventory?.get(2)?.count}")
            helper.expect(puppet.distanceTo(pig) > 6f, "маріонетка підійшла, а не стріляла")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun <E : Mob> GameTestHelper.mob(type: net.minecraft.world.entity.EntityType<E>, x: Int, z: Int): E =
        spawn(type, BlockPos(x, 1, z))

    /** A puppet as a conversion leaves it, with [pockets] for the victim's inventory and no owner. */
    private fun GameTestHelper.puppet(x: Int, z: Int, pockets: List<ItemStack>): MarionetteEntity {
        val puppet = MarionetteEntity(ModEntities.MARIONETTE, level)
        val spot = at(x, z)
        puppet.snapTo(spot.x, spot.y, spot.z, 0f, 0f)
        puppet.capture = MarionetteEntity.Capture(UUID.randomUUID(), "puppet", null, null, pockets)
        level.addFreshEntity(puppet)
        return puppet
    }

    private fun GameTestHelper.expect(condition: Boolean, what: String) {
        if (!condition) fail(what)
    }

    private companion object {
        const val PADDING = 48

        /** Past a fresh player's spawn invulnerability. */
        const val SPAWN_SHIELD = 70L

        /** Standing still, near enough, and the most a body walks in one tick. */
        const val STILL = 0.5
        const val NEAR = 4f
        const val STEP = 1.0

        /** An iron sword on a puppet: 2 base + 5. A pig of ten is left with three at most. */
        const val SWORD_LEFT = 4f
    }
}
