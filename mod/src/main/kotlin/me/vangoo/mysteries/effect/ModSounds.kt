package me.vangoo.mysteries.effect

import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

/**
 * The mod's sounds. Declaring them here **is** registering them, as in `ModParticles` and
 * `ModChannels`.
 *
 * ## Why an event of our own rather than `SoundEvents.AMBIENT_CAVE`
 *
 * A vanilla event is not just a file but also a volume, a pitch, a set of variants and a
 * subtitle. The whisper is a cave ambience quieted fourfold and dropped by a third; call the
 * vanilla event directly and the player hears an ordinary cave while the subtitle says
 * "Моторошний шум". An event of our own gives the mod both the mix and its own subtitle, and
 * `sounds.json` leaves it configurable from data.
 *
 * ## Why `sounds.json` points at vanilla files
 *
 * The mod carries no `.ogg` of its own yet, and that is deliberate: the composition is already
 * ours (the set of variants, the volumes, the pitches), and replacing the source with a file of
 * our own is one line in `sounds.json` and zero lines of code. The same line already drawn by
 * the potion, where the mod takes a vanilla model and paints it in its own colour.
 */
object ModSounds {

    /** Initialisation order: the list must exist before the first event. */
    private val declared = mutableListOf<SoundEvent>()

    /** A whisper at low sanity: voices that are not there. */
    val WHISPER: SoundEvent = event("whisper")

    /** A heartbeat at the edge: you hear your own body because there is nothing else left to hear. */
    val HEARTBEAT: SoundEvent = event("heartbeat")

    /** Forces the class to initialise before the registries close. */
    fun register() {
        declared.forEach { Registry.register(BuiltInRegistries.SOUND_EVENT, it.location(), it) }
    }

    private fun event(path: String): SoundEvent =
        SoundEvent.createVariableRangeEvent(modId(path)).also { declared += it }
}
