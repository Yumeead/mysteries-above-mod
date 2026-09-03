package me.vangoo.mysteries.world

import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.floor

/**
 * The **spirit world** — the first of the mod's worlds beyond the three vanilla ones.
 *
 * A white, soundless mirror of the real world: the same coordinate grid, but without a single
 * one of its blocks. The Traveller enters through a door, crosses the white plain on foot and
 * leaves wherever they chose — and leaves **at the very same X/Z** they would be looking at
 * standing here.
 *
 * ## A world is data, not code
 *
 * Everything it is lies in `data/mysteries_above/`: `dimension_type` (white haze, an eternal
 * noon, `coordinate_scale: 1.0` — the same grid), `dimension` (the generator and the biome),
 * `worldgen/noise_settings` (calcite under a snow crust, the surface waving ±4 blocks around
 * y = 64) and `worldgen/biome` (no features, no spawns). There is exactly as much code here as
 * it takes to bring the player in and take them back.
 *
 * ## A 1-to-1 ratio — in X and Z, not in Y
 *
 * The vertical is deliberately not mirrored: a real cave at y = -40 would be emptiness under the
 * white world's floor. The player lands on the white surface at the same X/Z and gets a new Y —
 * and gets it anew again on the way out.
 */
object SpiritWorld {

    val LEVEL: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, modId("spirit_world"))

    fun of(server: MinecraftServer): ServerLevel? = server.getLevel(LEVEL)

    fun holds(player: ServerPlayer): Boolean = player.level().dimension() == LEVEL

    /**
     * Takes the player into the white: the same X/Z, the surface instead of the height, a
     * weightless body.
     *
     * The game mode becomes **adventure**: a spirit body holds no tool and builds no walls, and
     * the white belongs to nobody to be taken apart. The previous mode is remembered right here
     * — otherwise there would be nowhere to bring the player back to.
     *
     * ⚠ The order is mandatory: `setGameMode` **resets** the abilities for the new mode
     * (`GameType.updatePlayerAbilities`), so flight is granted after it, not before.
     */
    fun enter(player: ServerPlayer, spirit: ServerLevel) {
        modes.putIfAbsent(player.uuid, player.gameMode())
        place(player, spirit)
        player.setGameMode(GameType.ADVENTURE)
        player.abilities.mayfly = true
        player.abilities.setFlyingSpeed(flySpeed(DEFAULT_NOTCH))
        player.onUpdateAbilities()
    }

    /** Takes the player back to [home] — again to the same X/Z, in their own mode and their own weight. */
    fun leave(player: ServerPlayer, home: ServerLevel) {
        place(player, home)
        // The server's mode is a fallback for exactly one case: the server was restarted while
        // the player was in the white, and the memory of their mode did not survive it.
        player.setGameMode(modes.remove(player.uuid) ?: home.server.defaultGameType)
        player.abilities.flying = false
        player.abilities.setFlyingSpeed(VANILLA_FLY_SPEED)
        player.onUpdateAbilities()
    }

    /**
     * Flight speed by the notch [notch] (0–8).
     *
     * In the spirit world the scroll wheel has nothing to switch items with, but the server sees
     * it as a change of the active slot — so that is what controls the speed, as in spectator
     * mode. No key and no packet of our own is needed for it.
     */
    fun drift(player: ServerPlayer, notch: Int) {
        val speed = flySpeed(notch)
        if (player.abilities.flyingSpeed == speed) return
        player.abilities.setFlyingSpeed(speed)
        player.onUpdateAbilities()
    }

    fun flySpeed(notch: Int): Float =
        SLOWEST_FLY_SPEED + notch.coerceIn(0, NOTCHES - 1) * FLY_SPEED_STEP

    /**
     * The first free height above the ground in this column.
     *
     * ⚠ `Level.getHeight` queries the heightmap **only in a loaded** chunk, and otherwise
     * silently returns `sea_level + 1`. In the spirit world that is y = 1, i.e. the bottom under
     * the whole thickness of calcite: the player appeared walled in and suffocated, and the same
     * happened on the way back. So the chunk is loaded first (generated if needed) — the
     * teleport would load it a moment later anyway — and only then the height is asked for.
     */
    fun surface(level: ServerLevel, x: Double, z: Double): Double {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        level.getChunk(bx shr 4, bz shr 4)
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)
        return roomFor(level, bx, top, bz).toDouble()
    }

    /** The first place from [from] upwards where the player has two free cells. */
    private fun roomFor(level: ServerLevel, x: Int, from: Int, z: Int): Int {
        val cursor = BlockPos.MutableBlockPos()
        val ceiling = level.maxY - 2
        var y = from.coerceIn(level.minY + 1, ceiling)
        while (y < ceiling) {
            val free = level.getBlockState(cursor.set(x, y, z)).isAir &&
                level.getBlockState(cursor.set(x, y + 1, z)).isAir
            if (free) return y
            y++
        }
        return from
    }

    /**
     * A player who left the game mid-journey would otherwise stay in the white forever: the
     * session goes out together with their connection, and there is nobody left to bring them back.
     */
    fun guardStranded() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            if (holds(player)) leave(player, server.overworld())
        }
    }

    private fun place(player: ServerPlayer, into: ServerLevel) {
        val landing = surface(into, player.x, player.z)
        player.teleportTo(into, player.x, landing, player.z, emptySet(), player.yRot, player.xRot, true)
        player.resetFallDistance()
    }

    /**
     * Whose mode to restore when the player leaves.
     *
     * It lives purely in the server's memory: a journey is shorter than that, and a player who
     * left the game in the middle of one is brought back by [guardStranded] — with the server's
     * mode by then.
     */
    private val modes = HashMap<UUID, GameType>()

    /** How many speed notches the wheel gives: exactly as many as there are hotbar slots. */
    private const val NOTCHES = 9
    private const val DEFAULT_NOTCH = 2
    private const val SLOWEST_FLY_SPEED = 0.05f
    private const val FLY_SPEED_STEP = 0.075f
    private const val VANILLA_FLY_SPEED = 0.05f
}
