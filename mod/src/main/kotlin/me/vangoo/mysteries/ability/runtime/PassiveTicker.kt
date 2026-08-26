package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.context.Beyonders
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Такт пасивок — спадкоємець `PassiveAbilityScheduler` плюс `PassiveAbilityManager` плагіна
 * (490 рядків на двох).
 *
 * Скоротилось трьома рішеннями:
 *
 * 1. **Період оголошується, а не рахується.** Плагін кликав `tick()` кожній пасивці
 *    кожен тік, і та, якій треба було раз на секунду, тримала власне поле `runs`.
 *    Тепер це [Ticking.tickPeriodTicks] — число, а не стан, що переживе перезахід.
 * 2. **Контекст не кешується.** Плагін тримав мапу `UUID → IAbilityContext`, бо контекст
 *    коштував дорого й ніс `Player`. Тутешній — вісім посилань, і будується щотакту;
 *    зате він не може віддати `ServerPlayer`, який після респавну вже не той гравець.
 * 3. **Реєстру здібностей немає.** Плагін вів `Map<UUID, Set<AbilityIdentity>>` і
 *    `Map<UUID, Map<Class<?>, PermanentPassiveAbility>>` паралельно до самого
 *    Потойбічного; список здібностей і так лежить у `Beyonder`, і читається звідти.
 *    Лишились рівно два записи стану: **що ввімкнено** й **що вже активовано**.
 */
class PassiveTicker(
    private val beyonders: Beyonders,
    private val contexts: AbilityContexts,
    private val players: (UUID) -> ServerPlayer?,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    /** Тоглі, які гравець увімкнув. Порожня множина не зберігається. */
    private val enabled = HashMap<UUID, MutableSet<AbilityIdentity>>()

    /**
     * Постійні пасивки, яким уже сказали `onActivate`. Тримаються **екземпляри**, а не
     * ідентичності: коли здібність зникає з Потойбічного (демоція, зміна шляху), сказати
     * їй `onDeactivate` вже нема кому, якщо не тримати саму здібність.
     */
    private val activated = HashMap<UUID, MutableMap<AbilityIdentity, PermanentPassiveAbility>>()

    // ── Такт ────────────────────────────────────────────────────────────────────

    fun tick(tickCount: Long) {
        for (beyonder in beyonders.online.toList()) {
            val player = players(beyonder.playerId) ?: continue
            val on = enabled[beyonder.playerId]

            for (spec in beyonder.abilities) {
                val ticking = spec as? Ticking ?: continue
                if (spec is ToggleablePassiveAbility && on?.contains(spec.identity) != true) continue
                if (tickCount % ticking.tickPeriodTicks.coerceAtLeast(1) != 0L) continue

                guard("такт ${spec.identity} гравця ${beyonder.playerId}") {
                    ticking.tick(contexts.of(player, beyonder, spec))
                }
            }
        }
    }

    // ── Тоглі ───────────────────────────────────────────────────────────────────

    fun isEnabled(owner: UUID, ability: AbilityIdentity): Boolean =
        enabled[owner]?.contains(ability) == true

    /** Перемикає тогл. Повертає новий стан: `true` — увімкнено. */
    fun toggle(player: ServerPlayer, ability: ToggleablePassiveAbility): Boolean {
        val beyonder = beyonders.of(player.uuid) ?: return false
        val context = contexts.of(player, beyonder, ability)
        val on = enabled.getOrPut(player.uuid) { LinkedHashSet() }

        val nowEnabled = if (on.remove(ability.identity)) {
            guard("вимкнення ${ability.identity}") { ability.onDisable(context) }
            false
        } else {
            on += ability.identity
            guard("увімкнення ${ability.identity}") { ability.onEnable(context) }
            true
        }
        if (on.isEmpty()) enabled.remove(player.uuid)
        return nowEnabled
    }

    // ── Життєвий цикл ───────────────────────────────────────────────────────────

    /** Вхід у світ або поява нових здібностей: активує постійні пасивки, яких ще не активували. */
    fun activate(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid) ?: return
        val live = activated.getOrPut(player.uuid) { LinkedHashMap() }

        val current = beyonder.abilities.filterIsInstance<PermanentPassiveAbility>()

        // Втрачені при демоції чи зміні шляху — попрощатись, поки екземпляр іще під рукою.
        val lost = live.keys - current.map { it.identity }.toSet()
        for (identity in lost) {
            val ability = live.remove(identity) ?: continue
            guard("деактивація $identity") {
                ability.onDeactivate(contexts.of(player, beyonder, ability))
            }
        }

        for (ability in current) {
            if (live.putIfAbsent(ability.identity, ability) != null) continue
            guard("активація ${ability.identity}") {
                ability.onActivate(contexts.of(player, beyonder, ability))
            }
        }
    }

    /** Зміна Послідовності: список здібностей став іншим, тоглі гаснуть. */
    fun refresh(player: ServerPlayer) {
        disableToggles(player)
        activate(player)
    }

    /** Вихід зі світу або втрата статусу Потойбічного. */
    fun deactivate(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid)
        disableToggles(player)

        val live = activated.remove(player.uuid) ?: return
        if (beyonder == null) return
        for ((identity, ability) in live) {
            guard("деактивація $identity") {
                ability.onDeactivate(contexts.of(player, beyonder, ability))
            }
        }
    }

    /** Гравця вже немає на сервері — питати його ні про що, просто забути. */
    fun forget(owner: UUID) {
        enabled.remove(owner)
        activated.remove(owner)
    }

    private fun disableToggles(player: ServerPlayer) {
        val beyonder = beyonders.of(player.uuid) ?: return
        val on = enabled.remove(player.uuid) ?: return
        beyonder.abilities.filterIsInstance<ToggleablePassiveAbility>()
            .filter { it.identity in on }
            .forEach { ability ->
                guard("вимкнення ${ability.identity}") {
                    ability.onDisable(contexts.of(player, beyonder, ability))
                }
            }
    }

    /**
     * Одна впала пасивка не має спиняти такт решти. У плагіні цей самий інваріант тримали
     * три окремі `try/catch` у циклах `PassiveAbilityManager`.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            onError("Пасивка впала: $what", error)
        }
    }
}
