package me.vangoo.mysteries.effect

import com.mojang.math.Transformation
import me.vangoo.mysteries.ability.context.Scheduling
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
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * Words that hang in the world at [at] for [durationTicks] and fade before they go — where a power
 * that **knows** something says it, instead of the chat.
 *
 * A vanilla text display, built from a tag the way `/summon` builds it (its setters are private),
 * exactly as `creature/SpeechBubbles` builds a creature's line. No background: the words belong to
 * the light they hang in, not to a sign. It is [Spirits.EPHEMERAL], so a line left half-said when
 * the chunk unloaded never comes back from disk.
 *
 * Returns the display, so whoever placed it can take it away early; `null` if it could not be placed.
 */
fun worldText(
    scheduling: Scheduling,
    level: ServerLevel,
    at: Vec3,
    text: Component,
    durationTicks: Int,
): Display.TextDisplay? {
    require(durationTicks > 0) { "Тривалість тексту мусить бути додатною: $durationTicks" }
    val tag = CompoundTag()
    tag.putString("id", "minecraft:text_display")
    tag.put(Display.TextDisplay.TAG_TEXT, ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow())
    tag.putString(Display.TAG_BILLBOARD, "center")
    tag.putInt("line_width", LINE_WIDTH)
    tag.putInt("background", 0)
    tag.putBoolean("shadow", true)
    tag.put(
        Display.TAG_TRANSFORMATION,
        Transformation.EXTENDED_CODEC.encodeStart(
            NbtOps.INSTANCE,
            Transformation(null, null, Vector3f(SCALE, SCALE, SCALE), null),
        ).getOrThrow(),
    )
    val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag)
    val display = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.TRIGGERED) {
        it.snapTo(at.x, at.y, at.z)
        it
    } as? Display.TextDisplay ?: return null
    level.addFreshEntity(display)
    display.addTag(Spirits.EPHEMERAL)

    // The fade steps down the text's own alpha; below a tenth the font would read it as opaque again.
    val fade = minOf(FADE_TICKS, durationTicks)
    var step = 0
    scheduling.every(FADE_STEP_TICKS) {
        val left = durationTicks - (++step) * FADE_STEP_TICKS
        if (left in 1..fade) {
            val alpha = MIN_ALPHA + (OPAQUE - MIN_ALPHA) * left / fade
            display.setTextOpacity(alpha.toByte())
        }
    }.also { handle -> scheduling.after(durationTicks) { handle.cancel(); display.discard() } }
    return display
}

private const val LINE_WIDTH = 180
private const val SCALE = 0.6f
private const val FADE_TICKS = 20
private const val FADE_STEP_TICKS = 2
private const val OPAQUE = 255
private const val MIN_ALPHA = 26
