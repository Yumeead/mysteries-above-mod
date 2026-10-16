package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.PendingCast
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.runtime.PendingChoices
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Visionary, Seq. 5 (Dreamwalker): **Alteration**.
 *
 * Plants a trap in someone's own behaviour: pick what sets it off, pick what happens, and three
 * minutes later it is still waiting, invisible until it fires.
 *
 * ## Two screens, not three menus
 *
 * [PendingChoices] asks for the trigger, then the effect — the second screen opens the moment the
 * first is answered. Closing either leaves the ticket unsettled: no trap, nothing paid.
 *
 * ## Nine triggers became two
 *
 * The plugin's triggers were pickup-item, touch-water, attack, break-block, open-chest, eat-food,
 * sneak, take-damage, chat — nine events Bukkit handed it directly. The mod's own
 * [me.vangoo.mysteries.event.GameEvent] bus carries three events total, and only one of them —
 * [IncomingDamage] — matches anything on that list, in both directions (the target as attacker,
 * the target as victim). Offering the other seven would mean a trigger the player picks and
 * watches never fire — worse than not offering it. They return once their own events reach the
 * bus, the same way `DecryptPatterns` and `Record` waited for `AbilityCast` to land in Door.
 *
 * The plugin's "view active" and "remove" submenus are gone outright, not deferred: they only
 * read state back, they never wrote any, and [MAX_PER_TARGET] enforces the one rule that
 * mattered — a full target cannot take a third trap until one expires.
 */
class Alteration : ActiveAbility(AbilityIdentity.of("visionary_alteration")) {

    override val displayName: Component = Component.literal("Видозміна")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Непомітно закладає у ціль тригер і наслідок: коли тригер спрацює, ціль відчує наслідок.\n" +
            "▪ Тримається ${MODIFICATION_DURATION_TICKS / TICKS_PER_SECOND / 60} хв, не більше $MAX_PER_TARGET на цілі\n" +
            "▪ Після застосування оберіть тригер і наслідок",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE) as? ServerPlayer
            ?: return AbilityOutcome.failed("Ціль має бути гравцем поруч")

        if ((active[target.uuid] ?: 0) >= MAX_PER_TARGET) {
            return AbilityOutcome.failed("На цій цілі вже досягнуто ліміт видозмін")
        }

        val ticket = context.cast
        val messaging = context.messaging
        val events = context.events
        val scheduling = context.scheduling
        val pendingChoices = context.pendingChoices
        val casterId = context.caster.uuid
        val targetId = target.uuid

        pendingChoices.offer(casterId, Component.literal("Видозміна: тригер"), Trigger.entries.map { Component.literal(it.label) }) { triggerIndex, _ ->
            val trigger = Trigger.entries[triggerIndex]
            offerEffect(messaging, events, scheduling, pendingChoices, casterId, targetId, trigger, ticket)
        }

        return AbilityOutcome.Awaiting()
    }

    private fun offerEffect(
        messaging: Messaging,
        events: Events,
        scheduling: Scheduling,
        pendingChoices: PendingChoices,
        casterId: UUID,
        targetId: UUID,
        trigger: Trigger,
        ticket: PendingCast,
    ) {
        val title = Component.literal("Видозміна: ${trigger.label.lowercase()} →")
        pendingChoices.offer(casterId, title, Effect.entries.map { Component.literal(it.label) }) { effectIndex, _ ->
            plant(messaging, events, scheduling, casterId, targetId, trigger, Effect.entries[effectIndex])
            ticket.complete()
        }
    }

    private fun plant(
        messaging: Messaging,
        events: Events,
        scheduling: Scheduling,
        casterId: UUID,
        targetId: UUID,
        trigger: Trigger,
        effect: Effect,
    ) {
        var lastTriggered = 0L
        events.on<IncomingDamage>(MODIFICATION_DURATION_TICKS) { damage ->
            val fired = when (trigger) {
                Trigger.ATTACK -> damage.attacker?.uuid == targetId
                Trigger.TAKE_DAMAGE -> damage.victim.uuid == targetId
            }
            if (!fired) return@on
            val now = scheduling.tick
            if (now - lastTriggered < RETRIGGER_GUARD_TICKS) return@on
            lastTriggered = now
            effect.apply(damage.victim.level() as ServerLevel, messaging, targetId)
        }

        active.merge(targetId, 1, Int::plus)
        scheduling.after(MODIFICATION_DURATION_TICKS) { active.merge(targetId, -1, Int::plus) }

        messaging.tell(casterId, Component.literal("✓ Видозміну успішно створено: ${trigger.label} → ${effect.label}"))
    }

    private enum class Trigger(val label: String) {
        ATTACK("Коли ціль атакує когось"),
        TAKE_DAMAGE("Коли ціль отримує шкоду"),
    }

    private enum class Effect(val label: String) {
        BLINDNESS("Сліпота") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) =
                affect(level, messaging, targetId, MobEffects.BLINDNESS, 0, "Раптова темрява...")
        },
        WEAKNESS("Слабкість") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) =
                affect(level, messaging, targetId, MobEffects.WEAKNESS, 1, "Сили покидають вас...")
        },
        HUNGER("Голод") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) =
                affect(level, messaging, targetId, MobEffects.HUNGER, 1, "Раптовий голод...")
        },
        CONFUSION("Запаморочення") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) =
                affect(level, messaging, targetId, MobEffects.NAUSEA, 0, "Голова крутиться...")
        },
        FREEZE("Заморожування") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) =
                affect(level, messaging, targetId, MobEffects.SLOWNESS, 250, "Тіло застигає...")
        },
        DAMAGE("Шкода") {
            override fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID) {
                val player = level.server.playerList.getPlayer(targetId) ?: return
                player.hurtServer(level, level.damageSources().magic(), 2.0f)
                messaging.tell(targetId, Component.literal("Різкий біль..."))
            }
        },
        ;

        abstract fun apply(level: ServerLevel, messaging: Messaging, targetId: UUID)

        protected fun affect(
            level: ServerLevel,
            messaging: Messaging,
            targetId: UUID,
            effect: Holder<MobEffect>,
            amplifier: Int,
            note: String,
        ) {
            val player = level.server.playerList.getPlayer(targetId) ?: return
            player.addEffect(MobEffectInstance(effect, EFFECT_DURATION_TICKS, amplifier))
            messaging.tell(targetId, Component.literal(note))
            level.playSound(null, player.x, player.y, player.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1f, 1f)
        }
    }

    private companion object {
        const val RANGE = 5.0
        const val COST = 250
        const val COOLDOWN_SECONDS = 90
        const val MODIFICATION_DURATION_TICKS = 3 * 60 * TICKS_PER_SECOND
        const val EFFECT_DURATION_TICKS = 10 * TICKS_PER_SECOND
        const val RETRIGGER_GUARD_TICKS = 3 * TICKS_PER_SECOND
        const val MAX_PER_TARGET = 2

        val active = ConcurrentHashMap<UUID, Int>()
    }
}
