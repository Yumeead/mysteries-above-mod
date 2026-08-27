package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.ability.AbilityExecutor
import me.vangoo.mysteries.beyonder.AbilityStatePump
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.event.GameEventBridge
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TickScheduler
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
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
 * свій (ефекти — 07). Коли їх стане забагато для одного файлу, це й буде
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

    /**
     * Підписки здібностей на події гри. Годинник той самий, що в сесій — тік мода:
     * тимчасова підписка на паузі не старіє, як не старіє сесія.
     */
    val events = EventHub({ scheduler.tick }, onError)

    /** Яку здібність гравець тримає під клавішею. До фази 11 — увесь вибір, що є. */
    val selection = AbilitySelection()

    val contexts = AbilityContexts(beyonders, cooldowns, messaging, scheduling, events)

    val passives = PassiveTicker(beyonders, contexts, events, players, onError)

    val executor = AbilityExecutor(beyonders, cooldowns, messaging, contexts)

    /**
     * Коли штовхати клієнту стан здібностей. Годинник — той самий `tickCount`, у якому
     * рахує [cooldowns]: інакше запобіжник міряв би одне, а `readyAtTick` означав інше.
     */
    val statePump = AbilityStatePump(
        send = { id -> players(id)?.let(BeyonderStatus::pushState) },
        now = { (server?.tickCount ?: 0).toLong() },
    )

    fun bootstrap() {
        ServerLifecycleEvents.SERVER_STARTING.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { reset() }

        // Гра → події мода. Порядок пошуку гачка й єдиний міксин — див. `GameEventBridge`.
        GameEventBridge.install(events::publish)

        // Один тік мода: спершу планувальник (сесії прокидаються), далі такт пасивок.
        // Порядок саме такий, щоб пасивка бачила світ уже після того, як сесії його змінили.
        ServerTickEvents.END_SERVER_TICK.register { s ->
            scheduler.advance()
            passives.tick(scheduler.tick)
            // Духовність відновлюється раз на секунду — саме в цих одиницях і задані
            // ставки регенерації (`SpiritualityCalculator.REGEN_RATES` — очок за секунду).
            if (scheduler.tick % TICKS_PER_SECOND == 0L) {
                regenerate()
                // Секундний такт синхронізації — тут само, бо регенерація і є
                // найчастішим джерелом «брудних» гравців.
                statePump.flush()
            }
            // Раз на пів хвилини: таблиця кулдаунів інакше росте на кожну здібність назавжди.
            if (s.tickCount % PRUNE_PERIOD_TICKS == 0) cooldowns.prune()
        }

        // Вхід у світ: постійні пасивки самі себе не ввімкнуть. До фази 09 реєстр порожній
        // на старті, тож спрацьовує це лише для тих, кого вже наповнило зілля цієї сесії.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            passives.activate(player)
            // Список здібностей клієнт інакше не дізнається ніяк: ростер летить на зміну,
            // а вхід у світ — це зміна з «нічого» на «ось що є».
            BeyonderStatus.pushRoster(player)
            statePump.pushNow(player.uuid)
        }

        // Вихід гравця гасить його сесії детерміновано. Сесія й сама помітить, що власника
        // немає, але лише на своєму такті — а такт може бути й раз на п'ять секунд.
        // Смерть гасить сесії негайно. Сесія помітила б і сама — але лише на своєму такті,
        // а такт буває й раз на п'ять секунд: увесь цей час труп платив би за утримання.
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) scheduling.stopAll(entity.uuid)
        }

        // Респавн — це **новий** `ServerPlayer`, а смерть уже зняла всі ефекти. Пасивки,
        // що клали щось на гравця в `onActivate`, лишились би після цього ні з чим, тож
        // облік скидається й активація йде наново. `deactivate` тут не годиться: прощатись
        // довелося б із трупом, а прибирати за пасивкою вже нічого.
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            passives.forget(newPlayer.uuid)
            events.forget(newPlayer.uuid)
            passives.activate(newPlayer)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            passives.deactivate(player)
            passives.forget(player.uuid)
            events.forget(player.uuid)
            scheduling.stopAll(player.uuid)
            selection.forget(player.uuid)
            statePump.forget(player.uuid)
        }
    }

    /**
     * Відновлення духовності всім, хто в грі.
     *
     * Плагінний `BeyonderService.regenerateAll` ходив по **репозиторію**, тобто й по тих,
     * кого в грі немає, і відсіював їх усередині. Тут реєстр і так тримає лише живих.
     */
    private fun regenerate() {
        beyonders.online.forEach { beyonder ->
            val before = beyonder.spirituality.current
            beyonder.regenerateSpirituality()
            // Бруднити лише тоді, коли число справді зросло: гравець зі стелею духовності
            // не має породжувати жодного пакета.
            if (beyonder.spirituality.current != before) statePump.markDirty(beyonder.playerId)
        }
    }

    private fun reset() {
        GameEventBridge.reset()
        events.clear()
        scheduling.shutdown()
        // Один прощальний тік. Скасування лише знімає сесію з очікування — розкрутка
        // стека до її `finally` диспетчиться в чергу, і без цього тіку прибирання
        // просто не відбулось би: наступного тіку в цього світу вже не буде.
        scheduler.advance()
        scheduler.cancelAll()
        beyonders.online.map { it.playerId }.forEach { id ->
            cooldowns.forget(id)
            passives.forget(id)
            selection.forget(id)
            statePump.forget(id)
        }
        beyonders.online.toList().forEach { beyonders.forget(it.playerId) }
        server = null
    }

    private const val PRUNE_PERIOD_TICKS = 600
}
