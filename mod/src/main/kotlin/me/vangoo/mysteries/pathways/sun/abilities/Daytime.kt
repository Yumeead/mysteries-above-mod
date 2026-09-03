package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock

/**
 * Sun, Seq. 8 (Light Seeker): **Daylight**.
 *
 * The light around the Seeker is **real**, not painted on: a vanilla `minecraft:light` block of
 * level 15 travels with them and lights a cave just as a torch would. The shadows retreat, mobs
 * nearby stop spawning, and everyone sees it, not only the Seeker.
 *
 * ## Why not Night Vision
 *
 * The plugin described this power as "light naturally spreads further (≈10+ blocks)" and had to
 * fake it with a potion effect, because on Spigot placing blocks under a player's feet twenty
 * times a second would have meant twenty chunk updates a second across the whole area.
 *
 * The vanilla `LightBlock` exists for exactly this: it is **invisible**, has no collision and no
 * model, and moves with a single `setBlock`. The difference from night vision is mechanical
 * rather than cosmetic — night vision shows **you**, light changes the **world**: other players
 * see it, mob spawning stops, daylight sensors react.
 *
 * ## One block, and it is cleaned up
 *
 * The light travels with the player: a new block is placed only once the Seeker has left the old
 * one, and the previous one is removed on the same tick. Only **air** is overwritten — the light
 * does not eat someone else's block.
 *
 * ponytail: an abrupt server shutdown between ticks will leave exactly one invisible light block
 * in the world. Removing it is ordinary breaking by hand; guarding against it with a journal of
 * its own costs more than the problem.
 */
class Daytime : ActiveAbility(AbilityIdentity.of("sun_daytime")) {

    override val displayName: Component = Component.literal("Денне світло")

    /** The cast is free: the price is the upkeep, and putting the light out must cost nothing. */
    override val spiritualityCost: Int = 0

    /**
     * The upkeep grows with the Sequence — and it must, because **regeneration** grows with it
     * too: 2 points/s at Seq. 8 against 6 at Seq. 5 (and 9 at full mastery). The flat price taken
     * from the plugin turned out at Seq. 5 to be below the regeneration, i.e. the power became
     * free and the spirituality bar crept back up to the ceiling while it was being sustained.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Справжнє світло йде за вами: джерело світла рівня $LIGHT_LEVEL.\n" +
            "▪ Печера освітлюється по-справжньому — моби поруч не спавняться\n" +
            "▪ Бачать усі, не лише ви\n" +
            "▪ Витрата: ${periodicCost(sequence)} духовності/с; повторний каст вимикає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Світло згасло")
        }

        val messaging = context.messaging
        // The light block is state in the world, not in the ability: the ability instance is
        // shared by every player, so the position must live in a specific Seeker's session.
        var lit: BlockPos? = null

        context.session {
            try {
                val end = sustain(periodTicks = MOVE_PERIOD_TICKS, upkeep = periodicCost) { priest ->
                    val here = priest.blockPosition().above()
                    if (here != lit) {
                        lit?.let { douse(priest.level() as ServerLevel, it) }
                        lit = kindle(priest.level() as ServerLevel, here)
                    }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Світло згасло: духовність скінчилась"))
                }
            } finally {
                val level = owner?.level() as? ServerLevel
                if (level != null) lit?.let { douse(level, it) }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f,
        )
        return AbilityOutcome.done("Довкола вас настав день")
    }

    /** Places the light if there is air there. Returns the position, or `null` if the spot is taken. */
    private fun kindle(level: ServerLevel, pos: BlockPos): BlockPos? {
        if (!level.getBlockState(pos).isAir) return null
        val state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL)
        return if (level.setBlock(pos, state, UPDATE_FLAGS)) pos else null
    }

    /** Removes **our own** light. Someone else's block in that spot is left alone. */
    private fun douse(level: ServerLevel, pos: BlockPos) {
        if (level.getBlockState(pos).block === Blocks.LIGHT) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS)
        }
    }

    private companion object {
        const val BASE_PERIODIC_COST = 10
        const val COOLDOWN_SECONDS = 3
        const val LIGHT_LEVEL = 15
        /** Once every five ticks: on foot the block changes less often, and there are four times fewer chunk updates. */
        const val MOVE_PERIOD_TICKS = 5
        /** Tell the client, do not wake the neighbours: a light block affects nothing. */
        const val UPDATE_FLAGS = 2
    }
}
