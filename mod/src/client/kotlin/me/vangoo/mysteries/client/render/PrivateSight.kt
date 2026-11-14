package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * What a power shows to this client alone (`effect/PrivateSight.kt` on the server): words in the
 * world, and outlines.
 *
 * Words are drawn here rather than as a vanilla text display because a text display is an entity,
 * and an entity is everyone's: an omen read over the Seer's hand would hang there for the whole
 * crowd. They face the camera, rise a hair as they live, and fade over their last second; words over
 * a body ride it, frame by frame.
 *
 * Outlines are answered to vanilla's own question — `Minecraft.shouldEntityAppearGlowing`, asked by
 * every outline the client draws (`PrivateGlowMixin`) — so they are the game's outline, nothing new.
 */
object PrivateSight {

    private class Words(val at: Vec3, val anchor: Int, val lines: List<FormattedCharSequence>, val rgb: Int, val ticks: Int) {
        var age = 0
    }

    private val words = mutableListOf<Words>()
    private val glowing = HashMap<Int, Int>()
    private var clock = 0

    fun accept(payload: WorldEffectPayload.Words) {
        val font = Minecraft.getInstance().font
        words += Words(payload.at, payload.anchor, font.split(payload.text, LINE_WIDTH), payload.color and 0xFFFFFF, payload.ticks)
    }

    fun accept(payload: WorldEffectPayload.Glow) {
        val until = clock + payload.ticks
        payload.entities.forEach { id -> glowing.merge(id, until, ::maxOf) }
    }

    /** Whether [entity] is outlined for this client by a power of its own. */
    @JvmStatic
    fun glows(entity: Entity): Boolean = glowing.isNotEmpty() && (glowing[entity.id] ?: return false) > clock

    fun tick() {
        clock++
        words.removeAll { ++it.age >= it.ticks }
        glowing.values.removeIf { it <= clock }
    }

    fun clear() {
        words.clear()
        glowing.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (words.isEmpty()) return
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val cameraState = context.levelState().cameraRenderState
        val camera = cameraState.pos
        val partial = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val font = client.font
        val poses = context.poseStack()
        val collector = context.submitNodeCollector()

        for (word in words.toList()) {
            val age = word.age + partial
            val over = if (word.anchor == WorldEffectPayload.NO_ANCHOR) null else level.getEntity(word.anchor)
            // Words over a body that has left this client's sight go with it.
            if (word.anchor != WorldEffectPayload.NO_ANCHOR && over == null) continue
            val base = over?.getPosition(partial)?.add(0.0, over.bbHeight + ABOVE_HEAD, 0.0) ?: word.at
            val left = word.ticks - age
            val alpha = (if (left < FADE_TICKS) left / FADE_TICKS else 1f) * (age / APPEAR_TICKS).coerceAtMost(1f)
            if (alpha <= MIN_ALPHA) continue
            val rise = RISE * age / word.ticks

            poses.pushPose()
            poses.translate(base.x - camera.x, base.y - camera.y + rise, base.z - camera.z)
            poses.mulPose(cameraState.orientation)
            poses.scale(SCALE, -SCALE, SCALE)
            val color = (alpha * 255).toInt() shl 24 or word.rgb
            word.lines.forEachIndexed { index, line ->
                val x = -font.width(line) / 2f
                val y = (index - word.lines.size + 1) * font.lineHeight.toFloat()
                collector.submitText(poses, x, y, line, true, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, color, 0, 0)
            }
            poses.popPose()
        }
    }

    private const val LINE_WIDTH = 160
    private const val SCALE = 0.02f
    private const val ABOVE_HEAD = 0.45
    private const val RISE = 0.15
    private const val FADE_TICKS = 20f
    private const val APPEAR_TICKS = 4f
    /** Below a tenth the font reads the alpha as opaque again (as `worldText` found). */
    private const val MIN_ALPHA = 0.11f
}
