package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.net.payload.ThreadSightPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Threads of the Spirit Body, as a Marionettist's Spirit Vision shows them (Fool, Seq. 5).
 *
 * ## What is drawn
 *
 * | state | the threads |
 * |---|---|
 * | free | pale silver, nearly transparent; out of the whole body (crown, shoulders, hips, knees) and up, swaying, fading into the Spirit World |
 * | weak, asleep, dying | dimmer with every lost heart; asleep at half; dying, they wither within a second |
 * | a grip being taken | ink runs down them from the caster's end; they go taut, bend towards the caster's hand and twitch — the victim resists |
 * | a puppet | black and ash, dry, perfectly still, straight from the body to the master's hand |
 *
 * ## Built here rather than sent
 *
 * Everything but the bond is already on the client: the living things, their health, their death
 * animation. The server sends only who holds whom ([ThreadSightPayload]); the rest is derived
 * here, every frame, against the body as it is being drawn — the only way a strand can rise out
 * of a moving shoulder without trailing behind it (see pitfall 7 in CLAUDE.md, which the glyph
 * already paid for).
 *
 * ## A line a pixel wide
 *
 * A strand is a hairline a pixel of a block across, bent along its curve in short straight runs —
 * the Door's rim and the Magician's thread are drawn the same way. The first version strung it out
 * of the spirit glyph's diamonds, and a bond across a few blocks read as a dotted line.
 *
 * Drawn translucent rather than as light ([RenderTypes.textBackground]): a puppet's strands are
 * black, and light can only add, so black light would not be there at all.
 */
object SpiritThreads {

    private var range = 0
    private var bonds: Map<Int, ThreadSightPayload.Bond> = emptyMap()
    private var clock = 0L
    private var heardAt = Long.MIN_VALUE / 2
    private val tangles = HashMap<Int, Tangle>()
    private var wasCrouching = false

    fun accept(payload: ThreadSightPayload) {
        range = payload.range
        bonds = payload.bonds.associateBy { it.held }
        heardAt = clock
    }

    fun clear() {
        tangles.clear()
        bonds = emptyMap()
        range = 0
    }

    /** Once a client tick: which bodies have threads, and how each one is held. */
    fun tick() {
        clock++
        val mc = Minecraft.getInstance()
        val level = mc.level
        val me = mc.player
        // The packets are the subscription: no beat for a second and a half — the sight is gone.
        if (level == null || me == null || clock - heardAt > STALE_TICKS) {
            if (tangles.isNotEmpty()) clear()
            return
        }

        val reach = range.toDouble()
        val wanted = HashMap<Int, LivingEntity>()
        level.getEntitiesOfClass(LivingEntity::class.java, me.boundingBox.inflate(reach)) {
            hasThreads(it, me) && it.distanceToSqr(me) <= reach * reach
        }.sortedBy { it.distanceToSqr(me) }.take(MAX_FREE).forEach { wanted[it.id] = it }
        // A held body is seen however far it is, as long as the client has it at all.
        bonds.keys.forEach { id ->
            (level.getEntity(id) as? LivingEntity)?.takeIf { hasThreads(it, me) }?.let { wanted[id] = it }
        }

        // Crouching is focusing the Spirit Vision: everything faded is looked at again.
        val focus = me.isShiftKeyDown && !wasCrouching
        wasCrouching = me.isShiftKeyDown
        if (focus) tangles.values.forEach(Tangle::notice)

        tangles.entries.removeIf { (id, tangle) -> wanted[id] !== tangle.body }
        wanted.forEach { (id, body) ->
            val bond = bonds[id]
            tangles.getOrPut(id) { Tangle(body) }.tick(bond?.grip ?: 0f, bond?.holder?.let(level::getEntity))
        }
    }

    fun submit(context: LevelRenderContext) {
        if (tangles.isEmpty()) return
        val mc = Minecraft.getInstance()
        val camera = context.levelState().cameraRenderState.pos
        val view = mc.gameRenderer.mainCamera()
        val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val all = tangles.values.toList()
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
            for (tangle in all) tangle.draw(pose, buffer, camera, partial, view)
        }
    }

    /** Not the local player (their threads would wrap the camera) and nothing without a spirit. */
    private fun hasThreads(entity: LivingEntity, me: Entity): Boolean =
        entity !== me &&
            entity !is ArmorStand &&
            !entity.isSpectator &&
            !(entity is MarionetteEntity && entity.hiddenForDriver)

    /** One body's threads: its strands and its grip. */
    private class Tangle(val body: LivingEntity) {

        private val strands: List<Strand> = (if (body.bbHeight >= HUMANOID_HEIGHT) HUMANOID else SMALL)
            .mapIndexed { i, (side, height) ->
                Strand(side, height, rise = RISE * (0.8f + 0.4f * hash(body.id, i)), phase = 6.28f * hash(body.id, i + 7))
            }
        private var grip = 0f
        private var gripO = 0f
        private var holder: Entity? = null

        /** When the sight last had a reason to look at this body; the threads fade after it. */
        private var noticedAt = clock

        fun notice() {
            noticedAt = clock
        }

        /** The three control points of every strand (p0, p1, p2 — 9 doubles each), for the frame being drawn. */
        private val curve = DoubleArray(strands.size * 9)

        /** Per strand, per point: this tick's twitch, −1..1 on each axis. */
        private val jolts = FloatArray(strands.size * (SEGMENTS + 1) * 3)

        fun tick(target: Float, holder: Entity?) {
            // A grip being taken is watched the whole way; a blow draws the eye back.
            if ((target > 0f && target < 1f) || body.hurtTime > 0) notice()
            gripO = grip
            // Taken slowly, let go a little faster: a snapped grip springs back.
            grip = if (target >= grip) minOf(target, grip + GRIP_RISE) else maxOf(target, grip - GRIP_FALL)
            this.holder = holder
            if (grip > 0f && grip < 1f) jolts.indices.forEach { jolts[it] = kotlin.random.Random.nextFloat() * 2f - 1f }
        }

        fun draw(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, partial: Float, view: Camera) {
            val g = Mth.lerp(partial, gripO, grip)
            if (attention(g) <= 0f) return
            shape(partial, view)
            val width = if (g >= 1f) PUPPET_WIDTH else FREE_WIDTH
            strands.forEachIndexed { s, strand ->
                var previous = point(s, strand, 0, g, partial)
                for (k in 1..SEGMENTS) {
                    val next = point(s, strand, k, g, partial)
                    val t = (k - 0.5f) / SEGMENTS
                    val alpha = alpha(t, g)
                    if (alpha > MIN_ALPHA) {
                        // A pixel of a block at arm's length is a band across the screen: near the eye, thinner.
                        val near = (previous.add(next).scale(0.5).distanceTo(camera) / NEAR_EYE).coerceIn(NEAR_THINNEST, 1.0)
                        pixelLine(pose, buffer, camera, previous, next, argbOf(paint(t, k + s, g), alpha), width * near, LightCoordsUtil.FULL_BRIGHT)
                    }
                    previous = next
                }
            }
        }

        /** The [k]-th point along strand [s], for this frame. */
        private fun point(s: Int, strand: Strand, k: Int, g: Float, partial: Float): Vec3 {
            val t = k.toDouble() / SEGMENTS
            val u = 1.0 - t
            val i = s * 9
            val a = u * u
            val b = 2.0 * u * t
            val c = t * t
            var x = a * curve[i] + b * curve[i + 3] + c * curve[i + 6]
            var y = a * curve[i + 1] + b * curve[i + 4] + c * curve[i + 7]
            var z = a * curve[i + 2] + b * curve[i + 5] + c * curve[i + 8]

            // Free threads drift in a wind that is not of this world; a held one cannot.
            val sway = SWAY * (1f - g) * t
            val time = clock + partial
            x += sin(time * 0.07 + strand.phase + t * 3.0) * sway
            z += cos(time * 0.05 + strand.phase * 1.3 + t * 2.5) * sway
            // The victim resists: a strand being taken twitches like a plucked string, ends pinned.
            if (g > 0f && g < 1f) {
                val jolt = (JOLT_START + JOLT_GAIN * g) * sin(PI * t)
                val j = (s * (SEGMENTS + 1) + k) * 3
                x += jolts[j] * jolt
                y += jolts[j + 1] * jolt * 0.5
                z += jolts[j + 2] * jolt
            }
            return Vec3(x, y, z)
        }

        private fun shape(partial: Float, camera: Camera) {
            val bx = Mth.lerp(partial.toDouble(), body.xo, body.x)
            val by = Mth.lerp(partial.toDouble(), body.yo, body.y)
            val bz = Mth.lerp(partial.toDouble(), body.zo, body.z)
            val yaw = Math.toRadians(Mth.rotLerp(partial, body.yBodyRotO, body.yBodyRot).toDouble())
            val rightX = -cos(yaw)
            val rightZ = -sin(yaw)
            val halfWidth = body.bbWidth / 2.0
            val height = body.bbHeight.toDouble()
            val hand = holder?.takeIf { !it.isRemoved }?.let { handOf(it, partial, camera) }
            val g = Mth.lerp(partial, gripO, grip).toDouble()
            val pull = g * g * (3.0 - 2.0 * g)

            strands.forEachIndexed { s, strand ->
                val i = s * 9
                val x0 = bx + rightX * strand.side * halfWidth
                val y0 = by + strand.height * height
                val z0 = bz + rightZ * strand.side * halfWidth
                // Unheld, a strand ends straight above its root, in the Spirit World.
                var x2 = x0
                var y2 = y0 + strand.rise
                var z2 = z0
                if (hand != null) {
                    x2 += (hand[0] - x2) * pull
                    y2 += (hand[1] - y2) * pull
                    z2 += (hand[2] - z2) * pull
                }
                // The bend: rising first while free; once held, a high arch over to the hand, the
                // way a marionette's strings climb to the control bar and come down to the fingers.
                val span = Math.sqrt((x2 - x0) * (x2 - x0) + (y2 - y0) * (y2 - y0) + (z2 - z0) * (z2 - z0))
                val arch = maxOf(y0, y2) + (span * ARCH_SHARE).coerceIn(ARCH_MIN, ARCH_MAX)
                val x1 = Mth.lerp(g, x0, (x0 + x2) / 2.0)
                val y1 = Mth.lerp(g, y0 + strand.rise * 0.8, arch)
                val z1 = Mth.lerp(g, z0, (z0 + z2) / 2.0)
                curve[i] = x0; curve[i + 1] = y0; curve[i + 2] = z0
                curve[i + 3] = x1; curve[i + 4] = y1; curve[i + 5] = z1
                curve[i + 6] = x2; curve[i + 7] = y2; curve[i + 8] = z2
            }
        }

        /** Ink runs down from the caster's end; behind its wet edge the strand is already dead. */
        private fun paint(t: Float, index: Int, g: Float): Int {
            if (g >= 1f) return if (index % ASH_EVERY == 0) ASH else DEAD
            val front = g * INK_SPEED
            val soaked = t - (1f - front)
            if (soaked <= 0f) return PALE
            return lerpColor(WET, DEAD, (soaked / WET_EDGE).coerceIn(0f, 1f))
        }

        private fun alpha(t: Float, g: Float): Float {
            val vitality = (body.health / body.maxHealth).coerceIn(0f, 1f)
            var free = FREE_ALPHA * (0.35f + 0.65f * vitality) * (1f - 0.85f * t * t)
            if (body.isSleeping) free *= 0.5f
            val withering = if (body.deathTime > 0) (1f - body.deathTime / DEATH_TICKS).coerceAtLeast(0f) else 1f
            return Mth.lerp(g, free, HELD_ALPHA) * withering * attention(g)
        }

        /**
         * The sight does not stare: threads show at full for a few seconds after being noticed,
         * then fade — a free body's to nothing, a puppet's to a faint reminder.
         */
        private fun attention(g: Float): Float {
            val idle = (clock - noticedAt - ATTENTION_HOLD).toFloat()
            if (idle <= 0f) return 1f
            val rest = if (g >= 1f) PUPPET_REST else 0f
            return maxOf(rest, 1f - idle / ATTENTION_FADE)
        }
    }

    private data class Strand(val side: Float, val height: Float, val rise: Float, val phase: Float)


    /**
     * The point the threads run to: the holder's right hand. For the local player in first
     * person that is in front of the camera, low and to the right, where their own hand is drawn.
     */
    private fun handOf(holder: Entity, partial: Float, camera: Camera): DoubleArray {
        val firstPerson = !camera.isDetached &&
            (holder === camera.entity() || (holder is MarionetteEntity && holder.hiddenForDriver))
        if (firstPerson) {
            val eye = camera.position()
            val f = camera.forwardVector()
            val l = camera.leftVector()
            val up = camera.upVector()
            return doubleArrayOf(
                eye.x + f.x() * 0.7 - l.x() * 0.35 - up.x() * 0.3,
                eye.y + f.y() * 0.7 - l.y() * 0.35 - up.y() * 0.3,
                eye.z + f.z() * 0.7 - l.z() * 0.35 - up.z() * 0.3,
            )
        }
        val yaw = Math.toRadians(
            (if (holder is LivingEntity) Mth.rotLerp(partial, holder.yBodyRotO, holder.yBodyRot) else holder.yRot).toDouble(),
        )
        val forwardX = -sin(yaw)
        val forwardZ = cos(yaw)
        return doubleArrayOf(
            Mth.lerp(partial.toDouble(), holder.xo, holder.x) - cos(yaw) * HAND_SIDE + forwardX * HAND_FORWARD,
            Mth.lerp(partial.toDouble(), holder.yo, holder.y) + holder.bbHeight * HAND_HEIGHT,
            Mth.lerp(partial.toDouble(), holder.zo, holder.z) - sin(yaw) * HAND_SIDE + forwardZ * HAND_FORWARD,
        )
    }

    /** A stable 0..1 per body and strand, so a creature keeps the same tangle from tick to tick. */
    private fun hash(id: Int, salt: Int): Float {
        var h = id * 0x9E3779B1.toInt() + salt * 0x85EBCA6B.toInt()
        h = h xor (h ushr 15)
        h *= 0x2C1B3C6D
        h = h xor (h ushr 13)
        return (h and 0xFFFF) / 65535f
    }

    private fun lerpColor(from: Int, to: Int, k: Float): Int {
        fun ch(shift: Int) = Mth.lerp(k, (from shr shift and 0xFF).toFloat(), (to shr shift and 0xFF).toFloat()).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Roots on the body: (side across the width, -1..1; height share). The crown and the two
     * shoulders — a marionette's own three strings. Seven read as a hairy cloud on a cow.
     */
    private val HUMANOID = listOf(0f to 1.0f, -1f to 0.8f, 1f to 0.8f)
    private val SMALL = listOf(0f to 1.0f)
    private const val HUMANOID_HEIGHT = 1.2f

    private const val STALE_TICKS = 30
    private const val MAX_FREE = 24

    /** Straight runs per strand: enough for the arch to read as a curve, not a polygon. */
    private const val SEGMENTS = 24

    /** How far a free strand climbs before it is gone into the Spirit World. */
    private const val RISE = 2.6f
    private const val SWAY = 0.14

    /** How high the held strands arch over, as a share of their span, within these bounds. */
    private const val ARCH_SHARE = 0.35
    private const val ARCH_MIN = 1.2
    private const val ARCH_MAX = 6.0

    private const val GRIP_RISE = 0.02f
    private const val GRIP_FALL = 0.06f
    private const val JOLT_START = 0.07
    private const val JOLT_GAIN = 0.22

    /** The ink reaches the body a little before the grip is done, so the last stretch is all black. */
    private const val INK_SPEED = 1.15f
    private const val WET_EDGE = 0.18f

    /** Full sight for this long after a body is noticed, then a fade over the next stretch. */
    private const val ATTENTION_HOLD = 4 * 20
    private const val ATTENTION_FADE = 2f * 20f
    private const val PUPPET_REST = 0.2f

    private const val FREE_ALPHA = 0.5f
    private const val HELD_ALPHA = 0.9f
    private const val DEATH_TICKS = 20f
    private const val FREE_WIDTH = PIXEL
    private const val PUPPET_WIDTH = PIXEL * 1.5
    private const val MIN_ALPHA = 0.004f
    private const val NEAR_EYE = 4.0
    private const val NEAR_THINNEST = 0.15
    /** Every few runs of a puppet's strand: ash. */
    private const val ASH_EVERY = 5

    private const val HAND_SIDE = 0.4
    private const val HAND_FORWARD = 0.25
    private const val HAND_HEIGHT = 0.6f

    /** Silver, barely there. */
    private const val PALE = 0xD2DEEC

    /** The wet edge of the ink: a dark crimson, as if the strand were bleeding into it. */
    private const val WET = 0x6B0F2A

    /** Dry, dead black with a breath of violet — the Fool's own. */
    private const val DEAD = 0x120C16

    private const val ASH = 0x4A464C
}
