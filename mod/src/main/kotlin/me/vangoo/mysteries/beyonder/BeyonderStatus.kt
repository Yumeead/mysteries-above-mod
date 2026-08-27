package me.vangoo.mysteries.beyonder

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.entities.Beyonder
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor

/**
 * Стан Потойбічного, яким його бачить клієнт.
 *
 * Джерело — живий реєстр. Порожній реєстр означає, що гравець ще звичайний, і це та
 * сама відповідь, яку екран уже вміє показувати. Наповнює реєстр сховище (фаза 09) на
 * вхід гравця, а до того — зілля.
 *
 * **Чистий збирач знімків.** Коли який знімок летить, вирішує [AbilityStatePump]; тут
 * лише «зібрати й віддати». Два знімки, а не один, бо частота в них різна на три
 * порядки: [roster] — одиниці разів за сесію, [state] — до разу на секунду. Злиття
 * означало б слати описи здібностей щосекунди.
 */
object BeyonderStatus {

    // ── Знімки ──────────────────────────────────────────────────────────────────

    fun snapshot(player: ServerPlayer): BeyonderStatusPayload {
        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return BeyonderStatusPayload(null)

        return BeyonderStatusPayload(
            BeyonderStatusPayload.Beyonder(
                pathwayId = beyonder.pathway.name,
                sequence = beyonder.sequence,
                spirituality = beyonder.spirituality,
            ),
        )
    }

    /**
     * Список здібностей з усім, що потрібно меню. Числа вже пораховані під
     * Послідовність власника — клієнт не має ні `SequenceScaler`, ні причин його мати.
     *
     * `AbilitySpec`, який не є [Ability], сюди не потрапляє: назви й іконки в нього
     * немає, а мовчазний пропуск чесніший за порожню картку. У моді таких і не буває —
     * усе, що реєструють шляхи, спадкує [Ability].
     *
     * Бере [Beyonder], а не гравця, навмисно: у списку здібностей немає нічого, що
     * потребувало б живого світу, тож знімок перевіряється звичайним юніт-тестом.
     * `null` — гравець не Потойбічний.
     */
    fun roster(beyonder: Beyonder?): AbilityRosterPayload {
        if (beyonder == null) return AbilityRosterPayload.NONE
        val pathway = beyonder.pathway

        return AbilityRosterPayload(
            AbilityRosterPayload.Roster(
                pathwayId = pathway.name,
                sequence = beyonder.sequence,
                sequenceName = Component.literal(pathway.sequenceName(beyonder.sequenceLevel)),
                entries = beyonder.abilities
                    .filterIsInstance<Ability>()
                    .map { entryOf(it, beyonder) },
            ),
        )
    }

    /** `null` — гравець не Потойбічний; про це вже сказав порожній [roster]. */
    fun state(player: ServerPlayer): AbilityStatePayload? {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return null
        val owner = player.uuid

        return AbilityStatePayload(
            serverTick = player.level().server.tickCount,
            spirituality = beyonder.spirituality,
            selected = AbilityServices.selection.of(owner)?.id,
            live = beyonder.abilities.mapNotNull { spec ->
                val readyAtTick = AbilityServices.cooldowns.readyAtTick(owner, spec.identity)
                val toggled = AbilityServices.passives.isEnabled(owner, spec.identity)
                val sustaining = AbilityServices.scheduling.isRunning(owner, spec.identity)
                // Готова, вимкнена й не триває — сказати про неї нічого.
                if (readyAtTick == 0 && !toggled && !sustaining) return@mapNotNull null
                AbilityStatePayload.Live(spec.identity.id, readyAtTick, toggled, sustaining)
            },
        )
    }

    // ── Відправка ───────────────────────────────────────────────────────────────

    fun push(player: ServerPlayer) {
        ModChannels.BEYONDER_STATUS.sendTo(listOf(player), snapshot(player))
    }

    /** Список здібностей став іншим: зілля, піднесення, демоція, вхід у світ, `forget`. */
    fun pushRoster(player: ServerPlayer) {
        ModChannels.ABILITY_ROSTER.sendTo(listOf(player), roster(AbilityServices.beyonders.of(player.uuid)))
    }

    /** Звичайному гравцеві не летить нічого: стан має сенс лише в Потойбічного. */
    fun pushState(player: ServerPlayer) {
        val state = state(player) ?: return
        ModChannels.ABILITY_STATE.sendTo(listOf(player), state)
    }

    // ── Збирання картки ─────────────────────────────────────────────────────────

    private fun entryOf(ability: Ability, beyonder: Beyonder): AbilityRosterPayload.Entry {
        val sequence = beyonder.sequence
        return AbilityRosterPayload.Entry(
            identity = ability.identity.id,
            name = ability.displayName,
            description = ability.description(sequence),
            type = ability.type,
            cost = ability.spiritualityCost,
            upkeep = ability.periodicCost(sequence),
            cooldownSeconds = ability.cooldownSeconds(sequence),
            icon = tinted(ability.icon, beyonder.pathway.name),
        )
    }

    /**
     * Фарбує іконку в колір шляху — безкоштовно, ванільним компонентом.
     *
     * Здібність, що оголосила власний `DYED_COLOR`, лишається зі своїм: перекриття
     * має бути сильнішим за типове, інакше воно нічого не варте.
     *
     * Копія навмисна. Типова іконка будується щоразу нова, але перекрита може бути
     * полем — і тоді фарба лягла б на спільний екземпляр назавжди.
     */
    private fun tinted(icon: ItemStack, pathwayName: String): ItemStack {
        if (icon.has(DataComponents.DYED_COLOR)) return icon
        val tinted = icon.copy()
        tinted.set(DataComponents.DYED_COLOR, DyedItemColor(PathwayBranding.liquidOf(pathwayName).packed))
        return tinted
    }
}
