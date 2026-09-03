package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Door, Seq. 7 (Astrologer): **Anti-Divination**.
 *
 * The pathway's only power that does nothing by itself. Its effect lives inside someone else's
 * cast: while the shield is on, [DivinationArts] against this player rolls a die instead of
 * simply working.
 *
 * ## Why the shield is a toggle rather than a permanent passive
 *
 * Divination can be felt. An Astrologer who keeps the shield on around the clock is noticeable
 * for exactly that — people trip over it — so the choice between "I am hiding now" and "I can be
 * found now" is the game. A permanent shield would take that choice away and give nothing back.
 *
 * ## Why there is no tick here
 *
 * The plugin's version ticked and sowed two particles once every two seconds — that is, it paid
 * a tick so the player would remember the shield was on. The HUD already says that: a toggle is
 * visible in the ability list. The sparks stayed where they mean something — at the moment of
 * switching on and off.
 *
 * ## Why the identity is in the `companion`
 *
 * `DivinationArts` has to ask about **another** player's shield, i.e. to name it. The plugin had
 * no such constant, and the role was played by four guessed strings in a row (`"Anti Divination"`,
 * `"Anti-Divination"`, `"anti_divination"`, `"AntiDivination"`), three of which were always wrong.
 */
class AntiDivination : ToggleablePassiveAbility(IDENTITY) {

    override val displayName: Component = Component.literal("Протидія ворожінню")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Чуже ворожіння вас не бачить.\n" +
            "▪ Поки увімкнено, спроба поворожити на вас має шанс провалитись\n" +
            "▪ Шанс тим більший, чим вища ваша Послідовність проти ворожбита\n" +
            "▪ Працює з Посл. $UNLOCK_SEQUENCE і вище",
    )

    override fun onEnable(context: AbilityContext) {
        context.messaging.actionBar(context.caster, Component.literal("Ви захищені від чужого ворожіння"))
        veil(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
    }

    override fun onDisable(context: AbilityContext) {
        context.messaging.actionBar(context.caster, Component.literal("Ви знову вразливі до ворожіння"))
        veil(context, SoundEvents.BEACON_DEACTIVATE, 0.8f)
    }

    private fun veil(context: AbilityContext, sound: net.minecraft.sounds.SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            at.x, at.y + 1.2, at.z,
            30, 0.4, 0.6, 0.4, 0.0,
        )
        context.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.0, at.z, 20, 0.3, 0.5, 0.3, 0.1)
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("door_anti_divination")

        /**
         * Below this Sequence the shield does not hold. The threshold is the plugin's and is not
         * cosmetic: otherwise a Seq. 9 Apprentice with someone else's shield would be unfindable
         * for a Seq. 7 Astrologer.
         */
        const val UNLOCK_SEQUENCE = 7
    }
}
