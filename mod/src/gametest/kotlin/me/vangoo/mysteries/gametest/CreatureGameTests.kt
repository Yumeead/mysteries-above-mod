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
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
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
        val murloc = helper.creature("tyrant_murloc_9", 2, 2)
        val player = helper.player(8, 2)
        helper.succeedWhen { helper.expect(murloc.target === player, "мурлок не взяв гравця в ціль") }
    }

    @GameTest(maxTicks = 160, skyAccess = true, padding = PADDING)
    fun aRangedCasterKeepsItsDistance(helper: GameTestHelper) {
        helper.floor()
        // Fool, Sequence 8: a ranged kit, the band 5..11 blocks.
        val goat = helper.creature("fool_hornacis_goat_8", 4, 4)
        val player = helper.player(4, 8)
        helper.succeedWhen {
            helper.expect(goat.target === player, "коза не взяла гравця в ціль")
            helper.expect(goat.distanceTo(player) >= 5f, "заклинач не відступив: ${goat.distanceTo(player)}")
        }
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = PADDING)
    fun aBeastDreadsABeyonderTwoSequencesAbove(helper: GameTestHelper) {
        helper.floor()
        val murloc = helper.creature("tyrant_murloc_9", 4, 4)
        val player = helper.player(4, 8)
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
        val player = helper.player(4, 10)
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
        val guard = helper.creature("guard", 9, 9)
        val player = helper.player(2, 4)
        citizen.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1f)
        helper.expect(helper.mind(citizen).isPanicking(), "городянин не запанікував")
        helper.succeedWhen { helper.expect(guard.target === player, "варта не прийшла на крик") }
    }

    // ── Death ────────────────────────────────────────────────────────────────────

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aWildBeyonderLeavesTheirCharacteristic(helper: GameTestHelper) {
        helper.floor()
        val beyonder = helper.creature("wild_beyonder", 4, 4)
        val persona = beyonder.getAttached(Creatures.PERSONA)!!
        val player = helper.player(4, 8)
        beyonder.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.runAfterDelay(2) {
            val dropped = helper.items(beyonder.position()).mapNotNull { it.item.get(ModComponents.CHARACTERISTIC) }
            helper.expect(dropped.size == 1, "Характеристик: ${dropped.size}")
            helper.expect(dropped[0].pathway == persona.pathway && dropped[0].sequence == persona.sequence, "не та Характеристика: ${dropped[0]}")
            helper.succeed()
        }
    }

    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun aCreatureDropsNothingOfItsVanillaBody(helper: GameTestHelper) {
        helper.floor()
        val murloc = helper.creature("tyrant_murloc_9", 4, 4)
        val player = helper.player(4, 8)
        val at = murloc.position()
        murloc.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.runAfterDelay(2) {
            helper.expect(helper.items(at).isEmpty(), "з тіла потопельника випало: ${helper.items(at).map { it.item }}")
            helper.succeed()
        }
    }

    @GameTest(maxTicks = 80, skyAccess = true, padding = PADDING)
    fun aServantHasNoWillOfItsOwn(helper: GameTestHelper) {
        helper.floor()
        val owner = helper.player(1, 1)
        val murloc = helper.creature("tyrant_murloc_9", 4, 4)
        Retinue.enroll(owner, murloc)
        helper.player(4, 8)
        helper.failIfEver { helper.expect(murloc.target == null, "слуга сам обрав ціль: ${murloc.target?.name?.string}") }
        helper.runAtTickTime(60) { helper.succeed() }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun GameTestHelper.floor() {
        for (x in -2..12) for (z in -2..12) setBlock(x, 0, z, Blocks.STONE)
    }

    private fun GameTestHelper.at(x: Int, z: Int): Vec3 = Vec3.atBottomCenterOf(absolutePos(BlockPos(x, 1, z)))

    private fun GameTestHelper.creature(id: String, x: Int, z: Int): Mob =
        Creatures.spawn(level, Creatures.creatureKey(modId(id)), at(x, z)) ?: throw AssertionError("Не вдалося викликати $id")

    private fun GameTestHelper.mind(mob: Mob): Mind = Creatures.mindOf(mob) ?: throw AssertionError("${mob.name.string} без розуму")

    private fun GameTestHelper.player(x: Int, z: Int): ServerPlayer = makeMockServerPlayerInLevel().also {
        it.setGameMode(GameType.SURVIVAL)
        val spot = at(x, z)
        it.snapTo(spot.x, spot.y, spot.z)
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
    }
}
