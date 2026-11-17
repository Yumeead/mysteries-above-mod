package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Whip of Pain as a whip: a chain of textured segments (grip, barbed and plain links, a tip) laid along a
 * curve that is animated over the swing, the way Terraria draws its whips.
 *
 * The swing is a wave down the whip. The grip turns from over the shoulder to the target first and every
 * link follows a little later than the one before, so the whip unrolls rather than rotating as a stick; the
 * tip overshoots a little and is straight on the target exactly at [WorldEffectPayload.Lash.WHIP_CRACK_TICKS],
 * where the server lands the blow. Then it goes slack, droops and is drawn back into the hand, fading.
 *
 * The links carry the pathway's current: a few of them crackle with a jagged pixel of light each tick, all of
 * them at the crack, when the tip flashes and throws sparks.
 */
internal object Whips {

    private class Whip(val from: Int, val to: Int, val rgb: Int) {
        var age = 0
    }

    private val whips = mutableListOf<Whip>()

    fun accept(payload: WorldEffectPayload.Lash) {
        whips += Whip(payload.from, payload.to, payload.color and 0xFFFFFF)
    }

    fun tick() {
        val level = Minecraft.getInstance().level
        whips.removeAll { whip ->
            if (++whip.age == CRACK && level != null) level.getEntity(whip.to)?.let { sparks(level, Strands.body(it, 1f)) }
            whip.age >= LIFE
        }
    }

    fun clear() = whips.clear()

    fun submit(context: LevelRenderContext, strokes: Strokes) {
        if (whips.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val drawn = whips.mapNotNull { whip ->
            val from = level.getEntity(whip.from) ?: return@mapNotNull null
            val to = level.getEntity(whip.to) ?: return@mapNotNull null
            val age = whip.age + partial
            val points = shape(grip(from, partial), Strands.body(to, partial), age)
            val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(points[points.size / 2]))
            current(strokes, whip, points, age, camera)
            Drawn(points, alpha(age), light)
        }
        if (drawn.isEmpty()) return
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE)) { pose, buffer ->
            for (whip in drawn) segments(pose, buffer, camera, whip)
        }
    }

    /**
     * The whip's joints at [age], from the grip at [hand] to the tip. Straight on [target] at the crack.
     */
    internal fun shape(hand: Vec3, target: Vec3, age: Float): List<Vec3> {
        val reach = target.subtract(hand)
        val length = reach.length().coerceAtLeast(0.5)
        val forward = reach.scale(1 / length)
        val right = forward.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up = right.cross(forward)
        // The swing comes over the right shoulder, not straight over the head: the arc reads from any side.
        val bend = up.scale(cos(SHOULDER)).add(right.scale(sin(SHOULDER)))
        val count = (length / SEGMENT).roundToInt().coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)

        val points = ArrayList<Vec3>(count + 1)
        points += hand
        var at = hand
        if (age <= CRACK) {
            val p = (age / CRACK).coerceIn(0f, 1f)
            val step = length / count * (COILED + (1 - COILED) * easeOut(p))
            for (i in 0 until count) {
                val k = (i + 0.5f) / count
                // Each link turns a little later than the one before it: the wave that makes a whip a whip.
                val q = (p * (1 + LAG) - k * LAG).coerceIn(0f, 1f)
                val angle = WIND_UP * (1 - easeOutBack(q))
                at = at.add(forward.scale(cos(angle)).add(bend.scale(sin(angle))).scale(step))
                points += at
            }
        } else {
            val r = ((age - CRACK) / (LIFE - CRACK)).coerceIn(0f, 1f)
            // Slack: the whole whip shortens back into the hand and the far end droops.
            val step = length / count * (1 - RECOIL * easeIn(r))
            for (i in 0 until count) {
                val k = (i + 0.5) / count
                val angle = DROOP * k * easeOut(r)
                at = at.add(forward.scale(cos(angle)).add(DOWN.scale(sin(angle))).scale(step))
                points += at
            }
        }
        return points
    }

    private class Drawn(val points: List<Vec3>, val alpha: Float, val light: Int)

    private fun segments(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, whip: Drawn) {
        val points = whip.points
        val last = points.size - 2
        for (i in 0..last) {
            val cell = when (i) {
                0 -> 0
                last -> 3
                else -> if (i % 2 == 1) 1 else 2
            }
            ribbon(pose, buffer, camera, points[i], points[i + 1], cell, whip.alpha, whip.light)
        }
    }

    /** One segment: a quad from [a] to [b] turned to the eye about its own axis, textured with cell [cell]. */
    private fun ribbon(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, a: Vec3, b: Vec3, cell: Int, alpha: Float, light: Int) {
        val axis = b.subtract(a)
        val mid = a.add(b).scale(0.5)
        val toEye = camera.subtract(mid)
        val side = axis.cross(toEye).let { if (it.lengthSqr() < 1e-10) return else it.normalize().scale(WIDTH / 2) }
        val normal = toEye.normalize()
        val u0 = cell / 4f
        val u1 = (cell + 1) / 4f
        val argb = argbOf(0xFFFFFF, alpha * nearFade(mid, camera))
        corner(pose, buffer, camera, a.add(side), u0, 0f, argb, light, normal)
        corner(pose, buffer, camera, a.subtract(side), u0, 1f, argb, light, normal)
        corner(pose, buffer, camera, b.subtract(side), u1, 1f, argb, light, normal)
        corner(pose, buffer, camera, b.add(side), u1, 0f, argb, light, normal)
    }

    private fun corner(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, at: Vec3, u: Float, v: Float, argb: Int, light: Int, normal: Vec3) {
        buffer.addVertex(pose, (at.x - camera.x).toFloat(), (at.y - camera.y).toFloat(), (at.z - camera.z).toFloat())
            .setColor(argb)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /**
     * The current along the links: a jagged pixel of light over some of them, re-chosen every tick so it
     * flickers; over all of them at the crack, with a star at the tip.
     */
    private fun current(strokes: Strokes, whip: Whip, points: List<Vec3>, age: Float, camera: Vec3) {
        val tick = age.toInt()
        val fade = alpha(age)
        val cracking = tick in CRACK until CRACK + FLASH_TICKS
        val rgb = Strands.mix(whip.rgb, WHITE, 0.55f)
        val random = java.util.Random(whip.from * 7919L + whip.to * 31L + tick)
        for (i in 0 until points.size - 1) {
            if (!cracking && random.nextFloat() > CHARGED_SHARE) continue
            val a = points[i]
            val b = points[i + 1]
            // A line of light keeps its width in blocks, so by the eye it is a white bar: the current stays further off.
            val near = nearFade(a.lerp(b, 0.5), camera, CURRENT_NEAR_GONE, CURRENT_NEAR_SPAN)
            if (near <= 0f) continue
            val kink = a.lerp(b, 0.5).add(
                (random.nextDouble() - 0.5) * JITTER, (random.nextDouble() - 0.5) * JITTER, (random.nextDouble() - 0.5) * JITTER,
            )
            strokes.hot(a, kink, argbOf(rgb, fade * near), PIXEL * 1.5)
            strokes.hot(kink, b, argbOf(rgb, fade * near), PIXEL * 1.5)
        }
        if (cracking) {
            val tip = points.last()
            val size = STAR * (1 - (age - CRACK) / FLASH_TICKS)
            for (ray in STAR_RAYS) strokes.hot(tip.subtract(ray.scale(size)), tip.add(ray.scale(size)), argbOf(WHITE, 1f), PIXEL * 2)
        }
    }

    private fun sparks(level: net.minecraft.client.multiplayer.ClientLevel, at: Vec3) {
        val random = level.random
        repeat(SPARKS) {
            level.addParticle(
                ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.6,
            )
        }
        repeat(SPARKS / 2) {
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, (random.nextDouble() - 0.5), random.nextDouble() * 0.5, (random.nextDouble() - 0.5))
        }
    }

    /**
     * Where the whip leaves the hand. In one's own first person that is the hand on screen: the offsets vanilla
     * puts a held item at (`ItemInHandRenderer.applyItemArmTransform`), along the camera's own axes, so the grip
     * stays in the fist whether one looks up, down or ahead.
     */
    internal fun grip(entity: Entity, partial: Float): Vec3 {
        if (!firstPerson(entity)) return Strands.hand(entity, partial)
        val look = entity.getViewVector(partial)
        val right = look.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up = right.cross(look)
        return entity.getEyePosition(partial).add(look.scale(HAND_AHEAD)).add(right.scale(HAND_RIGHT)).add(up.scale(HAND_DOWN))
    }

    internal fun firstPerson(entity: Entity): Boolean {
        val minecraft = Minecraft.getInstance()
        return entity === minecraft.cameraEntity && minecraft.options.cameraType.isFirstPerson
    }

    /** Whatever passes right by the eye (the swing goes over one's own shoulder) melts away instead of filling the view. */
    private fun nearFade(at: Vec3, camera: Vec3, gone: Double = NEAR_GONE, span: Double = NEAR_SPAN): Float =
        ((at.distanceTo(camera) - gone) / span).toFloat().coerceIn(0f, 1f)

    private fun alpha(age: Float): Float = ((LIFE - age) / FADE_TICKS).coerceIn(0f, 1f)

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)
    private fun easeIn(t: Float): Float = t * t

    /** Past 1 and back: the tip overshoots the line before it settles on it. */
    private fun easeOutBack(t: Float): Float {
        val x = t - 1
        return 1 + (OVERSHOOT + 1) * x * x * x + OVERSHOOT * x * x
    }

    private val TEXTURE = modId("textures/effect/whip.png")
    private val UP = Vec3(0.0, 1.0, 0.0)
    private val DOWN = Vec3(0.0, -1.0, 0.0)
    private val STAR_RAYS = listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.7, 0.7, 0.0), Vec3(0.0, 0.7, -0.7))
    private const val WHITE = 0xFFFFFF

    private const val CRACK = WorldEffectPayload.Lash.WHIP_CRACK_TICKS
    private val LIFE = WorldEffectPayload.Lash.Shape.WHIP.life

    /** A segment's length and width in blocks: the texture is 32 pixels a block, cells 16 × 8. */
    private const val SEGMENT = 0.5
    private const val WIDTH = 0.25
    private const val MIN_SEGMENTS = 6
    private const val MAX_SEGMENTS = 24

    /** How far behind the shoulder the whip starts (radians from the line to the target), and how tilted. */
    private const val WIND_UP = 2.6
    private const val SHOULDER = PI / 6

    /** How much later the tip turns than the grip; the share of its length the whip is coiled to at the start. */
    private const val LAG = 0.85f
    private const val COILED = 0.45
    private const val OVERSHOOT = 1.4f

    /** After the crack: how far the far end droops (radians) and how much of the length is drawn back. */
    private const val DROOP = 1.1
    private const val RECOIL = 0.85
    private const val FADE_TICKS = 3f

    private const val NEAR_GONE = 0.3
    private const val NEAR_SPAN = 0.3

    /** The first-person main hand, in the camera's axes (vanilla's held-item offsets). */
    private const val HAND_AHEAD = 0.72
    private const val HAND_RIGHT = 0.56
    private const val HAND_DOWN = -0.52
    private const val CURRENT_NEAR_GONE = 1.5
    private const val CURRENT_NEAR_SPAN = 1.5

    private const val CHARGED_SHARE = 0.3f
    private const val JITTER = 0.12
    private const val FLASH_TICKS = 2
    private const val STAR = 0.35
    private const val SPARKS = 8
}
