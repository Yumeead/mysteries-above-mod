package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Error, Seq. 5 (Dream Stealer): **Attack Theft**.
 *
 * A blow aimed at you is a thing, and things can be taken. For a few seconds the next hit does
 * not land on the thief at all — it is lifted whole, side effects and all, and handed to whoever
 * they strike next.
 *
 * Lore: a Dream Stealer can take a Reaper's Cull or Flame without their body being burned by it.
 *
 * ## The window and the charge are two subscriptions, not a session
 *
 * Nothing here needs a heartbeat: one subscription waits for a blow and cancels itself the moment
 * it catches one, the second waits for the thief to strike and cancels itself when they do. Both
 * carry a term, so a theft nobody completes simply expires. `IncomingDamage` already carries both
 * halves — cancelling the blow and changing the number — so neither needed a new hook.
 *
 * ## Why cancelled and not reduced
 *
 * Reducing damage is what `CombatProficiency` does; this is a different verb. The blow is not
 * softened, it is **taken**: the wiki's point is that its side effects do not touch the thief
 * either, and a cancelled `IncomingDamage` never reaches vanilla at all — no sound, no
 * invulnerability frames, no fire, nothing.
 */
class AttackTheft : ActiveAbility(ConceptualTheft.key("attack")) {

    override val displayName: Component = Component.literal("Крадіжка удару")

    override val spiritualityCost: Int = ConceptualTheft.COST

    override fun cooldownSeconds(sequence: Sequence): Int = ConceptualTheft.COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Наступний удар по вас ви забираєте собі — цілим, разом із тим, що він ніс.\n" +
            "▪ Вікно ${windowTicks(sequence) / TICKS_PER_SECOND} с: перший удар по вас не проходить зовсім\n" +
            "▪ Забране висить ${SPEND_TICKS / TICKS_PER_SECOND} с і лягає у ваш наступний удар\n" +
            "▪ Стеля перенесеного — ${CAP.toInt()} шкоди\n" +
            "▪ Відкат ${ConceptualTheft.COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val messaging = context.messaging
        val level = context.level
        val mote = ModParticles.mote(context.effects.pathwayColor)

        lateinit var window: Subscription
        window = context.events.on<IncomingDamage>(windowTicks(context.sequence)) { blow ->
            if (blow.victim !== caster || blow.amount <= 0f) return@on
            val stolen = blow.amount.coerceAtMost(CAP)
            blow.cancel()
            window.cancel()

            level.sendParticles(
                mote, caster.x, caster.eyeY, caster.z, 24, 0.4, 0.5, 0.4, 0.0,
            )
            level.playSound(
                null, caster.x, caster.y, caster.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.5f,
            )
            messaging.actionBar(caster, Component.literal("Удар у ваших руках: ${stolen.toInt()}"))

            lateinit var charge: Subscription
            charge = context.events.on<IncomingDamage>(SPEND_TICKS) { strike ->
                if (strike.attacker !== caster || strike.victim === caster) return@on
                strike.amount += stolen
                charge.cancel()
                level.playSound(
                    null, strike.victim.x, strike.victim.y, strike.victim.z,
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7f, 1.5f,
                )
                messaging.actionBar(caster, Component.literal("Ви повернули чужий удар"))
            }
        }

        return AbilityOutcome.done("Ви готові прийняти удар — і не віддати його назад")
    }

    private fun windowTicks(sequence: Sequence): Int =
        scale(BASE_WINDOW_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_WINDOW_TICKS = 5 * TICKS_PER_SECOND
        const val SPEND_TICKS = 10 * TICKS_PER_SECOND

        /** Ten hearts is as much as one stolen blow can ever carry. */
        const val CAP = 20.0f
    }
}
