package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.block.Blocks
import kotlin.random.Random

/**
 * Error, Seq. 8 (Swindler): **Mental Disruption**.
 *
 * The target is handed someone else's, false reality. Zero damage: a player sees walls that are
 * not there and hears steps behind their back; a creature goes blind and loses its target. The
 * illusion is personal — the world around it does not change, and nobody else sees a thing.
 *
 * ## The session owns the lie, so no visual primitive was needed
 *
 * The plugin had to add a `playPhantomBlocks` effect that held its own `BukkitTask` and reverted
 * every faked block on a hard timer, because there was nothing else that could outlive the cast.
 * Here the session **is** that lifetime: the fake blocks are sent when it starts and the real
 * ones are sent back in `finally`, which runs on every exit — the term expiring, a recast, the
 * caster leaving, the server stopping. Nothing to schedule, nothing to cancel by hand.
 *
 * ## What is faked and what is not
 *
 * Only air two blocks or more away from the victim, and never a cell they stand in: a
 * suffocation illusion would be a softlock, not a fright. The blocks are a lie told to one
 * client, so the server's world never learns of them — and if the victim dies, their client
 * reloads its chunks and the lie dies with the body.
 */
class MentalDisruption : ActiveAbility(AbilityIdentity.of("error_mental_disruption")) {

    override val displayName: Component = Component.literal("Ментальний розлад")

    override val spiritualityCost: Int = 45

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви підміняєте чуття цілі.\n" +
            "▪ Дальність ${RANGE.toInt()} бл., триває ${durationTicks(sequence) / TICKS_PER_SECOND} с\n" +
            "▪ Гравець бачить стіни, яких немає, і чує кроки за спиною\n" +
            "▪ Створіння сліпне й губить ціль\n" +
            "▪ Потойбічний утрачає ${sanityLoss(sequence)} глузду\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    override fun contestedTarget(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть погляд на живу ціль")

        val duration = durationTicks(context.sequence)
        context.beyonders.of(target)?.increaseSanityLoss(sanityLoss(context.sequence))

        when (target) {
            is ServerPlayer -> deceive(context, target, duration)
            else -> {
                // A creature has no client to lie to — its false reality is blindness.
                target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, true, true))
                (target as? Mob)?.target = null
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7f, 0.6f,
        )
        return AbilityOutcome.done("Реальність ${target.name.string} підмінено")
    }

    /**
     * A personal false reality: phantom walls, whispers from random sides, smoke in the eyes.
     *
     * Someone who can **decrypt** shakes it off in a couple of seconds rather than standing in it
     * for the full term — the wiki gives a Dream Stealer's Decryption exactly that, and this is
     * the only place in the mod where an illusion actually exists to be seen through. Written
     * here rather than in `Decryption` because a victim's power cannot reach into the caster's
     * session; what it can do is shorten what lands on them.
     */
    private fun deceive(context: CastContext, victim: ServerPlayer, requested: Int) {
        val level = context.level
        val messaging = context.messaging
        val walls = phantomWalls(level, victim)
        val reader = context.beyonders.of(victim)?.abilities?.any { it.identity == DECRYPTION } == true
        val duration = if (reader) SEEN_THROUGH_TICKS else requested

        context.session {
            try {
                walls.forEach { pos ->
                    victim.connection.send(ClientboundBlockUpdatePacket(pos, Blocks.STONE.defaultBlockState()))
                }
                messaging.actionBar(victim, Component.literal("Ви більше не певні, що бачите…"))

                var elapsed = 0
                while (elapsed < duration && !victim.isRemoved) {
                    whisper(level, victim)
                    waitTicks(WHISPER_PERIOD_TICKS)
                    elapsed += WHISPER_PERIOD_TICKS
                }
            } finally {
                walls.forEach { pos -> victim.connection.send(ClientboundBlockUpdatePacket(level, pos)) }
            }
        }
    }

    /**
     * Where the walls that are not there will stand.
     *
     * Only air, and never within [SAFE_GAP] of the victim: a block faked onto their own cell
     * would read as suffocation rather than as a fright.
     */
    private fun phantomWalls(level: ServerLevel, victim: ServerPlayer): List<BlockPos> {
        val origin = victim.blockPosition()
        return generateSequence {
            origin.offset(
                Random.nextInt(-RADIUS, RADIUS + 1),
                Random.nextInt(-1, 3),
                Random.nextInt(-RADIUS, RADIUS + 1),
            )
        }
            .filter { it.distSqr(origin) > SAFE_GAP * SAFE_GAP && level.getBlockState(it).isAir }
            .distinct()
            .take(WALL_COUNT)
            .toList()
    }

    /** A step, a stare, a groan — from a random side, so that it frightens instead of droning. */
    private fun whisper(level: ServerLevel, victim: ServerPlayer) {
        val from = victim.position().add(
            Random.nextDouble(-4.0, 4.0),
            Random.nextDouble(-1.0, 2.0),
            Random.nextDouble(-4.0, 4.0),
        )
        val (sound, volume, pitch) = WHISPERS.random()
        victim.connection.send(
            ClientboundSoundPacket(
                Holder.direct(sound),
                SoundSource.HOSTILE,
                from.x, from.y, from.z,
                volume, pitch, Random.nextLong(),
            ),
        )
        level.sendParticles(
            victim, ParticleTypes.SMOKE, true, true,
            from.x, from.y, from.z, 8, 0.2, 0.2, 0.2, 0.0,
        )
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private fun sanityLoss(sequence: Sequence): Int =
        scale(BASE_SANITY_LOSS, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 60
        const val BASE_DURATION_TICKS = 8 * TICKS_PER_SECOND
        const val BASE_SANITY_LOSS = 3
        const val WHISPER_PERIOD_TICKS = 25
        const val WALL_COUNT = 14
        const val RADIUS = 5

        /** How far a lie must stay from the victim's own body. */
        const val SAFE_GAP = 2

        /** How long a lie survives someone who reads them for a living. */
        const val SEEN_THROUGH_TICKS = 2 * TICKS_PER_SECOND
        val DECRYPTION: AbilityIdentity = AbilityIdentity.of("error_decryption")

        val WHISPERS: List<Triple<SoundEvent, Float, Float>> = listOf(
            Triple(SoundEvents.ENDERMAN_STARE, 0.5f, 0.7f),
            Triple(SoundEvents.STONE_STEP, 0.6f, 0.8f),
            Triple(SoundEvents.ZOMBIE_AMBIENT, 0.4f, 0.5f),
        )
    }
}
