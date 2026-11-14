package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.burst
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.AABB

/**
 * Sun, Seq. 9 (Bard): **Singing**.
 *
 * The Bard sings — and while they sing, light gathers around those who listen. The song lasts
 * [SONG_TICKS] ticks and has an end: the boost goes to those who **stood to the last note**, not
 * to those who happened to run past at the start.
 *
 * ## Why a song rather than an instant buff
 *
 * The plugin's version was a burst: cast, and everyone within eight blocks got the effect. That
 * works, but "Singing" was only a name there: nothing sang, and there was nothing to listen to.
 *
 * Here the song is a four-second session with a rising row of notes (`NOTE_BLOCK_CHIME`,
 * semitones [SCALE]), and that is exactly why it has a **condition**: stand nearby until it is
 * finished. The Bard pays not only with spirituality but with standing still for four seconds and
 * loudly marking themselves.
 *
 * ## Whoever hears is whoever gets it
 *
 * The listeners are counted **at the moment of the last note**, not at the start: the session
 * does not remember them at all. A list gathered at the start would have to be kept in an ability
 * field — and there is one ability instance for the whole server, so two bards singing at once
 * would write into the same list.
 */
class Singing : ActiveAbility(AbilityIdentity.of("sun_singing")) {

    override val displayName: Component = Component.literal("Спів")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви співаєте ${SONG_TICKS / TICKS_PER_SECOND} с, і світло збирається навколо тих, хто слухає.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Підсилення на ${boonSeconds(sequence)} с: Регенерація, Опір, Сила\n" +
            "▪ Отримає лише той, хто дослухав до кінця — вас зокрема",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // A second song on top of the first is no longer singing.
        if (context.sessionRunning) return AbilityOutcome.failed("Ви вже співаєте")

        val pending = context.cast
        val radius = range(context.sequence)
        val boonTicks = boonSeconds(context.sequence) * TICKS_PER_SECOND
        val color = context.effects.pathwayColor
        val light = ModParticles.light(color)

        context.session {
            repeat(SCALE.size) { note ->
                val bard = owner ?: return@session
                sing(bard, light, radius, note)
                waitTicks(SONG_TICKS / SCALE.size)
            }

            val bard = owner ?: return@session
            val listeners = audience(bard, radius)
            listeners.forEach { uplift(it, boonTicks, color) }
            pending.complete()
            chord(bard)
        }

        return AbilityOutcome.Awaiting()
    }

    /**
     * One note: a sound, and the light of the song creeping out towards everyone who hears it.
     *
     * Each note carries the stream one step further — from the bard to [note]/[SCALE] of the way —
     * so the light reaches a listener on the last note, the same moment the boon does. Whoever
     * walks off is followed: the line is drawn to where they stand **now**.
     */
    private fun sing(bard: ServerPlayer, light: ParticleOptions, radius: Double, note: Int) {
        val level = bard.level()
        level.playSound(
            null, bard.x, bard.y, bard.z,
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
            1.2f, pitchOf(SCALE[note]),
        )
        val from = bard.position().add(0.0, bard.bbHeight * CHEST, 0.0)
        val reach = (note + 1).toDouble() / SCALE.size
        audience(bard, radius).filter { it !== bard }.forEach { listener ->
            val to = listener.position().add(0.0, listener.bbHeight * CHEST, 0.0)
            val way = to.subtract(from)
            val drift = way.normalize().scale(DRIFT)
            repeat(MOTES_PER_NOTE) {
                // Over the stretch this note adds, and a little behind it: the stream is thickest at its head.
                val along = reach - level.random.nextDouble() * STRETCH / SCALE.size
                val at = from.add(way.scale(along.coerceIn(0.0, 1.0)))
                level.sendParticles(light, at.x, at.y, at.z, 0, drift.x, drift.y, drift.z, 1.0)
            }
        }
    }

    /** The final chord — it carries further than the song itself. */
    private fun chord(bard: ServerPlayer) {
        bard.level().playSound(
            null, bard.x, bard.y, bard.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f,
        )
    }

    /** The listeners are players within the radius, the bard included. */
    private fun audience(bard: ServerPlayer, radius: Double): List<ServerPlayer> {
        return bard.level().getEntitiesOfClass(
            ServerPlayer::class.java,
            AABB.ofSize(bard.position(), radius * 2, radius * 2, radius * 2),
        ).filter { it.position().distanceToSqr(bard.position()) <= radius * radius }
    }

    /** Whoever stood to the last note flares up: the song has reached them. */
    private fun uplift(listener: ServerPlayer, ticks: Int, color: RgbColor) {
        listener.addEffect(MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true))
        listener.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ticks, 0, false, true, true))
        listener.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, 0, false, true, true))
        val centre = listener.position().add(0.0, listener.bbHeight * CHEST, 0.0)
        burst(listener.level(), centre, BLOOM_RADIUS, color, BLOOM_POINTS, BLOOM_SPEED)
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun boonSeconds(sequence: Sequence): Int =
        scale(BASE_BOON_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RANGE = 8
        const val BASE_BOON_SECONDS = 10
        const val COOLDOWN_SECONDS = 20

        const val SONG_TICKS = 4 * TICKS_PER_SECOND
        /** A rising row in semitones from the root — a vanilla note block takes 0..24. */
        val SCALE = intArrayOf(0, 4, 7, 11, 12, 16)

        const val CHEST = 0.6
        const val MOTES_PER_NOTE = 5
        /** How far behind its head the stream of one note reaches, in notes. */
        const val STRETCH = 1.5
        /** The stream creeps, it does not fly: a light drifts half a block over its life. */
        const val DRIFT = 0.06
        const val BLOOM_RADIUS = 0.45
        const val BLOOM_POINTS = 24
        const val BLOOM_SPEED = 0.08
    }
}

/** The vanilla note block formula: pitch = 2^((note − 12) / 12). */
private fun pitchOf(semitone: Int): Float =
    Math.pow(2.0, (semitone - 12) / 12.0).toFloat()
