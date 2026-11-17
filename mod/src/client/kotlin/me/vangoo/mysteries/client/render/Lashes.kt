package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Lash.Shape
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The client half of `pathways/justiciar/Edict.kt`'s [lash][me.vangoo.mysteries.pathways.justiciar]: what passes
 * between two bodies, riding both frame by frame.
 *
 * - **Whip**: handed to [Whips], a textured whip with its own swing.
 * - **Needle** (Psychic Piercing): a bolt of lightning out of **each** eye into the target's head. The target's
 *   spirit is knocked half out of the body: a pale double of it is thrown back and fades.
 * - **Coat** (Psychic Lashing): little arcs crawling over the weapon in hand while it is charged. In one's own
 *   first person they are drawn in the held item's own coordinates (`HeldItemLayerMixin`), so they ride the
 *   blade through the swing; elsewhere along a guess at the blade beside the hand.
 * - **Bolt** (Psychic Lashing): the lightning jumping onto what the charged blade struck: arcs crawl over the
 *   struck body for a moment, and the blade flares.
 *
 * Every bolt is a jagged line re-drawn every [FLICKER_TICKS] (so it crackles), with forks, a white core and a
 * wide dim halo, and fades instead of vanishing.
 */
object Lashes {

    private class Live(val from: Int, val to: Int, val shape: Shape, val rgb: Int) {
        var age = 0
    }

    /** A spirit knocked out of [entity], thrown along [away]. */
    private class Ghost(val entity: Int, val away: Vec3) {
        var age = 0
    }

    private val lashes = mutableListOf<Live>()
    private val ghosts = mutableListOf<Ghost>()

    fun accept(payload: WorldEffectPayload.Lash) {
        if (payload.shape == Shape.WHIP) return Whips.accept(payload)
        if (payload.shape == Shape.DASH || payload.shape == Shape.FLOG) return Verdicts.accept(payload)
        if (payload.shape == Shape.MARK) return Marks.accept(payload)
        lashes += Live(payload.from, payload.to, payload.shape, payload.color and 0xFFFFFF)
        if (payload.shape == Shape.NEEDLE) {
            val level = Minecraft.getInstance().level ?: return
            val from = level.getEntity(payload.from) ?: return
            val to = level.getEntity(payload.to) ?: return
            val away = to.position().subtract(from.position()).multiply(1.0, 0.0, 1.0)
            ghosts += Ghost(payload.to, if (away.lengthSqr() < 1e-6) Vec3(0.0, 0.0, 1.0) else away.normalize())
        }
    }

    fun tick() {
        val level = Minecraft.getInstance().level
        lashes.removeAll { lash ->
            lash.age++
            // Now and then a spark leaps off the charged blade.
            if (lash.shape == Shape.COAT && level != null && lash.age % SPARK_EVERY == 0) {
                level.getEntity(lash.from)?.let { holder ->
                    val (a, b) = blade(holder, 1f)
                    val at = a.lerp(b, level.random.nextDouble())
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0.0, 0.05, 0.0)
                }
            }
            lash.age >= lash.shape.life
        }
        ghosts.removeAll { ++it.age >= GHOST_TICKS }
        Whips.tick()
    }

    fun clear() {
        lashes.clear()
        ghosts.clear()
        Whips.clear()
    }

    fun submit(context: LevelRenderContext) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos
        val strokes = Strokes()
        Whips.submit(context, strokes)
        for (lash in lashes) {
            val from = level.getEntity(lash.from) ?: continue
            val to = level.getEntity(lash.to) ?: continue
            val age = lash.age + partial
            when (lash.shape) {
                Shape.WHIP, Shape.DASH, Shape.FLOG, Shape.MARK -> Unit
                Shape.NEEDLE -> needle(strokes, lash, age, from, to, partial, camera)
                Shape.BOLT -> struck(strokes, lash, age, to, partial, camera)
                Shape.COAT -> if (!Whips.firstPerson(from)) coat(strokes, lash, age, from, partial, camera)
            }
        }
        if (!strokes.isEmpty) strokes.submit(context)
        for (ghost in ghosts) {
            val entity = level.getEntity(ghost.entity) as? LivingEntity ?: continue
            ghost(context, entity, ghost, ghost.age + partial, partial, camera)
        }
    }

    /** A bolt out of each eye into the head: out in a tick, crackling, then dimming. */
    private fun needle(strokes: Strokes, lash: Live, age: Float, from: Entity, to: Entity, partial: Float, camera: Vec3) {
        val reach = (age / WorldEffectPayload.Lash.REACH_TICKS).coerceIn(0f, 1f)
        val strength = fade(age, lash.shape.life)
        val head = to.getEyePosition(partial)
        for ((i, eye) in eyes(from, partial).withIndex()) {
            val end = eye.lerp(head, reach.toDouble())
            bolt(strokes, eye, end, seed(lash, age, i), strength, lash.rgb, NEEDLE_JAG, camera)
        }
    }

    /** Arcs crawling over the body the blade struck: between points of its frame, re-chosen as they flicker. */
    private fun struck(strokes: Strokes, lash: Live, age: Float, body: Entity, partial: Float, camera: Vec3) {
        val feet = body.getPosition(partial)
        val half = body.bbWidth / 2.0
        val random = Random(seed(lash, age, 3))
        fun point() = feet.add((random.nextDouble() * 2 - 1) * half, random.nextDouble() * body.bbHeight, (random.nextDouble() * 2 - 1) * half)
        repeat(STRUCK_ARCS) { i -> bolt(strokes, point(), point(), seed(lash, age, i + 11), fade(age, lash.shape.life), lash.rgb, ARC_JAG, camera) }
    }

    /** Small arcs between points of the blade, a couple at a time, re-chosen as they flicker; more while it strikes. */
    private fun coat(strokes: Strokes, lash: Live, age: Float, holder: Entity, partial: Float, camera: Vec3) {
        val (a, b) = blade(holder, partial)
        val strength = fade(age, lash.shape.life)
        val random = Random(seed(lash, age, 7))
        repeat(arcsOn(lash)) { i ->
            val t = random.nextDouble() * 0.7
            val span = 0.15 + random.nextDouble() * 0.3
            bolt(strokes, a.lerp(b, t), a.lerp(b, (t + span).coerceAtMost(1.0)), seed(lash, age, i + 1), strength, lash.rgb, COAT_JAG, camera, forks = false)
        }
    }

    /**
     * One bolt from [a] to [b]: a jagged line [jag] blocks off the straight at most, with a fork or two, a dim
     * halo under a near-white core. [seed] fixes its shape; [strength] (0 → 1) its brightness.
     */
    internal fun bolt(strokes: Strokes, a: Vec3, b: Vec3, seed: Long, strength: Float, rgb: Int, jag: Double, camera: Vec3, forks: Boolean = true) {
        if (strength <= 0f) return
        val random = Random(seed)
        val axis = b.subtract(a)
        val length = axis.length()
        if (length < 1e-4) return
        val (u, v) = across(axis.scale(1 / length))
        val steps = (length / BOLT_STEP).toInt().coerceIn(3, 40)
        val points = ArrayList<Vec3>(steps + 1)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            // Ends fixed, the middle wandering most.
            val swing = if (i == 0 || i == steps) 0.0 else jag * sin(t * PI).coerceAtLeast(0.35)
            val angle = random.nextDouble() * 2 * PI
            points += a.add(axis.scale(t)).add(u.scale(cos(angle) * swing * random.nextDouble())).add(v.scale(sin(angle) * swing * random.nextDouble()))
        }
        val coreRgb = Strands.mix(rgb, WHITE, CORE_WHITE)
        for (i in 0 until steps) {
            val near = nearFade(points[i], camera)
            if (near <= 0f) continue
            val thin = thinning(points[i], camera)
            strokes.hot(points[i], points[i + 1], argbOf(rgb, HALO_ALPHA * strength * near), PIXEL * HALO_WIDTH * thin)
            strokes.hot(points[i], points[i + 1], argbOf(coreRgb, strength * near), PIXEL * CORE_WIDTH * thin)
        }
        if (!forks) return
        repeat(1 + random.nextInt(2)) {
            val from = points[1 + random.nextInt(steps - 1)]
            val angle = random.nextDouble() * 2 * PI
            val out = axis.scale((0.15 + random.nextDouble() * 0.2) / length * length.coerceAtMost(3.0))
                .add(u.scale(cos(angle) * jag * 2)).add(v.scale(sin(angle) * jag * 2))
            val mid = from.add(out.scale(0.5)).add(u.scale((random.nextDouble() - 0.5) * jag))
            if (nearFade(from, camera) <= 0f) return@repeat
            val thin = thinning(from, camera)
            strokes.hot(from, mid, argbOf(rgb, HALO_ALPHA * strength), PIXEL * HALO_WIDTH * 0.6 * thin)
            strokes.hot(from, mid, argbOf(coreRgb, strength), PIXEL * CORE_WIDTH * thin)
            strokes.hot(mid, from.add(out), argbOf(rgb, strength * 0.6f), PIXEL * thin)
        }
    }

    /** How many arcs a charged blade carries now: a couple, more for a moment after it struck someone. */
    private fun arcsOn(coat: Live): Int =
        if (lashes.any { it.shape == Shape.BOLT && it.from == coat.from }) COAT_ARCS_STRIKING else COAT_ARCS

    /** The charge on the item the first-person hand renderer is drawing now, if it is a charged weapon in hand. */
    private var held: Live? = null

    /** From HeldItemCoatMixin: [entity]'s [stack] is about to be drawn in [context]. */
    fun holding(entity: LivingEntity, stack: ItemStack, context: ItemDisplayContext) {
        // The hand renderer keeps its own copy of the stack, so not identity: the hand the main arm is.
        val mainHand = if (entity.mainArm == HumanoidArm.RIGHT) ItemDisplayContext.FIRST_PERSON_RIGHT_HAND else ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        held = if (context == mainHand && !stack.isEmpty) {
            lashes.firstOrNull { it.shape == Shape.COAT && it.from == entity.id }
        } else {
            null
        }
    }

    fun released() {
        held = null
    }

    /** Whether HeldItemLayerMixin should draw onto the layer it is about to submit. */
    fun coating(): Boolean = held != null

    /**
     * Arcs along the blade of the held item, in its own coordinates: a flat item spans 0..1 across and up and is a
     * sixteenth thick about z = 0.5; a sword's blade runs corner to corner. Flat quads just off both faces (found
     * by a test quad: one at z = 0 lay hidden behind the sword, peeking out at its edges).
     */
    fun coatHeld(poses: PoseStack, collector: SubmitNodeCollector) {
        val coat = held ?: return
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val age = coat.age + partial
        val strength = fade(age, coat.shape.life)
        if (strength <= 0f) return
        val random = Random(seed(coat, age, 7))
        val core = argbOf(Strands.mix(coat.rgb, WHITE, CORE_WHITE), strength)
        val halo = argbOf(coat.rgb, HALO_ALPHA * strength)
        val arcs = arcsOn(coat)
        collector.submitCustomGeometry(poses, RenderTypes.lightning()) { pose, buffer ->
            repeat(arcs) {
                val t0 = HELD_BLADE_FROM + random.nextFloat() * (HELD_BLADE_TO - HELD_BLADE_FROM) * 0.7f
                val t1 = (t0 + (0.15f + random.nextFloat() * 0.3f) * HELD_UNIT).coerceAtMost(HELD_BLADE_TO)
                var x = t0
                var y = t0
                val steps = 4
                for (i in 1..steps) {
                    val t = t0 + (t1 - t0) * i / steps
                    // Off the blade's diagonal, across it, but home at the ends.
                    val off = if (i == steps) 0f else (random.nextFloat() - 0.5f) * HELD_JAG
                    val nx = t - off
                    val ny = t + off
                    flat(pose, buffer, x, y, nx, ny, HELD_HALO, halo)
                    flat(pose, buffer, x, y, nx, ny, HELD_CORE, core)
                    x = nx
                    y = ny
                }
            }
        }
    }

    /** A line from (x0, y0) to (x1, y1) in the item's plane, [width] across, wound both ways so either face shows. */
    private fun flat(pose: PoseStack.Pose, buffer: VertexConsumer, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, argb: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val sx = -dy / length * width / 2
        val sy = dx / length * width / 2
        val corners = arrayOf(floatArrayOf(x0 - sx, y0 - sy), floatArrayOf(x1 - sx, y1 - sy), floatArrayOf(x1 + sx, y1 + sy), floatArrayOf(x0 + sx, y0 + sy))
        // Just off both faces: a line inside the item's pixel of thickness is hidden by its faces.
        for (z in floatArrayOf(HELD_MIDDLE + HELD_FACE, HELD_MIDDLE - HELD_FACE)) {
            for (order in arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))) {
                for (c in order) buffer.addVertex(pose, corners[c][0], corners[c][1], z).setColor(argb)
            }
        }
    }

    /** The target's spirit, knocked out: a pale double thrown back and up from the body, fading as it goes. */
    private fun ghost(context: LevelRenderContext, entity: LivingEntity, ghost: Ghost, age: Float, partial: Float, camera: Vec3) {
        val t = (age / GHOST_TICKS).coerceIn(0f, 1f)
        val out = 1 - (1 - t) * (1 - t)
        val at = entity.getPosition(partial).add(ghost.away.scale(GHOST_THROW * out)).add(0.0, GHOST_RISE * out, 0.0)
        double(context, entity, at, partial, camera)
    }

    /**
     * A pale see-through double of [entity], in its pose of this frame, standing at [at]: vanilla draws a body that is
     * invisible to all but the viewer as exactly that. No name tag, no shadow, lit by itself. The spirit knocked out by
     * Psychic Piercing, the afterimages of the Verdict of Death.
     */
    internal fun double(context: LevelRenderContext, entity: Entity, at: Vec3, partial: Float, camera: Vec3) {
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val state = dispatcher.extractEntity(entity, partial)
        if (state is LivingEntityRenderState) {
            state.isInvisible = true
            state.isInvisibleToPlayer = false
        }
        state.nameTag = null
        state.shadowRadius = 0f
        state.shadowPieces.clear()
        state.lightCoords = FULL_BRIGHT
        dispatcher.submit(
            state, context.levelState().cameraRenderState,
            at.x - camera.x, at.y - camera.y, at.z - camera.z,
            context.poseStack(), context.submitNodeCollector(),
        )
    }

    /** The two eyes of [entity]: on the face, a little apart. In one's own first person, just ahead of the view. */
    private fun eyes(entity: Entity, partial: Float): List<Vec3> {
        val look = entity.getViewVector(partial)
        val right = look.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val ahead = if (Whips.firstPerson(entity)) FIRST_PERSON_AHEAD else FACE_AHEAD
        val face = entity.getEyePosition(partial).add(look.scale(ahead))
        return listOf(face.add(right.scale(EYE_APART)), face.subtract(right.scale(EYE_APART)))
    }

    /**
     * The weapon in [holder]'s main hand, grip to tip. A guess at the held item's pose, not its model: up and
     * forward from the hand in third person, up and to the left from the fist on screen in one's own first person.
     */
    private fun blade(holder: Entity, partial: Float): Pair<Vec3, Vec3> {
        if (Whips.firstPerson(holder)) {
            // Measured off a screenshot of an iron sword in hand: the grip low at the right edge, the blade up and
            // a little further right and in.
            val look = holder.getViewVector(partial)
            val right = look.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
            val up = right.cross(look)
            val grip = holder.getEyePosition(partial).add(look.scale(0.72)).add(right.scale(0.66)).add(up.scale(-0.42))
            return grip to grip.add(up.scale(0.5)).add(right.scale(0.12)).add(look.scale(0.35))
        }
        val grip = Strands.hand(holder, partial)
        val yaw = Math.toRadians(((holder as? LivingEntity)?.let { it.yBodyRotO + (it.yBodyRot - it.yBodyRotO) * partial } ?: holder.yRot).toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        return grip to grip.add(forward.scale(BLADE * 0.6)).add(0.0, BLADE * 0.8, 0.0)
    }

    /** Two directions square to [axis] and to each other. */
    private fun across(axis: Vec3): Pair<Vec3, Vec3> {
        val u = axis.cross(if (kotlin.math.abs(axis.y) > 0.9) Vec3(1.0, 0.0, 0.0) else UP).normalize()
        return u to axis.cross(u)
    }

    /** Full for most of [life], dimming over its last ticks. */
    private fun fade(age: Float, life: Int): Float = ((life - age) / FADE_TICKS).coerceIn(0f, 1f)

    /** Fixed for [FLICKER_TICKS] at a time: every client sees the same crackle, and it flickers, not swims. */
    private fun seed(lash: Live, age: Float, salt: Int): Long =
        lash.from * 92821L + lash.to * 6271L + (age.toInt() / FLICKER_TICKS) * 131L + salt

    /**
     * A line keeps its width in blocks, so near the eye a pixel of a block is a bar across the screen. Closer than
     * [THIN_FROM] it narrows with the distance: on screen it stays about as thin as it is a few blocks away.
     */
    private fun thinning(at: Vec3, camera: Vec3): Double = (at.distanceTo(camera) / THIN_FROM).coerceIn(0.12, 1.0)

    private fun nearFade(at: Vec3, camera: Vec3): Float = ((at.distanceTo(camera) - 0.3) / 0.4).toFloat().coerceIn(0f, 1f)

    private val UP = Vec3(0.0, 1.0, 0.0)
    private const val WHITE = 0xFFFFFF
    private const val FULL_BRIGHT = 0xF000F0

    private const val FLICKER_TICKS = 2
    private const val FADE_TICKS = 4f
    private const val BOLT_STEP = 0.3
    private const val HALO_ALPHA = 0.25f
    private const val HALO_WIDTH = 3.0
    private const val CORE_WIDTH = 1.0
    private const val CORE_WHITE = 0.5f
    private const val THIN_FROM = 3.0
    private const val NEEDLE_JAG = 0.2
    private const val ARC_JAG = 0.3
    private const val COAT_JAG = 0.06
    private const val COAT_ARCS = 2
    private const val COAT_ARCS_STRIKING = 5
    private const val STRUCK_ARCS = 3

    /** The held item's model, 0..1 across: where the blade runs along the diagonal, and the arcs on it. */
    private const val HELD_UNIT = 1f
    private const val HELD_BLADE_FROM = 0.25f
    private const val HELD_BLADE_TO = 0.95f
    private const val HELD_JAG = 0.08f
    private const val HELD_CORE = 0.022f
    private const val HELD_HALO = 0.065f
    private const val HELD_MIDDLE = 0.5f
    private const val HELD_FACE = 0.04f
    private const val SPARK_EVERY = 6

    private const val FACE_AHEAD = 0.25
    private const val FIRST_PERSON_AHEAD = 0.7
    private const val EYE_APART = 0.12
    private const val BLADE = 0.9

    private const val GHOST_TICKS = 12
    private const val GHOST_THROW = 0.8
    private const val GHOST_RISE = 0.25
}
