package me.vangoo.mysteries.client.beyonder

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.mixin.client.GameRendererInvoker
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource

/**
 * What the player **hears and sees** when the mind starts to shake.
 *
 * ## Why this lives on the client
 *
 * The whisper and the screen chain are not mechanics but perception: they hit nobody and affect
 * nothing. The server has already sent the number (`AbilityStatePayload.sanityLoss`) and has
 * nothing more to do here — otherwise every heartbeat would cost a packet. The client ticks on
 * its own, with the same clock the cooldowns use: the tick, not wall time, so a paused
 * single-player game falls silent along with the world.
 *
 * ## Bands, not a smooth curve
 *
 * `PostChain` has no volume knob: the uniforms are baked into JSON, and there is nothing to
 * tune `Amount` with smoothly during play. Hence there are two chains — one per band — and that
 * is data, not code. The sound, on the other hand, is a curve: the interval between whispers
 * shortens together with sanity.
 */
object SanityAmbience {

    /** How many ticks until the next whisper. `0` — time to speak. */
    private var untilWhisper = 0

    /** How many ticks until the next heartbeat. */
    private var untilBeat = 0

    /** The chain **we** put up. Someone else's (a creeper, a spider) must not be touched. */
    private var applied: Identifier? = null

    private val random: RandomSource = RandomSource.create()

    fun tick() {
        val minecraft = Minecraft.getInstance()
        // No world means nothing to shake and nothing to draw on.
        val alive = minecraft.level != null && minecraft.player != null
        val scale = if (alive && ClientAbilityState.isBeyonder) ClientAbilityState.sanityLoss else SanityLoss.none()

        applyPostEffect(minecraft, chainFor(scale))
        if (minecraft.isPaused) return
        speak(minecraft, scale)
    }

    /** Leaving the world: the chain must go away together with the state that called it up. */
    fun clear() {
        applyPostEffect(Minecraft.getInstance(), null)
        untilWhisper = 0
        untilBeat = 0
    }

    // ── Screen ──────────────────────────────────────────────────────────────────

    private fun chainFor(scale: SanityLoss): Identifier? = when {
        // Death's grey sight outranks the shaking mind: it is chosen, the other merely suffered.
        GREY_SIGHT.any(ClientAbilityState::isSustaining) -> DEATH_SIGHT
        scale.isCritical() || scale.isExtreme() -> BROKEN
        scale.isSevere() -> STRAINED
        else -> null
    }

    private fun applyPostEffect(minecraft: Minecraft, wanted: Identifier?) {
        if (wanted == applied) return

        val renderer = minecraft.gameRenderer
        if (wanted == null) {
            // Only our own chain is cleared: vanilla puts up its own when the player looks
            // through a creeper's eyes, and removing it would be someone else's breakage with no
            // error in the log at all.
            if (renderer.currentPostEffect() == applied) renderer.clearPostEffect()
        } else {
            (renderer as GameRendererInvoker).`mysteries_above$setPostEffect`(wanted)
        }
        applied = wanted
    }

    // ── Sound ───────────────────────────────────────────────────────────────────

    private fun speak(minecraft: Minecraft, scale: SanityLoss) {
        val whisperPeriod = whisperPeriod(scale)
        if (whisperPeriod == null) {
            untilWhisper = 0
        } else if (--untilWhisper <= 0) {
            untilWhisper = whisperPeriod / 2 + random.nextInt(whisperPeriod)
            // A local ambience rather than a sound from a point: the voices are not there, so
            // they have no place either.
            minecraft.soundManager.play(
                SimpleSoundInstance.forLocalAmbience(
                    ModSounds.WHISPER,
                    MIN_PITCH + random.nextFloat() * PITCH_SPREAD,
                    WHISPER_VOLUME,
                ),
            )
        }

        // The heart is heard only at the very edge — and at a steady rhythm, unlike the whisper.
        if (!scale.isCritical() && !scale.isExtreme()) {
            untilBeat = 0
            return
        }
        if (--untilBeat <= 0) {
            untilBeat = BEAT_PERIOD_TICKS
            minecraft.soundManager.play(
                SimpleSoundInstance.forLocalAmbience(ModSounds.HEARTBEAT, 1f, HEARTBEAT_VOLUME),
            )
        }
    }

    /** The default interval between whispers; `null` — the mind still holds, and silence fits. */
    private fun whisperPeriod(scale: SanityLoss): Int? = when {
        scale.isCritical() || scale.isExtreme() -> 6 * TICKS_PER_SECOND
        scale.isSevere() -> 12 * TICKS_PER_SECOND
        scale.isSerious() -> 25 * TICKS_PER_SECOND
        else -> null
    }

    private val STRAINED: Identifier = modId("sanity_strained")
    private val BROKEN: Identifier = modId("sanity_broken")
    private val DEATH_SIGHT: Identifier = modId("death_sight")

    /** Death's powers that turn the world grey for as long as their session runs (`SpiritVision`, `EyeOfDeath`). */
    private val GREY_SIGHT = listOf("death_spirit_vision", "death_eye_of_death")

    private const val WHISPER_VOLUME = 0.6f
    private const val HEARTBEAT_VOLUME = 0.8f
    private const val MIN_PITCH = 0.85f
    private const val PITCH_SPREAD = 0.3f

    /** The rhythm at the edge: about 50 beats a minute — slower than a living one, and therefore alien. */
    private const val BEAT_PERIOD_TICKS = 24
}
