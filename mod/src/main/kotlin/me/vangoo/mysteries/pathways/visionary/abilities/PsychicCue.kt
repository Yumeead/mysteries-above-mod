package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Visionary, Seq. 7 (Psychiatrist): **Psychic Cue**.
 *
 * Touch a player and plant one psychological block in them for a minute: hunger, sloth,
 * pacifism, silence or apathy.
 *
 * ## A choice offered in chat, not a menu — on purpose, and on request
 *
 * This is one of the three abilities carrying the pending-choice seam
 * ([me.vangoo.mysteries.ability.runtime.PendingChoices]) rather than an item-based redesign: a
 * real screen is coming in phase 11, and this ability is written against that seam now so the
 * swap later touches only [cast], not its callers.
 *
 * ## The plugin's sixth option is not here yet
 *
 * From Seq. 6 the plugin added a navigational imperative that pulled the target to a point set
 * by right-clicking a block. `Targeting` has no block-ray query — only
 * [me.vangoo.mysteries.ability.context.Targeting.lookingAt] for the living — and there is no
 * hook for "this player right-clicked a block" on the mod's own
 * [me.vangoo.mysteries.event.GameEvent] bus yet. Bolting one on for a single ability's upgrade
 * would be exactly the interface-to-a-subsystem-that-does-not-exist the context's own rule warns
 * against; it lands once a block-targeting primitive is worth adding for its own sake.
 */
class PsychicCue : ActiveAbility(AbilityIdentity.of("visionary_psychic_cue")) {

    override val displayName: Component = Component.literal("Психічний сигнал")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE)).roundToInt()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Навіює цілі психологічну установку через прямий контакт. Діє ${DURATION_TICKS / TICKS_PER_SECOND} с.\n" +
            "▪ Оберіть навіювання: /mysteries choose <n>\n" +
            "▪ Дальність $RANGE бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE) as? ServerPlayer
            ?: return AbilityOutcome.failed("Ціль має бути гравцем поруч")

        val labels = Cue.entries.map { it.label }
        context.messaging.tell(context.caster, Component.literal("Оберіть навіювання — /mysteries choose <n>:"))
        labels.forEachIndexed { i, label -> context.messaging.tell(context.caster, Component.literal("${i + 1}. $label")) }

        val ticket = context.cast
        val messaging = context.messaging
        val events = context.events
        val cooldowns = context.cooldowns
        val server = context.level.server
        val casterId = context.caster.uuid
        val targetId = target.uuid

        context.pendingChoices.offer(casterId, labels) { index ->
            val cue = Cue.entries[index]
            cue.apply(server, events, cooldowns, targetId)
            messaging.actionBar(casterId, Component.literal("Навіяно: ${cue.label}"))
            ticket.complete()
        }

        return AbilityOutcome.Awaiting(Component.literal("Оберіть навіювання: /mysteries choose <n>"))
    }

    private enum class Cue(val label: String) {
        HUNGER("Голод — виснажує насичення") {
            override fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID) {
                server.playerList.getPlayer(targetId)?.addEffect(MobEffectInstance(MobEffects.HUNGER, DURATION_TICKS, 4))
            }
        },
        SLOTH("Млявість — заважає бігати") {
            override fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID) {
                server.playerList.getPlayer(targetId)?.addEffect(MobEffectInstance(MobEffects.SLOWNESS, DURATION_TICKS, 4))
            }
        },
        PACIFISM("Пацифізм — заборонити атакувати") {
            override fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID) {
                events.on<IncomingDamage>(DURATION_TICKS) { damage ->
                    if (damage.attacker?.uuid == targetId) damage.cancel()
                }
            }
        },
        SILENCE("Тиша — блокує здібності") {
            override fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID) {
                cooldowns.lockAll(targetId, DURATION_TICKS / TICKS_PER_SECOND)
            }
        },
        APATHY("Апатія — притуплює реакцію") {
            override fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID) {
                server.playerList.getPlayer(targetId)?.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, DURATION_TICKS, 4))
            }
        },
        ;

        abstract fun apply(server: MinecraftServer, events: Events, cooldowns: Cooldowns, targetId: UUID)
    }

    private companion object {
        const val RANGE = 3.0
        const val COST = 150
        const val BASE_COOLDOWN_SECONDS = 60
        const val DURATION_TICKS = 60 * TICKS_PER_SECOND
    }
}
