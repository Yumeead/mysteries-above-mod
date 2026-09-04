package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID

/**
 * Fool, Seq. 9 (Seer): **Spirit Vision**.
 *
 * Every living thing nearby outlined for as long as the Seer keeps watching — the same
 * vanilla-glow tradeoff `SharpVision` and Sun's `EvilDetection` already made: vanilla `GLOWING`
 * has one colour for everyone, not a colour per health band and per viewer the way the plugin's
 * own scoreboard-team hack did it. What that colour coding was actually for — telling a Beyonder
 * apart from an ordinary target — moves to the action bar instead, and glowing already shows an
 * invisible entity's outline for free, so there is nothing extra to write for that part.
 */
class SeerSpiritVision :
    ToggleablePassiveAbility(AbilityIdentity.of("fool_spirit_vision")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Духовне бачення")

    private val lastAnnounced = HashMap<UUID, HashMap<UUID, Long>>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Бачите обриси всього живого в радіусі $RANGE бл., навіть невидимого.\n" +
            "▪ Потойбічних видно окремо в action bar\n" +
            "▪ Витрата $PERIODIC_COST духовності/с",
    )

    override fun onEnable(context: AbilityContext) {
        lastAnnounced[context.caster.uuid] = HashMap()
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
        context.messaging.actionBar(context.caster, Component.literal("✦ Духовне бачення активоване"))
    }

    override fun onDisable(context: AbilityContext) {
        lastAnnounced.remove(context.caster.uuid)
        chime(context, SoundEvents.BEACON_DEACTIVATE, 0.5f)
        context.messaging.actionBar(context.caster, Component.literal("✦ Духовне бачення згасло"))
    }

    override fun tick(context: AbilityContext) {
        val beyonder = context.beyonder
        if (!beyonder.payUpkeep(PERIODIC_COST)) {
            context.messaging.actionBar(context.caster, Component.literal("✦ Духовність вичерпана — бачення згасає"))
            return
        }

        val owner = context.caster.uuid
        val announced = lastAnnounced.getOrPut(owner) { HashMap() }
        val now = context.scheduling.tick

        for (target in context.targeting.livingNear(RANGE)) {
            target.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_REFRESH_TICKS, 0, true, false, true))

            val theirs = context.beyonders.of(target.uuid) ?: continue
            val last = announced[target.uuid]
            if (last != null && now - last < BEYONDER_ANNOUNCE_COOLDOWN_TICKS) continue
            announced[target.uuid] = now
            context.messaging.actionBar(
                context.caster,
                Component.literal("✦ ${target.name.string} — потойбічний (${theirs.pathway.name})"),
            )
        }

        if (now % (2 * TICKS_PER_SECOND) == 0L) {
            val at = context.caster.eyePosition
            context.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.2, at.z, 5, 0.3, 0.1, 0.3, 0.0)
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
    }
}
