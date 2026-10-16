package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityMode
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
 * ## The cue is a mode, picked before the touch
 *
 * The plugin opened a menu after the cast, i.e. with the target already in arm's reach. Here the
 * five cues are the ability's modes: picked ahead of time on the wheel (or by sneak + cast), so
 * the touch itself plants the cue at once — the whole point of a 3-block contact power.
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
            "▪ Навіювання обирається режимом наперед; присід + застосування перемикає\n" +
            "▪ Дальність $RANGE бл.",
    )

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Cue.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.label.substringBefore(" — "))) }

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.caster.isShiftKeyDown) {
            val next = context.modes.cycle(context.caster.uuid, this, context.sequence)
            return AbilityOutcome.Awaiting(Component.literal("Навіювання: ${next?.name?.string}"))
        }

        val target = context.targeting.lookingAt(RANGE) as? ServerPlayer
            ?: return AbilityOutcome.failed("Ціль має бути гравцем поруч")

        val cue = Cue.entries.first { it.name.lowercase() == mode(context) }
        cue.apply(context.level.server, context.events, context.cooldowns, target.uuid)
        return AbilityOutcome.done("Навіяно: ${cue.label}")
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
