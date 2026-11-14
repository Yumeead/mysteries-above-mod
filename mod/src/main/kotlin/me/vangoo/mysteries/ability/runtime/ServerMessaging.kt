package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.HintPayload
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Addresses by UUID through the server's player list — whatever dimension the target is in. */
class ServerMessaging(private val server: () -> MinecraftServer?) : Messaging {

    override fun tell(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message)
    }

    override fun actionBar(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message, true)
    }

    override fun tell(playerId: UUID, message: Component) {
        find(playerId)?.let { tell(it, message) }
    }

    override fun actionBar(playerId: UUID, message: Component) {
        find(playerId)?.let { actionBar(it, message) }
    }

    override fun hint(player: ServerPlayer, message: Component, fizzle: Fizzle) {
        // A vanilla client draws no fizzle and has no setting to read hints from: the action bar is all it has.
        if (ModChannels.HINT.reaches(player)) ModChannels.HINT.sendTo(player, HintPayload(fizzle, message))
        else actionBar(player, message)
    }

    override fun hint(playerId: UUID, message: Component, fizzle: Fizzle) {
        find(playerId)?.let { hint(it, message, fizzle) }
    }

    private fun find(playerId: UUID): ServerPlayer? = server()?.playerList?.getPlayer(playerId)
}
