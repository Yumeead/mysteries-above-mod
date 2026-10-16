package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.PendingCast
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

/**
 * Visionary, Seq. 5 (Dreamwalker): **Dream Traversal**.
 *
 * Slip into the nearest sleeping mind and, five seconds later, wake up beside its body. The
 * range grows sharply below Seq. 5 — 500 blocks at Seq. 5, doubling each Sequence closer to 0.
 *
 * ## Every sleeper, on a screen
 *
 * At a 500-block radius there is no gaze to pick with, so the Dreamwalker is shown every dreaming
 * mind in reach — as in the plugin, each with what a dream gives away of its owner: how far, how
 * long they have lived in this world, how many they have killed. The pick opens through
 * [me.vangoo.mysteries.ability.runtime.PendingChoices]; a sleeper who wakes before the click is
 * gone from the dream, and the cast costs nothing.
 */
class DreamTraversal : ActiveAbility(AbilityIdentity.of("visionary_dream_traversal")) {

    override val displayName: Component = Component.literal("Блукання у снах")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Проникає у сон сплячого гравця й через $TELEPORT_DELAY_SECONDS с переносить вас до нього.\n" +
            "▪ Оберіть, чий сон: показано всіх сплячих у радіусі ${range(sequence)} бл.\n" +
            "▪ Якщо ціль прокинулась до вибору — нічого не витрачається",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val searchRange = range(context.sequence)
        val origin = context.origin
        val sleepers = context.targeting.playersNear(searchRange.toDouble()).filter { it.isSleeping }
            .sortedBy { it.position().distanceTo(origin) }
        if (sleepers.isEmpty()) return AbilityOutcome.failed("Немає сплячих гравців у радіусі $searchRange бл.")

        val ticket = context.cast
        val messaging = context.messaging
        val scheduling = context.scheduling
        val server = context.level.server
        val casterId = context.caster.uuid
        val sleeperIds = sleepers.map { it.uuid }
        val labels = sleepers.map { Component.literal(glimpse(it, origin)) }

        context.pendingChoices.offer(casterId, Component.literal("Чий сон відвідати"), labels) { index, _ ->
            val caster = server.playerList.getPlayer(casterId) ?: return@offer
            val sleeper = server.playerList.getPlayer(sleeperIds[index])
            if (sleeper == null || !sleeper.isSleeping) {
                messaging.tell(casterId, Component.literal("✗ Цей сон уже скінчився"))
                return@offer
            }
            enter(caster, sleeper, messaging, scheduling, server, ticket)
        }
        return AbilityOutcome.Awaiting()
    }

    /** What a dream gives away of its owner — the plugin's menu entry, as one line. */
    private fun glimpse(sleeper: ServerPlayer, origin: Vec3): String {
        val stats = sleeper.stats
        val hours = stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME) / (TICKS_PER_SECOND * 3600)
        val kills = stats.getValue(Stats.CUSTOM, Stats.PLAYER_KILLS)
        val distance = sleeper.position().distanceTo(origin).toInt()
        return "${sleeper.name.string} — $distance бл. · $hours год у світі · вбивств: $kills"
    }

    private fun enter(
        caster: ServerPlayer,
        sleeper: ServerPlayer,
        messaging: Messaging,
        scheduling: Scheduling,
        server: MinecraftServer,
        ticket: PendingCast,
    ) {
        val casterId = caster.uuid
        val targetId = sleeper.uuid
        val targetName = sleeper.name.string
        val here = caster.level()

        messaging.tell(caster, Component.literal("✦ Ви проникаєте у сон $targetName..."))
        messaging.tell(sleeper, Component.literal("У вашому сні з'являється невідома присутність..."))
        here.playSound(null, caster.x, caster.y, caster.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0f, 0.8f)
        here.sendParticles(ParticleTypes.PORTAL, caster.x, caster.y + 1.0, caster.z, 50, 0.5, 1.0, 0.5, 0.0)
        messaging.actionBar(caster, Component.literal("Ви занурюєтесь у сон... ($TELEPORT_DELAY_SECONDS с)"))

        scheduling.after(TELEPORT_DELAY_SECONDS * TICKS_PER_SECOND) {
            // Five seconds is long enough to die or change dimension: both ends are looked up again.
            val traveller = server.playerList.getPlayer(casterId) ?: return@after
            val target = server.playerList.getPlayer(targetId)
            if (target == null) {
                messaging.tell(casterId, Component.literal("✗ Ціль недоступна"))
                return@after
            }

            val destination = target.position().add((Math.random() * 4 - 2), 0.0, (Math.random() * 4 - 2))
            val level = target.level()
            val from = traveller.level()

            from.sendParticles(ParticleTypes.PORTAL, traveller.x, traveller.y + 1.0, traveller.z, 100, 0.5, 1.0, 0.5, 0.0)
            from.playSound(null, traveller.x, traveller.y, traveller.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f)

            traveller.teleportTo(level, destination.x, destination.y, destination.z, emptySet(), traveller.yRot, traveller.xRot, false)

            level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1.0, destination.z, 100, 0.5, 1.0, 0.5, 0.0)
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, destination.x, destination.y, destination.z, 30, 0.5, 0.5, 0.5, 0.0)
            level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f)

            traveller.addEffect(MobEffectInstance(MobEffects.BLINDNESS, TICKS_PER_SECOND, 0))
            messaging.tell(traveller, Component.literal("✓ Ви проникли у світ снів $targetName"))
            messaging.tell(targetId, Component.literal("✦ У ваш сон хтось увійшов..."))

            ticket.complete()
        }
    }

    private fun range(sequence: Sequence): Int {
        if (sequence.level >= 5) return BASE_RANGE
        return BASE_RANGE * (1 shl (5 - sequence.level))
    }

    private companion object {
        const val BASE_RANGE = 500
        const val TELEPORT_DELAY_SECONDS = 5
        const val COST = 200
        const val COOLDOWN_SECONDS = 60
    }
}
