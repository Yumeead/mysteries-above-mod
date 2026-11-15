package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.effect.privateGlow
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID

/**
 * Fool, Seq. 9 (Seer): **Spirit Vision**.
 *
 * Every living thing nearby outlined for as long as the Seer keeps watching — and outlined for
 * **the Seer alone**. The first port lent the targets vanilla `GLOWING`, a flag every client sees, so
 * the Seer's sight lit up the crowd for the whole server; the outline is now the client's own
 * (`privateGlow`), drawn only on the Seer's screen. It still shows an invisible body's outline, the
 * same as glowing did.
 *
 * What the plugin's per-viewer colour coding was really for — telling a Beyonder apart from an
 * ordinary target — is the name of their pathway, hung over their head in its colour for the Seer
 * to read. A Clown's perfect mask ([ExpressionControl]) gives nothing away: there is no callout.
 */
class SeerSpiritVision :
    ToggleablePassiveAbility(AbilityIdentity.of("fool_spirit_vision")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Духовне бачення")

    private val lastAnnounced = HashMap<UUID, HashMap<UUID, Long>>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Бачите обриси всього живого в радіусі $RANGE бл., навіть невидимого.\n" +
            "▪ Над Потойбічним проступає назва його шляху; маска Клоуна її ховає\n" +
            "▪ Обриси бачите лише ви\n" +
            "▪ Витрата $PERIODIC_COST духовності/с",
    )

    override fun onEnable(context: AbilityContext) {
        lastAnnounced[context.caster.uuid] = HashMap()
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
    }

    override fun onDisable(context: AbilityContext) {
        lastAnnounced.remove(context.caster.uuid)
        chime(context, SoundEvents.BEACON_DEACTIVATE, 0.5f)
    }

    override fun tick(context: AbilityContext) {
        val beyonder = context.beyonder
        if (!beyonder.payUpkeep(PERIODIC_COST)) {
            context.messaging.hint(context.caster, Component.literal("Духовність вичерпана — бачення згасає"), Fizzle.EMPTY)
            return
        }

        val owner = context.caster.uuid
        val announced = lastAnnounced.getOrPut(owner) { HashMap() }
        val now = context.scheduling.tick

        val seen = context.targeting.livingNear(RANGE)
        privateGlow(context.caster, seen, GLOW_REFRESH_TICKS)

        for (target in seen) {
            val theirs = context.beyonders.of(target.uuid) ?: continue
            if (context.beyonders.isToggled(target.uuid, ExpressionControl.IDENTITY)) continue
            val last = announced[target.uuid]
            if (last != null && now - last < BEYONDER_ANNOUNCE_COOLDOWN_TICKS) continue
            announced[target.uuid] = now
            val name = theirs.pathway.name
            privateText(context.caster, target.position(), Component.literal(name), CALLOUT_TICKS, PathwayBranding.liquidOf(name), anchor = target)
        }
    }

    private fun chime(context: AbilityContext, sound: net.minecraft.sounds.SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    private companion object {
        const val PERIODIC_COST = 5
        const val RANGE = 15.0
        val GLOW_REFRESH_TICKS = 1 * TICKS_PER_SECOND + 10
        val BEYONDER_ANNOUNCE_COOLDOWN_TICKS = 5L * TICKS_PER_SECOND
        val CALLOUT_TICKS = 3 * TICKS_PER_SECOND
    }
}
