package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.runtime.LiveTargeting
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import me.vangoo.mysteries.world.SpiritWorld
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.floor

/**
 * Door, Seq. 5 (Traveller): **The Traveller's Door**.
 *
 * The pathway's main power, and it holds two different verbs separated by sneaking:
 *
 * | how | what | price |
 * |---|---|---|
 * | an ordinary cast | a door into the **spirit world**: a white plain with no walls and no distance | the cast + upkeep |
 * | sneaking, with a lodestone compass | a door **into a point**: an instant passage to the lodestone | the cast |
 *
 * ## A door is a door, not a button
 *
 * The cast moves nothing. It **places a door** — it stands for
 * [DOOR_TICKS] / [TICKS_PER_SECOND] s, and while it stands nothing has happened:
 * [AbilityOutcome.Awaiting] keeps the ticket unsettled, so a Traveller who changed their mind has
 * paid neither spirituality nor a cooldown. The journey begins the moment they **step through** —
 * and that is exactly when the ticket is settled.
 *
 * The first revision switched on spectator mode right in `cast`, and the door was decoration
 * around a teleport. Now it is the passage itself.
 *
 * ## The party is gathered by gaze rather than by a menu
 *
 * While the door stands, looking at a player gives a prompt, and sneaking takes them along
 * ([MARK_RANGE] blocks to look, [FELLOW_RADIUS] blocks at the moment of entry). Those taken cross
 * over with the Traveller and come back with them — they have no door of their own, they are
 * passengers.
 *
 * ## The spirit world is a place, not a mode
 *
 * Behind the door lies [SpiritWorld]: a white, soundless mirror of the real world with the very
 * same coordinate grid. The player walks it on foot — no walls, no mountains, no water — and comes
 * out exactly where they stop.
 *
 * The way out is a door too: a punch places it in front of them, and **the real world itself
 * stands behind it** — its blocks, carried into the white at the same coordinates (see [carve]).
 * The Traveller sees whether they are coming out into a field, into an ocean or into somebody's
 * living room **before** they come out.
 *
 * The time in the white is limited ([TRAVEL_TICKS] / [TICKS_PER_SECOND] s) and costs
 * [UPKEEP_PER_SECOND] spirituality per second. When the time, the spirituality, the connection or
 * the server itself runs out, `finally` brings everyone back, and that is the same single return
 * path that in the plugin was assembled from four static maps.
 */
class TravellersDoor : ActiveAbility(AbilityIdentity.of("door_travellers_door")) {

    override val displayName: Component = Component.literal("Двері Мандрівника")

    override val spiritualityCost: Int = 300

    override fun periodicCost(sequence: Sequence): Int = UPKEEP_PER_SECOND

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.ENDER_EYE)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Двері, які ведуть або в духовний світ, або в точку.\n" +
            "▪ Каст: двері стоять ${DOOR_TICKS / TICKS_PER_SECOND} с — увійдіть, щоб вирушити\n" +
            "▪ Погляд на гравця + присідання: взяти його з собою\n" +
            "▪ У білому: ${TRAVEL_TICKS / TICKS_PER_SECOND} с, $UPKEEP_PER_SECOND духовності/с; " +
            "удар рукою відчиняє двері назад\n" +
            "▪ Присівши з компасом лодестона: перехід просто до нього\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // A recast means "close", not "open a second one". There is nothing to pay for giving up
        // one's own journey, so this is Failed rather than Done.
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.failed("Ви зачиняєте двері")
        }
        if (SpiritWorld.holds(context.caster)) {
            return AbilityOutcome.failed("Тут двері не відчиняються — ви вже поза світом")
        }
        return if (context.caster.isShiftKeyDown) leap(context) else open(context)
    }

    // ── A door into a point ─────────────────────────────────────────────────────

    private fun leap(context: CastContext): AbilityOutcome {
        val tracker = context.caster.mainHandItem.get(DataComponents.LODESTONE_TRACKER)
            ?: return AbilityOutcome.failed("Присідання відкриває двері до лодестона — візьміть його компас")
        val target = tracker.target().orElse(null)
            ?: return AbilityOutcome.failed("Компас нічого не пам'ятає")
        if (target.dimension() != context.level.dimension()) {
            return AbilityOutcome.failed("Лодестон в іншому вимірі — туди двері не ведуть")
        }

        val to = Vec3(target.pos().x + 0.5, target.pos().y + 1.0, target.pos().z + 0.5)
        val forward = horizontal(context.look)
        doorway(
            context.scheduling, context.level, context.origin.add(forward.scale(STEP_OUT)),
            forward, LEAP_DOOR_TICKS, context.effects.pathwayColor,
        )
        context.caster.teleportTo(to.x, to.y, to.z)
        doorway(
            context.scheduling, context.level, to.add(forward.scale(STEP_OUT)),
            forward, LEAP_DOOR_TICKS, context.effects.pathwayColor,
        )
        context.level.playSound(
            null, to.x, to.y, to.z,
            SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
        return AbilityOutcome.done("Двері вивели вас до лодестона")
    }

    // ── A door into the spirit world ────────────────────────────────────────────

    private fun open(context: CastContext): AbilityOutcome {
        val server = context.level.server
        val spirit = SpiritWorld.of(server)
            ?: return AbilityOutcome.failed("Духовний світ не відгукується")

        val forward = horizontal(context.look)
        val footing = context.origin.add(forward.scale(STEP_OUT))
        val home = context.level.dimension()
        val color = context.effects.pathwayColor
        val scheduling = context.scheduling
        val messaging = context.messaging
        // The only thing an ability is allowed to outlive the `cast` call with. Settled at the
        // moment of entry.
        val ticket = context.cast

        doorway(scheduling, context.level, footing, forward, DOOR_TICKS, color)
        context.level.playSound(
            null, footing.x, footing.y, footing.z,
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 1.5f,
        )

        context.session {
            val fellowship = LinkedHashSet<UUID>()
            val party = LinkedHashSet<UUID>()
            try {
                if (!awaitEntry(footing, fellowship, messaging)) {
                    messaging.actionBar(ownerId, Component.literal("Двері зачинились — ніхто не увійшов"))
                    return@session
                }
                ticket.complete()

                party += gather(server, fellowship)
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    SpiritWorld.enter(traveller, spirit)
                    messaging.actionBar(
                        traveller,
                        Component.literal("Удар рукою відчиняє двері назад; коліщатко змінює швидкість"),
                    )
                }
                wander(server, spirit, home, party, scheduling, messaging, color)
            } finally {
                val back = server.getLevel(home) ?: server.overworld()
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller)) SpiritWorld.leave(traveller, back)
                }
            }
        }

        return AbilityOutcome.Awaiting(
            Component.literal("Двері стоять ${DOOR_TICKS / TICKS_PER_SECOND} с — увійдіть, щоб вирушити"),
        )
    }

    /** While the door stands: gathering the party by gaze and waiting for the Traveller to cross the threshold. */
    private suspend fun SessionScope.awaitEntry(
        footing: Vec3,
        fellowship: MutableSet<UUID>,
        messaging: Messaging,
    ): Boolean {
        val deadline = ticksAlive + DOOR_TICKS
        val threshold = threshold(footing)
        while (ticksAlive < deadline) {
            val walker = owner ?: return false
            if (walker.boundingBox.intersects(threshold)) return true
            mark(walker, fellowship, messaging)
            waitTicks(WATCH_PERIOD_TICKS)
        }
        return false
    }

    /**
     * The gaze asks, the sneak answers.
     *
     * There is deliberately no separate key here: the server sees sneaking itself
     * (`isShiftKeyDown`), so gathering a party costs neither a packet nor a keybind.
     */
    private fun mark(walker: ServerPlayer, fellowship: MutableSet<UUID>, messaging: Messaging) {
        val seen = LiveTargeting(walker).playerLookingAt(MARK_RANGE) ?: return
        if (!walker.isShiftKeyDown) {
            if (seen.uuid !in fellowship) {
                messaging.actionBar(
                    walker,
                    Component.literal("Присядьте, щоб узяти ${seen.name.string} із собою"),
                )
            }
            return
        }
        if (!fellowship.add(seen.uuid)) return
        messaging.actionBar(walker, Component.literal("${seen.name.string} піде з вами"))
        messaging.tell(
            seen,
            Component.literal("Мандрівник бере вас у духовний світ — тримайтесь ближче за ${FELLOW_RADIUS.toInt()} бл."),
        )
        seen.level().playSound(
            null, seen.x, seen.y, seen.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.4f,
        )
    }

    /** Who actually went: the Traveller and those of the called who were nearby at the moment of entry. */
    private fun SessionScope.gather(server: MinecraftServer, fellowship: Set<UUID>): Set<UUID> {
        val walker = owner ?: return emptySet()
        val party = LinkedHashSet<UUID>()
        party += walker.uuid
        fellowship.forEach { id ->
            val friend = server.playerList.getPlayer(id) ?: return@forEach
            if (friend.level() === walker.level() && friend.distanceToSqr(walker) <= FELLOW_RADIUS * FELLOW_RADIUS) {
                party += id
            }
        }
        return party
    }

    /** The journey through the white: until the time or the spirituality runs out — or until a door back is found. */
    private suspend fun SessionScope.wander(
        server: MinecraftServer,
        spirit: ServerLevel,
        home: ResourceKey<Level>,
        party: Set<UUID>,
        scheduling: Scheduling,
        messaging: Messaging,
        color: RgbColor,
    ) {
        val back = server.getLevel(home) ?: server.overworld()
        val deadline = ticksAlive + TRAVEL_TICKS
        var exit: Exit? = null
        var exitCloses = 0L
        var sinceUpkeep = 0

        try {
            while (ticksAlive < deadline) {
                val walker = owner ?: return
                if (!SpiritWorld.holds(walker)) return
                // The terrain appears **around** the door, i.e. where the party is standing too: a
                // neighbouring column may turn out higher than the one the door was opened in, and
                // a block is born right inside a traveller. In adventure mode that is suffocation,
                // so anyone walled in is pushed upwards.
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller) && traveller.isInWall) {
                        traveller.teleportTo(traveller.x, traveller.y + 1.0, traveller.z)
                    }
                }
                // Flight speed is the active hotbar slot: the wheel switches nothing in the white,
                // but the server sees it with no packet from the mod at all.
                // Everyone in the party turns their own.
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller)) {
                        SpiritWorld.drift(traveller, traveller.inventory.selectedSlot)
                    }
                }
                spirit.sendParticles(
                    ModParticles.mote(color),
                    walker.x, walker.y + 1.0, walker.z,
                    2, 0.35, 0.6, 0.35, 0.0,
                )

                val open = exit
                if (open == null) {
                    // A punch rather than sneaking: sneaking in the white would mean the Traveller
                    // can neither sneak up nor hide without opening a door. The server sees the
                    // swing itself (`swinging` holds for a few ticks after the packet), so no key
                    // of our own is needed here — and there are no mobs in the spirit world to hit
                    // by accident.
                    if (walker.swinging) {
                        exit = openExit(scheduling, spirit, back, walker, color)
                        exitCloses = ticksAlive + EXIT_TICKS
                        messaging.actionBar(
                            walker,
                            Component.literal("Двері назад відчинено — за ними ваше місце виходу"),
                        )
                    }
                } else if (ticksAlive >= exitCloses) {
                    open.close()
                    exit = null
                } else if (walker.boundingBox.intersects(open.threshold)) {
                    return
                }

                sinceUpkeep += WATCH_PERIOD_TICKS
                if (sinceUpkeep >= TICKS_PER_SECOND) {
                    sinceUpkeep -= TICKS_PER_SECOND
                    if (!payUpkeep()) {
                        messaging.actionBar(ownerId, Component.literal("Духовності не стало — біле виштовхує вас"))
                        return
                    }
                }
                waitTicks(WATCH_PERIOD_TICKS)
            }
            messaging.actionBar(ownerId, Component.literal("Час у білому вийшов"))
        } finally {
            // The cut-out is real blocks in a real world. Not filling it back in means leaving a
            // hole in the white behind, and that is the only thing in this ability that outlives
            // the server.
            exit?.close()
        }
    }

    /** An opened door back: the threshold to step into, and the cut-out to fill back in. */
    private class Exit(val threshold: AABB, val close: () -> Unit)

    private fun openExit(
        scheduling: Scheduling,
        spirit: ServerLevel,
        home: ServerLevel,
        walker: ServerPlayer,
        color: RgbColor,
    ): Exit {
        val forward = horizontal(walker.getViewVector(1.0f))
        val footing = walker.position().add(forward.scale(STEP_OUT))
        doorway(scheduling, spirit, footing, forward, EXIT_TICKS, color)
        spirit.playSound(
            null, footing.x, footing.y, footing.z,
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 0.9f,
        )
        return Exit(threshold(footing), carve(scheduling, spirit, home, footing))
    }

    /**
     * **A cut-out in the white**: behind the door stands not a hologram but a real piece of the
     * real world — its blocks, inserted into the spirit world at **the very same coordinates**.
     *
     * ## Why not a "portal"
     *
     * Real portal rendering means a second world in the client's memory, streaming its chunks and
     * a second camera pass clipped by the door's plane: a subsystem the size of a separate mod.
     * The first revision tried to get by with particles (`BLOCK_MARKER` — a block texture per
     * cell), and it looked exactly like what it was: three holograms in a frame.
     *
     * Here is a different answer to the same question. The client **already** knows how to draw
     * the blocks of the world it is standing in — so the blocks are brought there. The cut-out has
     * real geometry, real textures, real depth and parallax: lean your head aside and you see past
     * the jamb. The price is that it is a **copy**, taken at the moment of opening: without
     * entities and without anyone else's changes.
     *
     * ## A circle, not a cone
     *
     * The first revision copied the cone of visibility through the frame — what is visible from
     * one spot and in one direction. But the Traveller needs not to peek through a crack but to
     * **recognise the terrain**: where they are coming out, what is nearby, whether this is the
     * place at all. So a circle of radius [CARVE_RADIUS] around the door is copied — and it
     * appears from the door outwards, column by column.
     *
     * The heights are taken from the real world's ground: the [SpiritWorld.surface] level lands on
     * the threshold, so what stands on the ground there stands on the threshold here.
     *
     * Returns what the cut-out is filled back in with. Calling it is **mandatory** — this is the
     * only thing this ability leaves behind in the world.
     */
    private fun carve(
        scheduling: Scheduling,
        spirit: ServerLevel,
        home: ServerLevel,
        footing: Vec3,
    ): () -> Unit {
        // ponytail: reading another world loads (and generates if needed) its chunks
        // synchronously. One pause for one door — and it would have happened a moment later
        // anyway, when the player steps through.
        val ground = SpiritWorld.surface(home, footing.x, footing.z).toInt()
        val base = BlockPos(floor(footing.x).toInt(), floor(footing.y).toInt(), floor(footing.z).toInt())
        val saved = HashMap<BlockPos, BlockState>()

        // Columns in order of increasing distance: the terrain appears from the door outwards
        // rather than landing as a finished slab.
        val columns = buildList {
            for (dx in -CARVE_RADIUS..CARVE_RADIUS) {
                for (dz in -CARVE_RADIUS..CARVE_RADIUS) {
                    if (dx * dx + dz * dz <= CARVE_RADIUS * CARVE_RADIUS) add(dx to dz)
                }
            }
        }.sortedBy { (dx, dz) -> dx * dx + dz * dz }

        var next = 0
        lateinit var handle: TaskHandle
        // Column by column across ticks, not all at once: a 50-block circle is seven by seven
        // chunks of the real world, and generating them all in one tick means seconds of a frozen
        // server.
        handle = scheduling.every(1, delayTicks = 0) {
            if (next >= columns.size) return@every handle.cancel()
            val until = minOf(next + COLUMNS_PER_TICK, columns.size)
            while (next < until) {
                val (dx, dz) = columns[next++]
                column(spirit, home, base, ground, dx, dz, saved)
            }
        }

        return {
            handle.cancel()
            saved.forEach { (spot, state) -> spirit.setBlock(spot, state, CARVE_FLAGS) }
        }
    }

    /**
     * One column of terrain: the skin of the real relief plus a cut-out in the white above it.
     *
     * What is copied is not the whole thickness but the **skin** — from [SKIN_BELOW] blocks below
     * the ground to the top of whatever stands in this column (`WORLD_SURFACE` catches both a tree
     * and a roof). Copying the thickness makes no sense: it cannot be seen through its own surface
     * anyway, and it costs as much as everything else put together.
     *
     * A dip in the relief would be covered from above by the white slab, so a ceiling is cut out
     * over the skin — up to [CLEAR_CAP] blocks, until air begins.
     */
    private fun column(
        spirit: ServerLevel,
        home: ServerLevel,
        base: BlockPos,
        ground: Int,
        dx: Int,
        dz: Int,
        saved: MutableMap<BlockPos, BlockState>,
    ) {
        val x = base.x + dx
        val z = base.z + dz
        // The same chunk-before-heightmap as in SpiritWorld.surface: an unloaded chunk returns
        // sea_level + 1, and the whole column would land in the wrong place.
        home.getChunk(x shr 4, z shr 4)
        val top = home.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        val solid = home.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        for (ry in (solid - SKIN_BELOW) until top) {
            val state = home.getBlockState(BlockPos(x, ry, z))
            // A chest without its own entity is a block that breaks everything walking over it.
            if (state.hasBlockEntity()) continue
            put(spirit, BlockPos(x, base.y + (ry - ground), z), state, saved)
        }

        var lid = base.y + (top - ground)
        val ceiling = lid + CLEAR_CAP
        while (lid < ceiling) {
            val spot = BlockPos(x, lid, z)
            if (spirit.getBlockState(spot).isAir) break
            put(spirit, spot, Blocks.AIR.defaultBlockState(), saved)
            lid++
        }
    }

    /** Places a block, remembering what was here before it. Identical blocks are not touched at all. */
    private fun put(
        spirit: ServerLevel,
        spot: BlockPos,
        state: BlockState,
        saved: MutableMap<BlockPos, BlockState>,
    ) {
        val was = spirit.getBlockState(spot)
        // Air above the white surface is already air — and that is most of the circle.
        if (was == state) return
        if (spot !in saved) saved[spot] = was
        spirit.setBlock(spot, state, CARVE_FLAGS)
    }

    /** The threshold: the volume one must step into for the door to fire. */
    private fun threshold(footing: Vec3): AABB =
        AABB.ofSize(
            footing.add(0.0, THRESHOLD_HEIGHT / 2.0, 0.0),
            THRESHOLD_WIDTH, THRESHOLD_HEIGHT, THRESHOLD_WIDTH,
        )

    private companion object {
        const val COOLDOWN_SECONDS = 50
        const val UPKEEP_PER_SECOND = 8

        /** How long the door waits for whoever will step into it. */
        const val DOOR_TICKS = 10 * TICKS_PER_SECOND
        const val TRAVEL_TICKS = 60 * TICKS_PER_SECOND
        /** How long a door back stands: the circle appears in ~1.5 s, the rest is for looking around. */
        const val EXIT_TICKS = 15 * TICKS_PER_SECOND
        const val LEAP_DOOR_TICKS = 3 * TICKS_PER_SECOND

        const val WATCH_PERIOD_TICKS = 4
        const val MARK_RANGE = 12.0
        const val FELLOW_RADIUS = 10.0

        /** A step forward: the door must stand in front of the Traveller, not on them. */
        const val STEP_OUT = 1.6
        const val THRESHOLD_WIDTH = 1.3
        const val THRESHOLD_HEIGHT = 2.2

        /** The radius of the terrain circle around the door — all around, not only forwards. */
        const val CARVE_RADIUS = 50
        /** How many columns per tick: the circle appears in ~26 ticks. */
        const val COLUMNS_PER_TICK = 300
        /** How much ground below the surface: just enough that the edge does not read as paper. */
        const val SKIN_BELOW = 3
        /** How far the white ceiling is cut out above a dip in the relief. */
        const val CLEAR_CAP = 16

        /**
         * `UPDATE_CLIENTS or UPDATE_SKIP_ALL_SIDEEFFECTS` (2 + 816).
         *
         * The client sees the block while the world does not notice its "appearance" at all. Each
         * addend here closes its own failure, and the most expensive is `UPDATE_SKIP_ON_PLACE`
         * (512): without it `LevelChunk.setBlockState` calls `onPlace`, `LiquidBlock.onPlace`
         * schedules a spread tick, and carried water flows into cells that are not in `saved` —
         * i.e. stays in the white **forever**.
         */
        const val CARVE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS
    }
}
