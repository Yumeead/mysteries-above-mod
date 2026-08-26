package me.vangoo.mysteries.server

import com.mojang.brigadier.CommandDispatcher
import me.vangoo.mysteries.net.ModChannels
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/mysteries status` — штовхає клієнту його стан, клієнт відкриває екран.
 *
 * Це друга половина перевірки пакетного шару: команда доводить напрямок
 * сервер → клієнт, кнопка «Оновити» на екрані — напрямок назад.
 */
object StatusCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mysteries").then(
                Commands.literal("status").executes { context ->
                    val player = context.source.playerOrException
                    ModChannels.BEYONDER_STATUS.sendTo(player, BeyonderStatusSource.snapshot(player))
                    context.source.sendSuccess({ Component.literal("Стан надіслано на клієнт.") }, false)
                    1
                },
            ),
        )
    }
}
