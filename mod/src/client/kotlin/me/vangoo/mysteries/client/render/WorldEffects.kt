package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.effect.BEAM_FADE_TICKS
import me.vangoo.mysteries.effect.BEAM_REACH_TICKS
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * The client half of [me.vangoo.mysteries.effect.beam] and [me.vangoo.mysteries.effect.flash]; the
 * Door's doors and passages arrive by the same packet and are handed to [Doors], the Fool's paper to
 * [Paper], and what a power shows its caster alone to [PrivateSight].
 *
 * A beam is a solid shaft of light ([LightShaft]), not a line of particles: one packet says where and
 * how long, and every frame the shaft is drawn where it stands. Nothing about it switches: it runs
 * out of its source over its first ticks — from the sky it falls, from the hand it flies — with its
 * front thinning ahead of it, swells and brightens as it lands, holds, and then thins and fades away.
 *
 * A flash is weighed here too, because only this client knows where its camera looks: full in the
 * face, a quarter from the side or behind a wall, nothing beyond its radius.
 */
object WorldEffects : HudElement {

    private class Beam(val from: Vec3, val to: Vec3, val rgb: Int, val width: Float, val sourceWidth: Float, val ticks: Int) {
        var age = 0
    }

    private val beams = mutableListOf<Beam>()

    private var flash = 0f
    private var previousFlash = 0f
    private var flashColor = 0xFFFFFF

    fun register() {
        // Over the world, under the HUD: the screen whitens, the hearts stay readable.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, modId("flash"), this)
        // The shafts go in with the rest of the frame's geometry, after the entities and particles —
        // and read the ground's depth, copied before anything else is drawn over it.
        LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register { if (beams.isNotEmpty() && !ShaderPacks.inUse) LightShaft.captureDepth() }
        LevelRenderEvents.COLLECT_SUBMITS.register(::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Doors::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Paper::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(PrivateSight::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Strands::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(SpiritThreads::submit)
        // Bent air reads the frame's colour once the solid entities are in it; it is drawn with the translucent ones.
        LevelRenderEvents.AFTER_SOLID_FEATURES.register { if ((AirShots.active || Verdicts.bending) && !ShaderPacks.inUse) Refraction.captureColor() }
        LevelRenderEvents.COLLECT_SUBMITS.register(AirShots::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Pendulums::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Edicts::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Lashes::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Laws::submit)
        LevelRenderEvents.COLLECT_SUBMITS.register(Marks::submit)
    }

    fun accept(payload: WorldEffectPayload) {
        when (payload) {
            is WorldEffectPayload.Beam ->
                beams += Beam(payload.from, payload.to, payload.color and 0xFFFFFF, payload.width, payload.sourceWidth, payload.ticks)
            is WorldEffectPayload.Flash -> weigh(payload)
            is WorldEffectPayload.Door -> Doors.accept(payload)
            is WorldEffectPayload.Passage -> Doors.accept(payload)
            is WorldEffectPayload.Sheet -> Paper.accept(payload)
            is WorldEffectPayload.Effigy -> Paper.accept(payload)
            is WorldEffectPayload.Words -> PrivateSight.accept(payload)
            is WorldEffectPayload.Glow -> PrivateSight.accept(payload)
            is WorldEffectPayload.Thread -> Strands.accept(payload)
            is WorldEffectPayload.Spool -> Strands.accept(payload)
            is WorldEffectPayload.Face -> Paper.accept(payload)
            is WorldEffectPayload.AirShot -> AirShots.accept(payload)
            is WorldEffectPayload.Pendulum -> Pendulums.accept(payload)
            is WorldEffectPayload.Edict -> Edicts.accept(payload)
            is WorldEffectPayload.Lash -> Lashes.accept(payload)
            is WorldEffectPayload.Prohibition -> Laws.accept(payload)
            is WorldEffectPayload.Vision -> me.vangoo.mysteries.client.hud.DreamVision.accept(payload)
        }
    }

    /**
     * For client game tests: every effect stands at its age while this is set. A screenshot takes several
     * ticks, so a test that wants each phase of a swing lets one tick through between shots.
     */
    @JvmStatic
    var held = false

    fun tick() {
        if (held) return
        previousFlash = flash
        flash = if (flash < FLASH_GONE) 0f else flash * FLASH_DECAY
        if (Minecraft.getInstance().isPaused) return
        beams.removeAll { beam -> ++beam.age >= beam.ticks + BEAM_FADE_TICKS }
        Doors.tick()
        Paper.tick()
        PrivateSight.tick()
        Strands.tick()
        AirShots.tick()
        Pendulums.tick()
        Edicts.tick()
        Lashes.tick()
        Laws.tick()
        Marks.tick()
        me.vangoo.mysteries.client.hud.DreamVision.tick()
    }

    fun clear() {
        beams.clear()
        Doors.clear()
        Paper.clear()
        PrivateSight.clear()
        Strands.clear()
        AirShots.clear()
        Pendulums.clear()
        Edicts.clear()
        Lashes.clear()
        Laws.clear()
        Marks.clear()
        me.vangoo.mysteries.client.hud.DreamVision.clear()
        flash = 0f
        previousFlash = 0f
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val strength = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), previousFlash, flash)
        if (strength <= 0f) return
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), UiTheme.argb(flashColor, strength * FLASH_PEAK))
    }

    /**
     * Every shaft standing this frame, as one batch of geometry. The batch is written later, when the
     * frame draws its translucent geometry — so it gets its own copy of the list and of the clock.
     */
    private fun submit(context: LevelRenderContext) {
        if (beams.isEmpty()) return
        // Under a shader pack the shaft's own shader is never run: the plain one is drawn instead.
        val plain = ShaderPacks.inUse
        if (!plain && !LightShaft.ready) return
        val camera = context.levelState().cameraRenderState.pos
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val standing = beams.toList()
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), if (plain) LightShaft.PLAIN else LightShaft.TYPE) { pose, buffer ->
            for (beam in standing) {
                val age = beam.age + partial
                // It runs out of its source first, easing in and out, its front thin while it travels.
                val reach = ease(age / BEAM_REACH_TICKS)
                val span = beam.to.subtract(beam.from)
                val to = beam.from.add(span.scale(reach.toDouble()))
                val front = span.length() * reach * (1 - reach) * FRONT_SHARE
                // It brightens and swells as it lands, and thins as it fades.
                val appear = ease(age / APPEAR_TICKS)
                val fading = ease((age - beam.ticks) / BEAM_FADE_TICKS)
                val swell = (THIN + (1 - THIN) * appear) * (1 - (1 - THIN) * fading)
                val source = haze(beam.sourceWidth) * swell
                // Where its front stands now, between its width at the source and where it lands.
                val radius = source + (haze(beam.width) * swell - source) * reach
                val intensity = appear * (1 - fading)
                if (plain) LightShaft.drawPlain(pose, buffer, camera, beam.from, to, source, radius, beam.rgb, intensity)
                else LightShaft.draw(pose, buffer, camera, beam.from, to, source, radius, beam.rgb, intensity, age, front)
            }
        }
    }

    /** The haze's radius for a beam [width] blocks across: the width is the core, the haze reaches past it. */
    private fun haze(width: Float): Double = maxOf(width, MIN_WIDTH) * HAZE_PER_WIDTH

    /** 0 → 1 over 0 → 1, starting and settling gently; clamped outside. */
    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
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

    /** How long a beam takes to come to full light: a little longer than to land, so it lands still growing. */
    private const val APPEAR_TICKS = 5f
    /** How thin a beam is at its first and last tick, as a share of its full width. */
    private const val THIN = 0.55
    /** How much of a travelling beam its front thins over, at the middle of its way. */
    private const val FRONT_SHARE = 1.2
    /** A "thread" of width 0 is still a ray of light, not nothing. */
    private const val MIN_WIDTH = 0.12f
    /** The shader's core is 0.16 of the haze, its glow reaching past it; a haze of 1.3 widths reads as the width. */
    private const val HAZE_PER_WIDTH = 1.3

    private const val FLASH_PEAK = 0.85f
    private const val FLASH_DECAY = 0.62f
    private const val FLASH_GONE = 0.02f
    /** What reaches a player who looks away or stands behind a wall. */
    private const val AMBIENT = 0.25
    private const val HIDDEN_MARGIN = 1.0
}
