package me.vangoo.mysteries.client.render

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * The client half of [me.vangoo.mysteries.effect.beam] and [me.vangoo.mysteries.effect.flash].
 *
 * A beam is sown here on the client's own clock: one packet says where and how long, and every tick
 * the line is filled with [ModParticles.SPIRIT_LIGHT] again. Each light lives longer than a tick,
 * so the line is always several generations deep — and because the light is additive, that depth
 * is what makes its middle white-hot. When the beam's term is over nothing is removed: the last
 * generations simply burn out, and the beam fades rather than switching off.
 *
 * A flash is weighed here too, because only this client knows where its camera looks: full in the
 * face, a quarter from the side or behind a wall, nothing beyond its radius.
 *
 * ponytail: a beam is made of particles, not of geometry — pixel-native and free of a render hook.
 * If a beam ever needs to be a solid shaft (seen end-on, or kilometres long), that is a
 * `LevelRenderEvents` pass with a vanilla `beaconBeam`/`lightning` render type.
 */
object WorldEffects : HudElement {

    /**
     * [light] is the glow; [body] is a sparse translucent mote of the same colour under it. Additive
     * light over a bright sky saturates to white and loses the pathway — the motes keep the hue
     * where the glow alone cannot.
     */
    private class Beam(
        val from: Vec3,
        val to: Vec3,
        val light: ColorParticleOption,
        val body: ColorParticleOption,
        val width: Float,
        var ticksLeft: Int,
    )

    private val beams = mutableListOf<Beam>()

    private var flash = 0f
    private var previousFlash = 0f
    private var flashColor = 0xFFFFFF

    fun register() {
        // Over the world, under the HUD: the screen whitens, the hearts stay readable.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, modId("flash"), this)
    }

    fun accept(payload: WorldEffectPayload) {
        when (payload) {
            is WorldEffectPayload.Beam -> {
                val color = RgbColor(payload.color and 0xFFFFFF)
                beams += Beam(payload.from, payload.to, ModParticles.light(color), ModParticles.mote(color),
                    payload.width, payload.ticks)
            }
            is WorldEffectPayload.Flash -> weigh(payload)
        }
    }

    fun tick() {
        previousFlash = flash
        flash = if (flash < FLASH_GONE) 0f else flash * FLASH_DECAY
        if (Minecraft.getInstance().isPaused) return
        beams.removeAll { beam ->
            sow(beam)
            --beam.ticksLeft <= 0
        }
    }

    fun clear() {
        beams.clear()
        flash = 0f
        previousFlash = 0f
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val strength = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), previousFlash, flash)
        if (strength <= 0f) return
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), UiTheme.argb(flashColor, strength * FLASH_PEAK))
    }

    private fun sow(beam: Beam) {
        val level = Minecraft.getInstance().level ?: return
        val random = level.random
        val span = beam.to.subtract(beam.from)
        val steps = ceil(span.length() / STEP).toInt().coerceAtLeast(1)
        val across = 1 + (beam.width * ACROSS_PER_BLOCK).toInt()
        val spread = beam.width / 2 + THREAD_SHIMMER
        for (i in 0..steps) {
            // Jittered along the line too: generations sown on the same points stack into beads.
            val along = ((i + random.nextDouble() - 0.5) / steps).coerceIn(0.0, 1.0)
            val at = beam.from.add(span.scale(along))
            repeat(across) {
                // Always visible: a pillar of light is seen from farther than a spark is.
                level.addAlwaysVisibleParticle(
                    beam.light,
                    at.x + (random.nextDouble() - 0.5) * 2 * spread,
                    at.y + (random.nextDouble() - 0.5) * 2 * THREAD_SHIMMER,
                    at.z + (random.nextDouble() - 0.5) * 2 * spread,
                    0.0, 0.0, 0.0,
                )
            }
            if (random.nextFloat() < BODY_CHANCE) {
                level.addAlwaysVisibleParticle(beam.body, at.x, at.y, at.z, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun weigh(payload: WorldEffectPayload.Flash) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val eye = player.getEyePosition(1f)
        val toward = payload.at.subtract(eye)
        val distance = toward.length()
        if (distance > payload.radius) return

        val facing = if (distance < 1e-3) 1.0 else player.getViewVector(1f).dot(toward.normalize()).coerceAtLeast(0.0)
        val hit = level.clip(ClipContext(eye, payload.at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
        val hidden = hit.type == HitResult.Type.BLOCK && hit.location.distanceTo(payload.at) > HIDDEN_MARGIN
        var strength = payload.strength * (AMBIENT + (1 - AMBIENT) * facing) * (1 - distance / payload.radius)
        if (hidden) strength *= AMBIENT
        // A flash is not motion, but it is the one effect hardest on sensitive eyes.
        if (ClientOptions.reducedMotion) strength *= 0.5
        if (strength.toFloat() <= flash) return
        flash = strength.toFloat()
        // Light washes the colour out: the flash is the pathway's tint over white, not a coloured screen.
        flashColor = whitened(payload.color)
    }

    private fun whitened(rgb: Int): Int {
        fun channel(shift: Int) = ((rgb shr shift and 0xFF) + (0xFF - (rgb shr shift and 0xFF)) * 3 / 5) shl shift
        return channel(16) or channel(8) or channel(0)
    }

    /**
     * Spacing of the lights along a beam. Each light outlives a tick by ten or so, so the line is
     * that many generations deep: sparser than it looks, or the overlap saturates to white even
     * where the pathway's colour should show.
     */
    private const val STEP = 0.5
    private const val ACROSS_PER_BLOCK = 2f
    /** The share of the line's points that also get a translucent mote of the beam's colour. */
    private const val BODY_CHANCE = 0.2f
    /** How much a thread wavers: a line held perfectly straight reads as geometry, not light. */
    private const val THREAD_SHIMMER = 0.03f

    private const val FLASH_PEAK = 0.85f
    private const val FLASH_DECAY = 0.62f
    private const val FLASH_GONE = 0.02f
    /** What reaches a player who looks away or stands behind a wall. */
    private const val AMBIENT = 0.25
    private const val HIDDEN_MARGIN = 1.0
}
