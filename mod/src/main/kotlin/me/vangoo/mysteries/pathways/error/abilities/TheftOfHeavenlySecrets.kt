package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.item.ModComponents
import me.vangoo.mysteries.pathways.common.Altar
import me.vangoo.mysteries.pathways.common.RitualMagic
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Error, Seq. 5 (Dream Stealer): **Theft of the Secrets of Heaven**.
 *
 * The one theft that takes neither a thing nor a capacity but **belonging**: for ten minutes the
 * Dream Stealer passes as a follower of someone else's god, and the god's own answers come to
 * their hands.
 *
 * ## The god is a pathway, and the offering names it
 *
 * The plugin hung this on its church subsystem, which this mod cut on purpose and will rebuild
 * with different rules. It did not have to wait for that: the divinity a Beyonder can impersonate
 * already exists in the mod, and it is the **pathway**. So the rite reads a pathway's potion left
 * on the ground as the offering — someone else's brew, laid before someone else's candles — and
 * the disguise takes the shape of whatever was offered. Stealing a potion to pass as its owner's
 * kin is the most Error-shaped way this could possibly work.
 *
 * When churches return, they attach to this same disguise at one point and nothing else changes.
 *
 * ## The altar is the parameter
 *
 * Candles burning, an offering on the ground — the reading `RitualMagic` invented and now shares
 * through `Altar`. No menu asks which god: the world already says so, out loud, where anyone can
 * see it and anyone can steal it.
 *
 * ## What the disguise actually grants
 *
 * The stolen prayer answers as the borrowed god's **weakest tier** — the Sequence 9 roster of that
 * pathway — plus ritual magic that was never Error's to know. It is not the borrowed pathway's
 * strength, it is its **welcome**: the doors open, and what is behind them is a novice's share.
 *
 * The disguise does **not** take the theft slot: it holds nothing of anybody's, and the plugin
 * made the same call for the same reason.
 */
class TheftOfHeavenlySecrets : ActiveAbility(AbilityIdentity.of("error_theft_of_heavenly_secrets")) {

    override val displayName: Component = Component.literal("Крадіжка Таємниць Небес")

    override val spiritualityCost: Int = 140

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви видаєте себе за чужого — і чуже небо відповідає вам як своєму.\n" +
            "▪ Потрібен вівтар: щонайменше $MIN_CANDLES запалених свічок навколо\n" +
            "▪ І приношення на землі — зілля того шляху, за чийого ви себе видаєте\n" +
            "▪ На ${HOLD_TICKS / TICKS_PER_SECOND / 60} хв ви отримуєте його найнижчі сили й ритуальну магію\n" +
            "▪ Другий каст, поки личина тримається, її зриває\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    /** One disguise at a time — the second cast is how you take it off. */
    override fun canCast(context: CastContext): Boolean = true

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.scheduling.stop(context.caster.uuid, identity)
            return AbilityOutcome.done("Ви скинули личину")
        }

        val level = context.level
        val anchor = context.caster.blockPosition()

        val candles = Altar.candles(level, anchor)
        if (candles < MIN_CANDLES) {
            return AbilityOutcome.failed("Замало свічок: $candles з $MIN_CANDLES — небо вас не почує")
        }

        val offering = Altar.offerings(level, anchor)
            .firstNotNullOfOrNull { drop -> drop.item.get(ModComponents.POTION_BREW)?.let { drop to it } }
            ?: return AbilityOutcome.failed("Покладіть на вівтар зілля того шляху, за кого себе видаєте")

        val (drop, brew) = offering
        val borrowed = brew.resolvePathway()
            ?: return AbilityOutcome.failed("Цього божества цей світ ще не знає")
        if (borrowed.name == context.beyonder.pathway.name) {
            return AbilityOutcome.failed("Своїм ви і так є — личина тут ні до чого")
        }

        drop.item.shrink(1)
        if (drop.item.isEmpty) drop.discard()

        wear(context, borrowed)
        return AbilityOutcome.done("Небо ${borrowed.name} прийняло вас за свого")
    }

    /**
     * The borrowed welcome, for as long as the session lives.
     *
     * Everything granted is off-pathway, so `finally` gives it all back on any exit — the term
     * running out, the second cast, leaving the world, the server stopping. Nothing here is ever
     * written down: a disguise that survives a restart is not a disguise, it is membership.
     */
    private fun wear(context: CastContext, borrowed: Pathway) {
        val granted = borrowed.abilitiesFor(NOVICE_SEQUENCE) + RitualMagic()
        val beyonder = context.beyonder
        val messaging = context.messaging
        val level = context.level
        val mote = ModParticles.mote(context.effects.pathwayColor)

        granted.forEach(beyonder::grantOffPathway)
        level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.CANDLE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f,
        )

        context.session {
            try {
                var elapsed = 0
                while (elapsed < HOLD_TICKS) {
                    val wearer: ServerPlayer = owner ?: break
                    level.sendParticles(
                        mote, wearer.x, wearer.y + 1.0, wearer.z, 3, 0.4, 0.6, 0.4, 0.0,
                    )
                    waitTicks(AURA_PERIOD_TICKS)
                    elapsed += AURA_PERIOD_TICKS
                }
            } finally {
                granted.forEach { beyonder.remove(it.identity) }
                messaging.tell(ownerId, Component.literal("Личина спала — небо впізнало чужого"))
            }
        }
    }

    private companion object {
        const val COOLDOWN_SECONDS = 600
        const val MIN_CANDLES = 4
        const val HOLD_TICKS = 10 * 60 * TICKS_PER_SECOND
        const val AURA_PERIOD_TICKS = 10

        /** What a borrowed god grants a stranger: the bottom of its ladder, and nothing more. */
        const val NOVICE_SEQUENCE = 9
    }
}
