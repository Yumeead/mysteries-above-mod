package me.vangoo.mysteries.creature

import com.mojang.math.Transformation
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.pathways.death.Spirits
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.storage.TagValueInput
import org.joml.Vector3f

/**
 * Words over a creature's head: a vanilla text display riding the speaker for a few seconds.
 *
 * Chat would have told the whole server what one baker said to one passer-by; an action bar would
 * have put it in the listener's face with no speaker. A line hanging over the person who said it
 * is how a street sounds — and riding them, it walks where they walk without a tick of upkeep.
 *
 * The display's setters are private, so it is built the way a `/summon` builds it: from a tag. It
 * is [Spirits.EPHEMERAL] — a line half-said when the chunk unloaded never comes back from disk.
 */
object SpeechBubbles {

    /** How far a line carries. Nobody this close — nobody hears, and nothing is spawned. */
    const val HEARING = 16.0

    fun say(speaker: Mob, line: String) {
        val level = speaker.level() as ServerLevel
        val tag = CompoundTag()
        tag.putString("id", "minecraft:text_display")
        tag.put(Display.TextDisplay.TAG_TEXT, ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, Component.literal(line)).getOrThrow())
        tag.putString(Display.TAG_BILLBOARD, "center")
        tag.putInt("line_width", LINE_WIDTH)
        tag.putInt("background", BACKGROUND)
        tag.put(
            Display.TAG_TRANSFORMATION,
            Transformation.EXTENDED_CODEC.encodeStart(
                NbtOps.INSTANCE,
                Transformation(Vector3f(0f, LIFT, 0f), null, Vector3f(SCALE, SCALE, SCALE), null),
            ).getOrThrow(),
        )
        val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)
        val bubble = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.TRIGGERED) {
            it.snapTo(speaker.x, speaker.eyeY, speaker.z)
            it
        } ?: return
        level.addFreshEntity(bubble)
        bubble.addTag(Spirits.EPHEMERAL)
        bubble.startRiding(speaker, true, false)
        AbilityServices.scheduler.after(LINGER_TICKS) { bubble.discard() }
    }

    private const val LINE_WIDTH = 160
    private const val BACKGROUND = 0x66000000
    private const val LIFT = 0.55f
    private const val SCALE = 0.7f
    private const val LINGER_TICKS = 70
}
