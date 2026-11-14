package me.vangoo.mysteries.client.hud

import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.DangerPayload
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Seer's Danger Intuition by the crosshair: a pixel chevron on a ring round it for each threat,
 * on the side the threat is — ahead at the top, behind at the bottom — and turned towards it every
 * frame as the head turns. A threat above or below tilts its chevron up or down.
 *
 * Pale violet while it is a presence ("mind that"), red and pulsing under a second from a timed blow
 * (an arrow in flight, a fuse). Four at most, nearest first; each fades once its time is up. The
 * server only says who and when ([DangerPayload]); the turning is this client's, by its own camera.
 * Off with the "Danger arrows" switch in the HUD settings.
 */
object DangerArrows : HudElement {

    private class Threat(var at: Vec3, var urgent: Boolean, var blow: Int, var until: Int, val since: Int)

    /** By the threat's entity id: a threat sighted again is renewed, not doubled. */
    private val threats = HashMap<Int, Threat>()
    private var clock = 0

    fun register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, modId("danger"), this)
    }

    fun accept(payload: DangerPayload) {
        val blow = clock + payload.ticks
        val until = blow + if (payload.urgent) AFTER_BLOW_TICKS else 0
        val known = threats[payload.entity]
        if (known == null) {
            threats[payload.entity] = Threat(payload.at, payload.urgent, blow, until, clock)
        } else {
            known.at = payload.at
            known.urgent = payload.urgent
            known.blow = blow
            known.until = maxOf(known.until, until)
        }
    }

    fun tick() {
        clock++
        threats.values.removeIf { clock > it.until + FADE_TICKS }
    }

    fun clear() = threats.clear()

    /** How many threats are felt right now — for the client tests. */
    val count: Int get() = threats.size

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!ClientOptions.dangerArrows || threats.isEmpty()) return
        val client = Minecraft.getInstance()
        if (client.gui.hud.isHidden()) return
        val player = client.player ?: return
        val level = client.level ?: return
        val partial = delta.getGameTimeDeltaPartialTick(false)
        val now = clock + partial
        val eye = player.getEyePosition(partial)
        val yaw = Math.toRadians(player.getViewYRot(partial).toDouble())
        val pitch = Math.toRadians(player.getViewXRot(partial).toDouble())
        // Vanilla's look: yaw 0 faces +z, and the screen's right is then -x.
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val right = Vec3(-cos(yaw), 0.0, -sin(yaw))
        val cx = graphics.guiWidth() / 2
        val cy = graphics.guiHeight() / 2

        val shown = threats.entries
            .map { (id, threat) -> threat to (level.getEntity(id)?.let { it.getPosition(partial).add(0.0, it.bbHeight / 2.0, 0.0) } ?: threat.at) }
            .sortedBy { (_, at) -> at.distanceToSqr(eye) }
            .take(MAX_SHOWN)

        for ((threat, at) in shown) {
            val alpha = fade(threat, now)
            if (alpha <= 0.02f) continue
            val to = at.subtract(eye)
            val ahead = to.dot(forward)
            val across = to.dot(right)
            val around = atan2(across, ahead)
            val elevation = atan2(to.y, sqrt(ahead * ahead + across * across)) + pitch
            // Out from the crosshair towards it, tilted up or down by how far above or below the view it is.
            val ox = sin(around)
            val oy = -cos(around) - sin(elevation.coerceIn(-MAX_TILT, MAX_TILT))
            val x = cx + (sin(around) * RING).roundToInt()
            val y = cy - (cos(around) * RING).roundToInt()
            val pressing = threat.urgent && threat.blow - now < RED_TICKS
            val pulse = if (pressing && !ClientOptions.reducedMotion) 0.55f + 0.45f * abs(sin(now * PULSE_RATE)) else 1f
            chevron(graphics, x, y, octant(ox, oy), UiTheme.argb(if (pressing) RED else VIOLET, alpha * pulse), UiTheme.argb(SHADOW, alpha * 0.6f))
        }
    }

    /** In over a few ticks, out over a few once its time is up. */
    private fun fade(threat: Threat, now: Float): Float {
        val appear = ((now - threat.since) / APPEAR_TICKS).coerceIn(0f, 1f)
        val gone = 1f - ((now - threat.until) / FADE_TICKS).coerceIn(0f, 1f)
        return if (ClientOptions.reducedMotion) (if (now > threat.until + FADE_TICKS) 0f else 1f) else appear * gone
    }

    /** The nearest of the eight pixel directions to ([x], [y]), as an index into [DIRECTIONS]. */
    private fun octant(x: Double, y: Double): Int =
        Math.floorMod((atan2(x, -y) / (Math.PI / 4)).roundToInt(), DIRECTIONS.size)

    /**
     * A chevron pointing along [DIRECTIONS] at [dir], its tip at ([x], [y]): two arms of [ARM] pixels
     * back along the neighbouring directions — a caret on the axes, a corner on the diagonals — over a
     * shadow one pixel down and right.
     */
    private fun chevron(graphics: GuiGraphicsExtractor, x: Int, y: Int, dir: Int, color: Int, shadow: Int) {
        val left = DIRECTIONS[Math.floorMod(dir - 1, DIRECTIONS.size)]
        val rightArm = DIRECTIONS[Math.floorMod(dir + 1, DIRECTIONS.size)]
        for ((ink, shift) in listOf(shadow to 1, color to 0)) {
            graphics.fill(x + shift, y + shift, x + shift + 1, y + shift + 1, ink)
            for (k in 1..ARM) {
                graphics.fill(x + shift - k * left.first, y + shift - k * left.second, x + shift - k * left.first + 1, y + shift - k * left.second + 1, ink)
                graphics.fill(x + shift - k * rightArm.first, y + shift - k * rightArm.second, x + shift - k * rightArm.first + 1, y + shift - k * rightArm.second + 1, ink)
            }
        }
    }

    /** Clockwise from straight up, in screen pixels (y down). */
    private val DIRECTIONS = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)

    private const val VIOLET = 0xC8B4F0
    private const val RED = 0xE04848
    private const val SHADOW = 0x000000

    /** The ring round the crosshair, in GUI pixels, and the chevron's arm. */
    private const val RING = 15
    private const val ARM = 3
    private const val MAX_SHOWN = 4
    /** How far a threat above or below tilts its chevron, at most (radians). */
    private const val MAX_TILT = 0.8

    /** Under this many ticks to the blow the chevron turns red. */
    private const val RED_TICKS = 20f
    private const val PULSE_RATE = 0.9f
    private const val APPEAR_TICKS = 2f
    private const val FADE_TICKS = 6f
    /** A timed blow is kept a little past its moment: the arrow lands, then the chevron goes. */
    private const val AFTER_BLOW_TICKS = 3
}
