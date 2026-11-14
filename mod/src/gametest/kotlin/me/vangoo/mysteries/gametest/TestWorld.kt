package me.vangoo.mysteries.gametest

import com.mojang.authlib.GameProfile
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** How far the stone yard reaches past the test's own 8×8. */
private const val YARD = 14

/**
 * A stone yard to stand on, wider than the test's own 8×8: its edge is a barrier wall even with
 * sky access, and a creature that must run or keep its distance needs more room than that. The
 * world creatures may fight in: the test server runs on Peaceful, and vanilla's `Mob.canAttack`
 * refuses any player as a target there — rightly so, and the mod's spawner keeps to the same rule.
 */
internal fun GameTestHelper.floor() {
    level.server.setDifficulty(Difficulty.NORMAL, true)
    for (x in -YARD..7 + YARD) for (z in -YARD..7 + YARD) {
        setBlock(x, 0, z, Blocks.STONE)
        for (y in 1..4) if (getBlockState(BlockPos(x, y, z)).`is`(Blocks.BARRIER)) setBlock(x, y, z, Blocks.AIR)
    }
}

internal fun GameTestHelper.at(x: Int, z: Int): Vec3 = Vec3.atBottomCenterOf(absolutePos(BlockPos(x, 1, z)))

/**
 * A survival player whose "client" has finished loading.
 *
 * Vanilla's `makeMockServerPlayerInLevel` will not do: its player answers `gameMode()` with
 * CREATIVE no matter what, and no creature fights a creative player. This is the same
 * assembly — a fake connection, `placeNewPlayer` — without that override. The load packet is
 * the one a real client sends; until it arrives the server treats the player as not yet in the
 * world and invulnerable to everything.
 */
internal fun GameTestHelper.player(x: Int, z: Int, name: String = "test-survivor"): ServerPlayer {
    val server = level.server
    val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), name), false)
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
