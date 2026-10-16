package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Effects
import me.vangoo.mysteries.ability.context.Messaging
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Thunder Voice**.
 *
 * The Songster's voice rides a bolt of lightning to whoever it is aimed at — it passes through
 * any obstacle, and near the addressee it becomes a thunderclap that everyone nearby hears and
 * only the addressee understands as a call.
 *
 * ## One screen: the line, then the name
 *
 * The plugin picked the addressee in a chest menu and then waited 30 seconds for the message in
 * chat — two steps, and the second one leaked into the ordinary chat if the player forgot. Here
 * [me.vangoo.mysteries.ability.runtime.PendingChoices] opens one screen with a line of text and
 * the online players: type the words, click the name. An empty line still rings a summons.
 */
class ThunderVoice : ActiveAbility(AbilityIdentity.of("tyrant_thunder_voice")) {

    override val displayName: Component = Component.literal("Громовий Голос")

    override val spiritualityCost: Int = 25

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваш голос летить блискавкою крізь будь-які перепони й на будь-якій відстані.\n" +
            "▪ Напишіть послання й оберіть адресата\n" +
            "▪ Гуркіт чують усі поруч, слова — лише адресат",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val targets = context.level.server.playerList.players.filter { it.uuid != caster.uuid }
        if (targets.isEmpty()) {
            return AbilityOutcome.failed("Нікого кликати — ви самі в цьому світі")
        }

        val ticket = context.cast
        val messaging = context.messaging
        val effects = context.effects
        val server = context.level.server
        val casterId = caster.uuid
        val names = targets.map { it.name }
        val targetIds = targets.map { it.uuid }

        val title = Component.literal("Громовий Голос: кому і що")
        context.pendingChoices.offer(casterId, title, names, withText = true) { index, text ->
            // The screen may stay open a while: both ends are looked up again, never the ones from the cast.
            val speaker = server.playerList.getPlayer(casterId) ?: return@offer
            val target = server.playerList.getPlayer(targetIds[index])
            if (target == null) {
                messaging.tell(casterId, Component.literal("${names[index].string} більше не в мережі"))
                return@offer
            }

            deliver(effects, messaging, speaker, target, text)
            messaging.actionBar(casterId, Component.literal("Послання долетіло до ${target.name.string}"))
            ticket.complete()
        }

        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7f, 1.6f)
        return AbilityOutcome.Awaiting()
    }

    private fun deliver(effects: Effects, messaging: Messaging, caster: ServerPlayer, target: ServerPlayer, text: String) {
        effects.bodyAura(caster, RISE_TICKS)

        val casterAt = caster.position()
        caster.level().playSound(null, casterAt.x, casterAt.y, casterAt.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.4f)

        val level = target.level()
        val bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED)
        val at = target.position()
        if (bolt != null) {
            bolt.setVisualOnly(true)
            bolt.snapTo(at.x, at.y, at.z)
            level.addFreshEntity(bolt)
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 16, 0.4, 0.6, 0.4, 0.05)
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.3f)

        val line = if (text.isEmpty()) "кличе вас" else "гримить: «$text»"
        messaging.tell(target, Component.literal("⚡ Громовий Голос ${caster.name.string} $line"))
    }

    private companion object {
        const val COOLDOWN_SECONDS = 30
        const val RISE_TICKS = 24
    }
}
