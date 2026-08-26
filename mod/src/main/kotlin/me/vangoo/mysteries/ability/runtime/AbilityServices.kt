package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.AbilityExecutor
import me.vangoo.mysteries.schedule.TickScheduler
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Сервіси, які здібності бачать через `AbilityContext`, зібрані в одному місці.
 *
 * Не `ServiceContainer` плагіна: там DI-контейнер збирав ~40 сервісів і сам вирішував
 * порядок. Тут об'єктів рівно стільки, скільки їх існує, і кожна наступна фаза додає
 * свій (події — 06, ефекти — 07). Коли їх стане забагато для одного файлу, це й буде
 * сигналом розділити — не раніше.
 *
 * Прив'язані до життя сервера: інтегрований сервер одиночної гри піднімається і
 * гасне всередині того самого процесу, тож стан, переживши зупинку, приїхав би в
 * наступний світ. [reset] цього не допускає.
 */
object AbilityServices {

    private val log = LoggerFactory.getLogger(MOD_ID)

    private var server: MinecraftServer? = null

    private val onError: (String, Throwable) -> Unit = { what, error -> log.error(what, error) }

    private val players: (UUID) -> ServerPlayer? = { id -> server?.playerList?.getPlayer(id) }

    val beyonders = BeyonderRegistry()

    /** Годинник усього мода: власний лічильник, що цокає лише разом із тіком сервера. */
    val scheduler = TickScheduler(onError)

    val cooldowns = CooldownTable { server?.tickCount ?: 0 }

    val messaging = ServerMessaging { server }

    val scheduling = SessionHost(scheduler, beyonders, players, onError)

    val contexts = AbilityContexts(beyonders, cooldowns, messaging, scheduling)

    val passives = PassiveTicker(beyonders, contexts, players, onError)

    val executor = AbilityExecutor(beyonders, cooldowns, messaging, contexts)

    fun bootstrap() {
        ServerLifecycleEvents.SERVER_STARTING.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { reset() }

        // Один тік мода: спершу планувальник (сесії прокидаються), далі такт пасивок.
        // Порядок саме такий, щоб пасивка бачила світ уже після того, як сесії його змінили.
        ServerTickEvents.END_SERVER_TICK.register { s ->
            scheduler.advance()
            passives.tick(scheduler.tick)
            // Раз на пів хвилини: таблиця кулдаунів інакше росте на кожну здібність назавжди.
            if (s.tickCount % PRUNE_PERIOD_TICKS == 0) cooldowns.prune()
        }

        // Вихід гравця гасить його сесії детерміновано. Сесія й сама помітить, що власника
        // немає, але лише на своєму такті — а такт може бути й раз на п'ять секунд.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            passives.deactivate(player)
            passives.forget(player.uuid)
            scheduling.stopAll(player.uuid)
        }
    }

    private fun reset() {
        scheduling.shutdown()
        // Один прощальний тік. Скасування лише знімає сесію з очікування — розкрутка
        // стека до її `finally` диспетчиться в чергу, і без цього тіку прибирання
        // просто не відбулось би: наступного тіку в цього світу вже не буде.
        scheduler.advance()
        scheduler.cancelAll()
        beyonders.online.map { it.playerId }.forEach { id ->
            cooldowns.forget(id)
            passives.forget(id)
        }
        beyonders.online.toList().forEach { beyonders.forget(it.playerId) }
        server = null
    }

    private const val PRUNE_PERIOD_TICKS = 600
}
