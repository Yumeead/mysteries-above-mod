package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Prohibition
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The client half of the Judge's Prohibition (`pathways/justiciar/abilities/PowerProhibition.kt`).
 *
 * The law, once spoken, spreads over the ground from the Judge as a belt of golden runes, motes rising off its front,
 * and stands at the edge of its round turning slowly, the sign of what it forbids hanging over the middle (the thing
 * itself in a golden ring, struck through). A breach flashes the same sign over the offender, and a ring of gold comes
 * down over them from head to foot: the law's weight. Nothing appears at once and nothing goes out at once.
 */
internal object Laws {

    private class Zone(val law: Prohibition) {
        var age = 0
    }

    private class Breach(val law: Prohibition) {
        var age = 0
    }

    private val zones = mutableListOf<Zone>()
    private val breaches = mutableListOf<Breach>()

    fun accept(payload: Prohibition) {
        if (payload.breach == WorldEffectPayload.NO_ANCHOR) zones += Zone(payload) else breaches += Breach(payload)
    }

    fun tick() {
        val level = Minecraft.getInstance().level
        zones.removeAll { zone ->
            zone.age++
            // Motes rising off the front of the law as it spreads.
            if (level != null && zone.age < Prohibition.SPREAD_TICKS) {
                val r = zone.law.radius * easeOut(zone.age.toFloat() / Prohibition.SPREAD_TICKS)
                val option = ModParticles.mote(RgbColor(zone.law.color and 0xFFFFFF))
                repeat(FRONT_MOTES) {
                    val a = level.random.nextDouble() * 2 * PI
                    level.addParticle(option, zone.law.at.x + cos(a) * r, zone.law.at.y + 0.2, zone.law.at.z + sin(a) * r, 0.0, 0.03, 0.0)
                }
            }
            zone.age >= zone.law.ticks
        }
        breaches.removeAll { ++it.age >= it.law.ticks }
    }

    fun clear() {
        zones.clear()
        breaches.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (zones.isEmpty() && breaches.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos

        // Everything is laid out here; the callbacks below only write it (they run later, when the frame is drawn).
        val belts = ArrayList<Textured>()
        val signs = ArrayList<Textured>()
        val veils = ArrayList<Veil>()
        val strokes = Strokes()
        for ((index, zone) in zones.withIndex()) {
            val age = zone.age + partial
            val law = zone.law
            // Laws stack (the novel's Paladin lays one on another): an older law's sign over the same ground keeps
            // its place, and a newer one hangs above it instead of through it.
            val stack = zones.subList(0, index).count { it.law.at.distanceToSqr(law.at) < STACK_REACH * STACK_REACH }
            val out = ((age - (law.ticks - FADE_TICKS)) / FADE_TICKS).coerceIn(0f, 1f)
            val life = 1 - out * out
            val spread = easeOut((age / Prohibition.SPREAD_TICKS).coerceAtMost(1f))
            val ground = law.at.add(0.0, LIFT, 0.0)
            // The spreading front, bright, thinning into the standing belt.
            if (spread < 1f) belt(belts, ground, (law.radius * spread).toDouble(), law.radius.toDouble(), age, argbOf(Strands.mix(RUNE, WHITE, 0.4f), 1f))
            val standing = BELT_ALPHA * life * (0.85f + 0.15f * sin(age * 0.1f)) * spread
            if (standing > 0.01f) belt(belts, ground, law.radius.toDouble(), law.radius.toDouble(), age, argbOf(RUNE, standing))
            // A veil of light standing up off the runes at the edge: the law's bound, seen from in and out alike. It
            // rides the front while the law spreads, and grows to its height as it settles.
            veil(veils, ground, (law.radius * spread).toDouble(), VEIL_HEIGHT * (0.5 + 0.5 * spread), age, VEIL_ALPHA * life * (0.6f + 0.4f * spread))
            // The sign over the middle: up out of nothing with a little overshoot, bobbing, gone with the law.
            val shown = easeOutBack(((age - SIGN_DELAY) / SIGN_TICKS).coerceIn(0f, 1f))
            if (shown > 0f && life > 0f) {
                val centre = law.at.add(0.0, SIGN_HEIGHT + stack * SIGN_SIZE * 1.1 + SIGN_BOB * sin(age * 0.08), 0.0)
                sign(signs, centre, camera, SIGN_SIZE * shown, law.sign.ordinal, argbOf(0xFFFFFF, life * (shown.coerceAtMost(1f))))
                // A thread of light from the sign down to the ground: the law hangs from it.
                strokes.hot(centre.add(0.0, -SIGN_SIZE * shown / 2, 0.0), ground, argbOf(RUNE, 0.25f * life * shown.coerceAtMost(1f)), PIXEL * 1.5)
            }
        }
        for (breach in breaches) {
            val body = level.getEntity(breach.law.breach) ?: continue
            val age = breach.age + partial
            val t = age / breach.law.ticks
            val pop = easeOutBack((age / BREACH_POP_TICKS).coerceAtMost(1f))
            val fade = 1 - easeIn(((t - 0.5f) * 2).coerceIn(0f, 1f))
            val feet = body.getPosition(partial)
            val head = feet.add(0.0, body.bbHeight + BREACH_ABOVE + age * BREACH_RISE, 0.0)
            sign(signs, head, camera, BREACH_SIZE * pop, breach.law.sign.ordinal, argbOf(0xFFFFFF, fade))
            // The weight: a ring of gold coming down over the body from head to foot, closing as it goes.
            val down = easeIn((age / WEIGHT_TICKS).coerceAtMost(1f))
            if (down < 1f) {
                val y = body.bbHeight * (1 - down)
                val r = body.bbWidth * (WEIGHT_WIDE - (WEIGHT_WIDE - WEIGHT_NARROW) * down)
                circle(strokes, feet.add(0.0, y.toDouble(), 0.0), r.toDouble(), argbOf(Strands.mix(RUNE, WHITE, 0.3f), (1 - down) * 0.9f))
            }
        }
        if (veils.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
                for (v in veils) v.write(pose, buffer, camera)
            }
        }
        if (belts.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(RUNES)) { pose, buffer ->
                for (q in belts) q.write(pose, buffer, camera)
            }
        }
        if (signs.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(SIGNS)) { pose, buffer ->
                for (q in signs) q.write(pose, buffer, camera)
            }
        }
        if (!strokes.isEmpty) strokes.submit(context)
    }

    /**
     * A belt of runes lying on the ground round [centre] at [radius], [BELT_WIDTH] across, the runes the size the
     * final [full] radius gives them (they spread out with the front, as a ripple does), turning slowly.
     */
    private fun belt(out: MutableList<Textured>, centre: Vec3, radius: Double, full: Double, age: Float, argb: Int) {
        if (radius < 0.3) return
        val tiles = (2 * PI * full * BELT_PIXELS_PER_BLOCK / RUNE_WIDTH).roundToInt().coerceAtLeast(1)
        val segments = tiles * SEGMENTS_PER_TILE
        val turn = age * BELT_SPIN
        val inner = radius - BELT_WIDTH / 2
        val outer = radius + BELT_WIDTH / 2
        for (k in 0 until segments) {
            val a0 = 2 * PI * k / segments + turn
            val a1 = 2 * PI * (k + 1) / segments + turn
            val u0 = (k % SEGMENTS_PER_TILE).toFloat() / SEGMENTS_PER_TILE
            val u1 = (k % SEGMENTS_PER_TILE + 1f) / SEGMENTS_PER_TILE
            fun at(a: Double, r: Double) = centre.add(cos(a) * r, 0.0, sin(a) * r)
            out += Textured(at(a0, inner.coerceAtLeast(0.0)), at(a1, inner.coerceAtLeast(0.0)), at(a1, outer), at(a0, outer), u0, u1, 1f, 0f, argb, UP)
        }
    }

    /**
     * The veil at the edge: a band of light round [centre] at [radius], fading out upwards over [height], brighter in
     * slow waves that run round it.
     */
    private fun veil(out: MutableList<Veil>, centre: Vec3, radius: Double, height: Double, age: Float, alpha: Float) {
        if (radius < 0.5 || alpha <= 0.01f) return
        val segments = (radius * VEIL_SEGMENTS_PER_BLOCK).roundToInt().coerceIn(24, 256)
        for (k in 0 until segments) {
            val a0 = 2 * PI * k / segments
            val a1 = 2 * PI * (k + 1) / segments
            fun wave(a: Double) = alpha * (0.65f + 0.35f * sin((a * VEIL_WAVES + age * VEIL_DRIFT).toFloat()))
            val p0 = centre.add(cos(a0) * radius, 0.0, sin(a0) * radius)
            val p1 = centre.add(cos(a1) * radius, 0.0, sin(a1) * radius)
            out += Veil(p0, p1, p1.add(0.0, height, 0.0), p0.add(0.0, height, 0.0), argbOf(RUNE, wave(a0)), argbOf(RUNE, wave(a1)))
        }
    }

    /** A band of plain light from [a]-[b] on the ground up to [c]-[d], [bottomA] and [bottomB] at the foot, nothing at the top. */
    private class Veil(val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3, val bottomA: Int, val bottomB: Int) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val colours = intArrayOf(bottomA, bottomB, bottomB and 0xFFFFFF, bottomA and 0xFFFFFF)
            for (order in arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))) {
                for (i in order) {
                    val p = corners[i]
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                        .setColor(colours[i]).setLight(FULL_BRIGHT)
                }
            }
        }
    }

    /** Sign [index] of the sheet, [size] across, at [centre], turned to the eye. */
    private fun sign(out: MutableList<Textured>, centre: Vec3, camera: Vec3, size: Double, index: Int, argb: Int) {
        if (size <= 0.01) return
        val toEye = camera.subtract(centre).normalize()
        val right = UP.cross(toEye).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }.scale(size / 2)
        val up = toEye.cross(right).normalize().scale(size / 2)
        val u0 = index / 4f
        val u1 = (index + 1) / 4f
        out += Textured(centre.subtract(right).subtract(up), centre.add(right).subtract(up), centre.add(right).add(up), centre.subtract(right).add(up), u0, u1, 1f, 0f, argb, toEye)
    }

    private fun circle(strokes: Strokes, centre: Vec3, radius: Double, argb: Int) {
        var last = centre.add(radius, 0.0, 0.0)
        for (i in 1..CIRCLE_STEPS) {
            val a = 2 * PI * i / CIRCLE_STEPS
            val next = centre.add(cos(a) * radius, 0.0, sin(a) * radius)
            strokes.hot(last, next, argb, PIXEL * 2)
            last = next
        }
    }

    /** A textured quad of one frame, both faces, lit by itself: [v0] at [a]/[b], [v1] at [c]/[d]. */
    private class Textured(
        val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3,
        val u0: Float, val u1: Float, val v0: Float, val v1: Float, val argb: Int, val normal: Vec3,
    ) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val us = floatArrayOf(u0, u1, u1, u0)
            val vs = floatArrayOf(v0, v0, v1, v1)
            for (order in arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))) {
                for (i in order) {
                    val p = corners[i]
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                        .setColor(argb).setUv(us[i], vs[i]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT).setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
                }
            }
        }
    }

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)
    private fun easeIn(t: Float): Float = t * t
    private fun easeOutBack(t: Float): Float {
        val x = t - 1
        return 1 + 2.7f * x * x * x + 1.7f * x * x
    }

    private val RUNES = modId("textures/effect/confinement_runes.png")
    private val SIGNS = modId("textures/effect/prohibition_signs.png")
    private val UP = Vec3(0.0, 1.0, 0.0)
    private const val WHITE = 0xFFFFFF
    private const val RUNE = 0xFFD04A
    private const val FULL_BRIGHT = 0xF000F0

    private const val LIFT = 0.06
    private const val BELT_WIDTH = 1.4
    private const val BELT_PIXELS_PER_BLOCK = 32 / BELT_WIDTH
    private const val RUNE_WIDTH = 256.0
    private const val SEGMENTS_PER_TILE = 8
    private const val BELT_SPIN = 0.0015
    private const val BELT_ALPHA = 0.8f
    private const val VEIL_HEIGHT = 3.0
    private const val VEIL_ALPHA = 0.32f
    private const val VEIL_SEGMENTS_PER_BLOCK = 2.0
    private const val VEIL_WAVES = 6.0
    private const val VEIL_DRIFT = 0.04
    private const val FADE_TICKS = 30f
    private const val FRONT_MOTES = 6

    private const val SIGN_DELAY = 8f
    private const val SIGN_TICKS = 16f
    private const val SIGN_HEIGHT = 6.0
    private const val SIGN_BOB = 0.15
    private const val SIGN_SIZE = 3.6
    private const val STACK_REACH = 3.0

    private const val BREACH_POP_TICKS = 5f
    private const val BREACH_SIZE = 1.0
    private const val BREACH_ABOVE = 0.55
    private const val BREACH_RISE = 0.01
    private const val WEIGHT_TICKS = 9f
    private const val WEIGHT_WIDE = 1.3
    private const val WEIGHT_NARROW = 0.7
    private const val CIRCLE_STEPS = 32
}
