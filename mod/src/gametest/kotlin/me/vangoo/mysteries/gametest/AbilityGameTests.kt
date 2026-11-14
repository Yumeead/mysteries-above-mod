package me.vangoo.mysteries.gametest

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastReport
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.beyonder.AbilityRequests
import me.vangoo.mysteries.beyonder.Ascension
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.pathways.ModPathways
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Every power of every pathway, used in a real world — the re-test of phase 16.
 *
 * A unit test cannot reach what an ability does: it needs a living caster, a world and ticks for
 * its sessions to run. Here each pathway walks Seq. 9 → 5; on every step the caster is bestowed,
 * the roster is built (names, descriptions, modes), every active is cast in every mode with a
 * full tank, every toggle is switched on, and the sessions get a couple of seconds to run.
 *
 * What fails the test is what the runtime would otherwise swallow: `AbilityExecutor`,
 * `SessionHost`, `EventHub`, `PassiveTicker` and the scheduler all catch a thrown power and only
 * log it — correct in a game, invisible in a build. The test listens to that log. Whether a power
 * *looks* right is still checked by playing; this catches the ones that do not run at all.
 */
class AbilityGameTests {

    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun whiteTower(h: GameTestHelper) = walk(h, "WhiteTower")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun sun(h: GameTestHelper) = walk(h, "Sun")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun door(h: GameTestHelper) = walk(h, "Door")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun error(h: GameTestHelper) = walk(h, "Error")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun visionary(h: GameTestHelper) = walk(h, "Visionary")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun tyrant(h: GameTestHelper) = walk(h, "Tyrant")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun justiciar(h: GameTestHelper) = walk(h, "Justiciar")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun fool(h: GameTestHelper) = walk(h, "Fool")
    @GameTest(maxTicks = MAX_TICKS, skyAccess = true, padding = PADDING) fun death(h: GameTestHelper) = walk(h, "Death")

    /** The thirteen stubs: no powers, but a potion's worth of ascension and a roster each. */
    @GameTest(maxTicks = 20, skyAccess = true, padding = PADDING)
    fun stubs(helper: GameTestHelper) {
        helper.floor()
        val player = helper.player(4, 4, "stub-walker")
        val stubs = ModPathways.all.filter { pathway -> (0..9).all { pathway.abilitiesFor(it).isEmpty() } }
        helper.expect(stubs.size == 13, "заглушок не 13: ${stubs.map { it.name }}")
        for (pathway in stubs) for (level in 9 downTo 0) {
            Ascension.bestow(player, pathway, Sequence(level))
            BeyonderStatus.roster(AbilityServices.beyonders.of(player.uuid))
        }
        Ascension.forget(player)
        helper.level.server.playerList.remove(player)
        helper.succeed()
    }

    private fun walk(helper: GameTestHelper, name: String) {
        Errors.install()
        helper.floor()
        val pathway = ModPathways.byName(name) ?: throw AssertionError("немає шляху $name")
        val packages = (0..9).flatMap(pathway::abilitiesFor).map { it::class.java.packageName }.toSet()
        val since = Errors.mark()

        val scene = Scene(helper, name)

        SEQUENCES.forEachIndexed { step, level ->
            helper.runAfterDelay(step * STEP_TICKS + 1L) { castEverything(helper, scene, pathway, Sequence(level)) }
        }

        val end = SEQUENCES.size * STEP_TICKS + 1L
        helper.runAfterDelay(end) { Ascension.forget(scene.caster) }
        // `finally` blocks of cancelled sessions run on the next mod tick, and they may throw too.
        helper.runAfterDelay(end + 5) {
            val ours = Errors.since(since).filter { it.belongsTo(packages) }
            if (ours.isNotEmpty()) helper.fail("$name: ${ours.size} збоїв\n" + ours.joinToString("\n") { it.describe() })
            scene.clear()
            helper.succeed()
        }
    }

    private fun castEverything(helper: GameTestHelper, scene: Scene, pathway: Pathway, sequence: Sequence) {
        val caster = scene.caster
        Ascension.bestow(caster, pathway, sequence)
        val beyonder = AbilityServices.beyonders.of(caster.uuid)!!
        helper.expect(beyonder.abilities.isNotEmpty(), "${pathway.name} Посл. ${sequence.level} без здібностей")
        // Names, descriptions, icons and modes: what the wheel and the book read.
        BeyonderStatus.roster(beyonder)

        // Half the powers want a creature under the gaze, the other half a player — so each is
        // cast at both. Toggles flip once and stay on, so their tick runs until the next step.
        for (gaze in Gaze.entries) {
            for (spec in beyonder.abilities) {
                val ability = spec as? Ability ?: continue
                if (ability is ToggleablePassiveAbility && gaze != Gaze.CREATURE) continue
                if (ability !is ActiveAbility && ability !is ToggleablePassiveAbility) continue
                val modes = ability.modes(sequence).map { it.id }.ifEmpty { listOf(null) }
                for (mode in modes) {
                    if (mode != null) AbilityServices.modes.select(caster.uuid, ability, sequence, mode)
                    // Empty-handed first; a power that wants a tool in hand gets each in turn.
                    for (tool in TOOLS) {
                        scene.reset(gaze, tool)
                        AbilityServices.cooldowns.forget(caster.uuid)
                        beyonder.setSpirituality(Spirituality.full(beyonder.maxSpirituality))
                        val report = AbilityRequests.cast(caster, ability.identity)
                        if ((report as? CastReport.Performed)?.outcome !is AbilityOutcome.Failed) break
                    }
                    if (ability is ToggleablePassiveAbility) break
                }
            }
        }
    }

    private enum class Gaze { CREATURE, PLAYER }

    /**
     * The caster, a husk and a bystander: a target that stands still and does not burn, and a
     * weak Beyonder of another pathway — someone to read, steal from, bind and talk to.
     */
    private class Scene(private val helper: GameTestHelper, name: String) {
        val caster: ServerPlayer = helper.player(3, 1, "caster-${name.lowercase()}")
        private val bystander: ServerPlayer = helper.player(4, 2, "bystander-${name.lowercase()}")
        private var husk: Mob? = null

        init {
            val other = ModPathways.all.first { it.name != name && it.abilitiesFor(9).isNotEmpty() }
            Ascension.bestow(bystander, other, Sequence(9))
        }

        /**
         * The same scene before every cast: a power before this one may have thrown the caster,
         * killed the husk, drunk the pool or blown out the altar.
         */
        fun reset(gaze: Gaze, tool: Item) {
            if (husk?.isAlive != true) {
                husk?.discard()
                husk = helper.spawn(EntityTypes.HUSK, helper.at(1, 4)).also { it.setNoAi(true) }
            }
            if (!bystander.isAlive) bystander.setHealth(bystander.maxHealth)
            caster.health = caster.maxHealth
            bystander.health = bystander.maxHealth
            home(caster, 3, 1)
            home(bystander, 4, 2)
            caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(tool))
            if (!caster.inventory.contains(ItemStack(Items.PAPER))) caster.inventory.add(ItemStack(Items.PAPER, 64))
            // An altar (Ritual Magic, the Theft of Heavenly Secrets) and a pool (the Tyrant's water).
            for ((x, z) in CANDLES) {
                helper.setBlock(x, 1, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true))
            }
            helper.setBlock(6, 0, 1, Blocks.WATER)
            val seen = if (gaze == Gaze.CREATURE) husk!! else bystander
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, seen.eyePosition)
        }

        fun clear() {
            husk?.discard()
            Ascension.forget(bystander)
            helper.level.server.playerList.remove(caster)
            helper.level.server.playerList.remove(bystander)
        }

        private fun home(player: ServerPlayer, x: Int, z: Int) {
            val spot = helper.at(x, z)
            player.deltaMovement = Vec3.ZERO
            player.snapTo(spot.x, spot.y, spot.z)
        }
    }

    private fun GameTestHelper.expect(condition: Boolean, what: String) {
        if (!condition) fail(what)
    }

    /**
     * The mod's error log, as the test sees it.
     *
     * Every isolating `catch` in the runtime ends in `log.error(…, throwable)` on the mod's logger,
     * so one appender on it sees them all. Errors are attributed to a pathway by the stack: the
     * nine walks run side by side, and a frame from a pathway's package is what says whose it is.
     */
    private object Errors : AbstractAppender("ability-game-tests", null, null, true, Property.EMPTY_ARRAY) {

        private val seen = ConcurrentLinkedQueue<Pair<Long, LogEvent>>()
        private var counter = 0L
        private var installed = false

        @Synchronized
        fun install() {
            if (installed) return
            installed = true
            start()
            (LogManager.getLogger(MOD_ID) as Logger).addAppender(this)
        }

        @Synchronized
        fun mark(): Long = counter

        fun since(mark: Long): List<LogEvent> = seen.filter { it.first >= mark }.map { it.second }

        @Synchronized
        override fun append(event: LogEvent) {
            if (event.level.isMoreSpecificThan(Level.ERROR)) seen += counter++ to event.toImmutable()
        }
    }

    private fun LogEvent.belongsTo(packages: Set<String>): Boolean {
        val frames = generateSequence(thrown) { it.cause }.flatMap { it.stackTrace.asSequence() }.map { it.className }.toList()
        val ours = frames.filter { it.startsWith(PATHWAYS) }
        // An error with no pathway frame at all has no owner to blame, so every walk reports it.
        return ours.isEmpty() || ours.any { frame -> packages.any { frame.startsWith("$it.") } }
    }

    private fun LogEvent.describe(): String =
        "• ${message.formattedMessage}: ${thrown?.let { "$it @ ${it.stackTrace.firstOrNull { f -> f.className.startsWith(PATHWAYS) }}" }}"

    private companion object {
        const val MAX_TICKS = 400
        const val PADDING = 64
        const val STEP_TICKS = 50
        const val PATHWAYS = "me.vangoo.mysteries.pathways"
        val SEQUENCES = listOf(9, 8, 7, 6, 5)

        /** What divination, the Sea's memory and paper weaving reach for — and an empty hand. */
        val TOOLS: List<Item> = listOf(Items.AIR, Items.SPYGLASS, Items.COMPASS, Items.BLAZE_ROD)

        /** Within `Altar.RADIUS` of the caster's spot, off everyone's feet. */
        val CANDLES = listOf(1 to 0, 5 to 0, 1 to 2)
    }
}
