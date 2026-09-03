package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Thunder Voice**.
 *
 * The Songster's voice rides a bolt of lightning to whoever it is aimed at — it passes through
 * any obstacle, and near the addressee it becomes a thunderclap that everyone nearby hears and
 * only the addressee understands as a call.
 *
 * ## A pending choice instead of a menu, a call instead of free text
 *
 * Picking the addressee uses [me.vangoo.mysteries.ability.runtime.PendingChoices] — the same
 * no-menu seam `PsychicCue` and `Alteration` use ahead of phase 11's real screens. Composing a
 * free chat message afterward, the way the plugin did with a 30-second chat prompt, has no
 * counterpart yet: capturing a line of chat for one ability is exactly the `ui` seam phase 11
 * owns, not a thing to invent here. The lightning still calls the chosen player across any
 * distance — it just rings a summons rather than carrying a written line.
 */
class ThunderVoice : ActiveAbility(AbilityIdentity.of("tyrant_thunder_voice")) {

    override val displayName: Component = Component.literal("Громовий Голос")

    override val spiritualityCost: Int = 25

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваш голос летить блискавкою крізь будь-які перепони й на будь-якій відстані.\n" +
            "▪ Оберіть адресата: /mysteries choose <n>\n" +
            "▪ Гуркіт лунає біля нього — і кличе саме вас",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val targets = context.level.server.playerList.players.filter { it.uuid != caster.uuid }
        if (targets.isEmpty()) {
            return AbilityOutcome.failed("Нікого кликати — ви самі в цьому світі")
        }

        val labels = targets.map { it.name.string }
        context.messaging.tell(caster, Component.literal("Кого покликати громом — /mysteries choose <n>:"))
        labels.forEachIndexed { i, label -> context.messaging.tell(caster, Component.literal("${i + 1}. $label")) }

        val ticket = context.cast
        val messaging = context.messaging
        val level = context.level
        val casterName = caster.name.string
        val casterId = caster.uuid

        context.pendingChoices.offer(casterId, labels) { index ->
            val target = level.server.playerList.getPlayer(targets[index].uuid)
            if (target == null) {
                messaging.tell(casterId, Component.literal("${labels[index]} більше не в мережі"))
                return@offer
            }

            deliver(context, caster, target)
            messaging.actionBar(casterId, Component.literal("Послання долетіло до ${target.name.string}"))
            ticket.complete()
        }

        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7f, 1.6f)
        return AbilityOutcome.Awaiting(Component.literal("Оберіть адресата: /mysteries choose <n>"))
    }

    private fun deliver(context: CastContext, caster: ServerPlayer, target: ServerPlayer) {
        context.effects.bodyAura(caster, RISE_TICKS)

        val casterAt = caster.position()
        context.level.playSound(null, casterAt.x, casterAt.y, casterAt.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.4f)

        val bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(context.level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED)
        val at = target.position()
        if (bolt != null) {
            bolt.setVisualOnly(true)
            bolt.snapTo(at.x, at.y, at.z)
            context.level.addFreshEntity(bolt)
        }
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 16, 0.4, 0.6, 0.4, 0.05)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.3f)

        target.sendSystemMessage(Component.literal("⚡ Громовий Голос ${caster.name.string} кличе вас"))
    }

    private companion object {
        const val COOLDOWN_SECONDS = 30
        const val RISE_TICKS = 24
    }
}
