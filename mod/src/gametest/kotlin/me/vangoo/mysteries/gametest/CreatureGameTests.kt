package me.vangoo.mysteries.gametest

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.creature.Mind
import me.vangoo.mysteries.creature.ai.CastGoal
import me.vangoo.mysteries.creature.ai.RoutineGoal
import me.vangoo.mysteries.entity.HumanEntity
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.ModPathways
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import com.mojang.authlib.GameProfile
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.network.CommonListenerCookie
import java.util.UUID
import net.minecraft.world.Difficulty
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Creature minds on living bodies, in a real world.
 *
 * Each test is one claim the engine makes about behaviour — the things that read well in
 * `Mind`'s KDoc and are worth nothing until a body actually does them. The tests stand far apart
 * ([PADDING]): a hostile creature from one test would otherwise hunt the player of the next.
 */
class CreatureGameTests {

    // ── The body ─────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun spawnDressesTheBodyAndWakesItsMind(helper: GameTestHelper) {
        helper.floor()
        val murloc = helper.creature("tyrant_murloc_9", 4, 4)
        helper.expect(Creatures.keyOf(murloc)?.identifier() == modId("tyrant_murloc_9"), "немає ключа істоти")
        helper.expect(murloc.maxHealth == 57f, "здоров'я не з опису: ${murloc.maxHealth}")
        helper.expect(murloc.customName?.string == "Мурлок", "ім'я: ${murloc.customName?.string}")
        helper.expect(Creatures.TAG in murloc.entityTags(), "немає тега для селекторів")
        val mind = helper.mind(murloc)
        helper.expect(mind.kit != null, "кіт tyrant_9 не підтягнувся")
        helper.expect(murloc.goalSelector.availableGoals.any { it.goal is CastGoal }, "розум не встановив цілей")
        helper.succeed()
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aCitizenIsAPersonWithAHome(helper: GameTestHelper) {
        helper.floor()
        val citizen = helper.creature("citizen", 4, 4)
        helper.expect(citizen is HumanEntity, "тіло городянина — не людина")
        helper.expect(citizen.isPersistenceRequired, "городянин зникне, щойно гравець відійде")
        helper.expect(citizen.getAttached(Creatures.PERSONA)?.name?.isNotBlank() == true, "без імені")
        helper.expect(citizen.getAttached(Creatures.ANCHORS) != null, "без дому")
        helper.expect(citizen.goalSelector.availableGoals.any { it.goal is RoutineGoal }, "без розпорядку дня")
        helper.succeed()
    }

    // ── Fighting ─────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 100, skyAccess = true, padding = PADDING)
    fun aHostileBeastTakesThePlayer(helper: GameTestHelper) {
        helper.floor()
        val murloc = helper.creature("tyrant_murloc_9", 1, 1)
        val player = helper.player(6, 6)
        helper.succeedWhen { helper.expect(murloc.target === player, "мурлок не взяв гравця в ціль: ${helper.state(murloc, player)}") }
    }

    @GameTest(maxTicks = 160, skyAccess = true, padding = PADDING)
    fun aRangedCasterKeepsItsDistance(helper: GameTestHelper) {
        helper.floor()
        // Fool, Sequence 8: a ranged kit, the band 5..11 blocks.
        val goat = helper.creature("fool_hornacis_goat_8", 4, 4)
        // Untouchable but still a fair target: otherwise the goat simply kills the player before it is done stepping back.
        val player = helper.player(1, 4).also { it.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 400, 4)) }
        helper.succeedWhen {
            helper.expect(goat.target === player, "коза не взяла гравця в ціль: ${helper.state(goat, player)}")
            helper.expect(goat.distanceTo(player) >= 5f, "заклинач не відступив: ${goat.distanceTo(player)}")
        }
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = PADDING)
    fun aBeastDreadsABeyonderTwoSequencesAbove(helper: GameTestHelper) {
        helper.floor()
        val murloc = helper.creature("tyrant_murloc_9", 3, 3)
        val player = helper.player(3, 7)
        AbilityServices.beyonders.register(Beyonder(player.uuid, Sequence(5), ModPathways.byName("Sun")!!))
        val start = murloc.distanceTo(player)
        helper.failIfEver { helper.expect(murloc.target !== player, "мурлок напав на Потойбічного Посл. 5") }
        helper.succeedWhen { helper.expect(murloc.distanceTo(player) > start + 4f, "мурлок не тікає: ${murloc.distanceTo(player)}") }
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aWoundUpTechniqueMissesWhoeverLeftTheMark(helper: GameTestHelper) {
        helper.floor()
        val despot = helper.creature("drowned_despot", 2, 2)
        val player = helper.player(6, 6)
        val mind = helper.mind(despot)
        val storm = Creatures.skill(helper.level, skillKey("boss/storm_mark"))!!
        val healthy = player.health

        mind.release(storm, player, player.position().add(12.0, 0.0, 0.0))
        helper.expect(player.health == healthy, "удар по порожньому місцю влучив у гравця")
        mind.release(storm, player, player.position())
        helper.expect(player.health < healthy, "удар по гравцю, що стоїть на мітці, не влучив")
        helper.succeed()
    }

    // ── Bosses ───────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 40, skyAccess = true, padding = PADDING)
    fun aBossTurnsToItsSecondActAndCannotBeTouchedMeanwhile(helper: GameTestHelper) {
        helper.floor()
        val despot = helper.creature("drowned_despot", 4, 4)
        val player = helper.player(4, 7)
        val mind = helper.mind(despot)
        val firstKit = mind.kit
        despot.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 200f)

        helper.expect(mind.act == 1, "бос не перейшов до другого акту: ${despot.health}/${despot.maxHealth}")
        helper.expect(mind.kit !== firstKit && mind.kit === Creatures.kit(helper.level, kitKey("boss/despot_storm")), "кіт акту не змінився")
        val during = despot.health
        despot.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 50f)
        helper.expect(despot.health == during, "бос отримав шкоду під час переходу між актами")
        helper.runAfterDelay(5) {
            val murlocs = helper.level.getEntitiesOfClass(Mob::class.java, despot.boundingBox.inflate(8.0)) {
                Creatures.keyOf(it)?.identifier() == modId("tyrant_murloc_9")
            }
            helper.expect(murlocs.size >= 2, "Безодня не відгукнулась: мурлоків ${murlocs.size}")
            helper.succeed()
        }
    }

    // ── The town ─────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 400, skyAccess = true, padding = PADDING)
    fun aCitizenGoesToBedAtNight(helper: GameTestHelper) {
        helper.floor()
        val foot = BlockPos(2, 1, 3)
        helper.setBlock(foot, Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT))
        helper.setBlock(foot.north(), Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD))
        helper.command("time set 18000")
        val citizen = helper.creature("citizen", 6, 6)
        helper.succeedWhen { helper.expect(citizen.isSleeping, "городянин не ліг спати") }
    }

    @GameTest(maxTicks = 100, skyAccess = true, padding = PADDING)
    fun aGuardAnswersACitizensCry(helper: GameTestHelper) {
        helper.floor()
        val citizen = helper.creature("citizen", 2, 2)
        val guard = helper.creature("guard", 6, 6)
        val player = helper.player(2, 4)
        citizen.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1f)
        helper.expect(helper.mind(citizen).isPanicking(), "городянин не запанікував")
        helper.succeedWhen { helper.expect(guard.target === player, "варта не прийшла на крик: ${helper.state(guard, player)}") }
    }

    // ── Death ────────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aWildBeyonderLeavesTheirCharacteristic(helper: GameTestHelper) {
        helper.floor()
        val beyonder = helper.creature("wild_beyonder", 4, 4)
        val persona = beyonder.getAttached(Creatures.PERSONA)!!
        val player = helper.player(4, 7)
        beyonder.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.runAfterDelay(2) {
            val dropped = helper.items(beyonder.position()).mapNotNull { it.item.get(ModComponents.CHARACTERISTIC) }
            helper.expect(dropped.size == 1, "Характеристик: ${dropped.size}")
            helper.expect(dropped[0].pathway == persona.pathway && dropped[0].sequence == persona.sequence, "не та Характеристика: ${dropped[0]}")
            helper.succeed()
        }
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aCreatureDropsItsOwnLootAndNothingOfItsVanillaBody(helper: GameTestHelper) {
        helper.floor()
        // A witch's body; its table always gives one or two of its own parts.
        val hunter = helper.creature("fool_thousand_faced_hunter_6", 4, 4)
        val player = helper.player(4, 7)
        val at = hunter.position()
        hunter.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.runAfterDelay(2) {
            val dropped = helper.items(at).map { BuiltInRegistries.ITEM.getKey(it.item.item).path }
            val own = setOf("thousand_faced_hunter_pituitary", "thousand_faced_hunter_blood")
            helper.expect(dropped.isNotEmpty(), "Тисячоликий Мисливець нічого не скинув")
            helper.expect(dropped.all { it in own }, "з тіла відьми випало чуже: $dropped")
            helper.succeed()
        }
    }

    @GameTest(maxTicks = 80, skyAccess = true, padding = PADDING)
    fun aServantHasNoWillOfItsOwn(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(1, 1)
        val murloc = helper.creature("tyrant_murloc_9", 4, 4)
        Retinue.enroll(owner, murloc)
        helper.player(4, 7)
        helper.failIfEver { helper.expect(murloc.target == null, "слуга сам обрав ціль: ${murloc.target?.name?.string}") }
        helper.runAtTickTime(60) { helper.succeed() }
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun spiritWorldBeingsAreKnownByTheirTag(helper: GameTestHelper) {
        helper.floor()
        val knight = helper.creature("death_knight", 2, 2)
        val murloc = helper.creature("tyrant_murloc_9", 5, 5)
        helper.expect(Spirits.isSpiritWorldBeing(knight), "Лицар Смерті не впізнаний як істота Світу Духів")
        helper.expect(!Spirits.isSpiritWorldBeing(murloc), "мурлок — не з Світу Духів")
        helper.expect(helper.mind(knight).chooseTarget() == null, "у Лицаря немає власної волі, а він обрав ціль")
        helper.succeed()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * A stone yard to stand on, wider than the test's own 8×8: its edge is a barrier wall even with
     * sky access, and a creature that must run or keep its distance needs more room than that. The
     * world creatures may fight in: the test server runs on Peaceful, and vanilla's `Mob.canAttack`
     * refuses any player as a target there — rightly so, and the mod's spawner keeps to the same rule.
     */
    private fun GameTestHelper.floor() {
        level.server.setDifficulty(Difficulty.NORMAL, true)
        for (x in -YARD..7 + YARD) for (z in -YARD..7 + YARD) {
            setBlock(x, 0, z, Blocks.STONE)
            for (y in 1..4) if (getBlockState(BlockPos(x, y, z)).`is`(Blocks.BARRIER)) setBlock(x, y, z, Blocks.AIR)
        }
    }

    private fun GameTestHelper.at(x: Int, z: Int): Vec3 = Vec3.atBottomCenterOf(absolutePos(BlockPos(x, 1, z)))

    private fun GameTestHelper.creature(id: String, x: Int, z: Int): Mob =
        Creatures.spawn(level, Creatures.creatureKey(modId(id)), at(x, z)) ?: throw AssertionError("Не вдалося викликати $id")

    private fun GameTestHelper.mind(mob: Mob): Mind = Creatures.mindOf(mob) ?: throw AssertionError("${mob.name.string} без розуму")

    /**
     * A survival player whose "client" has finished loading.
     *
     * Vanilla's `makeMockServerPlayerInLevel` will not do: its player answers `gameMode()` with
     * CREATIVE no matter what, and no creature fights a creative player. This is the same
     * assembly — a fake connection, `placeNewPlayer` — without that override. The load packet is
     * the one a real client sends; until it arrives the server treats the player as not yet in the
     * world and invulnerable to everything.
     */
    private fun GameTestHelper.player(x: Int, z: Int): ServerPlayer {
        val server = level.server
        val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), "test-survivor"), false)
        val player = ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation())
        val connection = Connection(PacketFlow.SERVERBOUND)
        EmbeddedChannel(connection)
        server.playerList.placeNewPlayer(connection, player, cookie)
        return player.also {
        it.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        it.setGameMode(GameType.SURVIVAL)
        val spot = at(x, z)
        it.snapTo(spot.x, spot.y, spot.z)
        }
    }

    private fun GameTestHelper.state(mob: Mob, player: ServerPlayer): String {
        val mind = mind(mob)
        return "target=${mob.target?.name?.string} hp=${player.health} alive=${player.isAlive} dist=${"%.1f".format(mob.distanceTo(player))} " +
            "sees=${mob.hasLineOfSight(player)} fight=${mind.canFight(player)} choose=${mind.chooseTarget()?.name?.string} " +
            "mobAlive=${mob.isAlive} owned=${mind.isOwned} time=${level.defaultClockTime % 24000} mode=${player.gameMode()} " +
            "ts=${mob.targetSelector.availableGoals.map { it.goal.javaClass.simpleName + ":" + it.isRunning }} age=${mob.tickCount} noAi=${mob.isNoAi}"
    }

    private fun GameTestHelper.items(around: Vec3): List<ItemEntity> =
        level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(around, 6.0, 6.0, 6.0))

    private fun GameTestHelper.command(line: String) {
        val server = level.server
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), line)
    }

    private fun GameTestHelper.expect(condition: Boolean, what: String) {
        if (!condition) fail(what)
    }

    private fun skillKey(path: String) =
        ResourceKey.create(me.vangoo.mysteries.creature.CreatureRegistries.SKILL, modId(path))

    private fun kitKey(path: String) =
        ResourceKey.create(me.vangoo.mysteries.creature.CreatureRegistries.KIT, modId(path))

    private companion object {
        const val PADDING = 48
        const val YARD = 14
    }
}
