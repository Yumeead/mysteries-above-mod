package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.AirShot.End
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Fool's air bullet in flight, as bent air ([Refraction]): one packet says where from, where to
 * and how it ends, and every frame the shot is drawn as a function of its age.
 *
 * - **The shot.** A wave of bent air runs out from the fingertip and is gone in a few ticks.
 * - **The flight.** A lens the size of a fist rides the line; behind it the air it tore through
 *   trembles, widens and settles back from the shooter's end first — a cone that dies in ~0.4 s — and
 *   two or three pale streaks of wind twist along the track.
 * - **The end.** A burst of bent air where it struck a body, a smaller one where a wall stopped it,
 *   a puff where it spent itself in the air.
 *
 * Under a shader pack the bending is never drawn ([ShaderPacks]): the rims and the streaks are, and
 * that is the whole look there, not a stand-in. Near the camera everything thins out, so the shooter
 * looking down their own track sees it from inside, not a wall of discs at the eye.
 */
object AirShots {

    private class Shot(val shot: WorldEffectPayload.AirShot) {
        var age = 0
        val span: Vec3 = shot.to.subtract(shot.from)
        val length = span.length()
        val dir: Vec3 = if (length > 1e-6) span.scale(1 / length) else Vec3(0.0, 0.0, 1.0)
        val side: Vec3 = dir.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up: Vec3 = side.cross(dir).normalize()
        val twist = Math.random() * 2 * Math.PI
        val size = if (shot.powered) POWERED else 1.0

        /** Ticks since the bullet passed [along] blocks of its track; negative — not there yet. */
        fun since(t: Float, along: Double): Float = t - (along / length * shot.ticks).toFloat()

        fun at(along: Double): Vec3 = shot.from.add(dir.scale(along))

        val gone: Int get() = shot.ticks + LINGER_TICKS
    }

    private val shots = mutableListOf<Shot>()

    /** Whether anything is in the air: the frame's colour is copied only then. */
    val active: Boolean get() = shots.isNotEmpty()

    fun accept(payload: WorldEffectPayload.AirShot) {
        shots += Shot(payload)
    }

    fun tick() {
        shots.removeAll { ++it.age > it.gone }
    }

    fun clear() = shots.clear()

    fun submit(context: LevelRenderContext) {
        if (shots.isEmpty()) return
        val camera = context.levelState().cameraRenderState.pos
        val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val flying = shots.toList()
        val plain = ShaderPacks.inUse
        if (!plain && Refraction.ready) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), Refraction.TYPE) { pose, buffer ->
                for (shot in flying) bend(pose, buffer, camera, shot, shot.age + partial)
            }
        }
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning()) { pose, buffer ->
            for (shot in flying) light(pose, buffer, camera, shot, shot.age + partial, rims = plain)
        }
    }

    /** Every disc of bent air the shot is at [t]: the muzzle wave, the lens, its wake and the end. */
    private fun bend(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, shot: Shot, t: Float) {
        discs(shot, t) { center, radius, kind, strength, front, rimmed ->
            Refraction.disc(pose, buffer, camera, center, radius, kind, strength, if (rimmed) AIR else WAKE_RIM, front)
        }
    }

    /** The streaks of wind, and — under a shader pack — the rim of every disc the bending would be. */
    private fun light(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, shot: Shot, t: Float, rims: Boolean) {
        val streaks = if (shot.shot.powered) 3 else 2
        val travelled = shot.length * (t / shot.shot.ticks).coerceAtMost(1f)
        for (j in 0 until streaks) {
            val phase = shot.twist + j * 2 * Math.PI / streaks
            var last: Vec3? = null
            var along = 0.0
            while (along <= travelled) {
                val since = shot.since(t, along)
                val fade = 1f - since / STREAK_TICKS
                val radius = STREAK_RADIUS * shot.size * (1 + since / STREAK_TICKS)
                val angle = phase + along * STREAK_TWIST
                val point = shot.at(along).add(shot.side.scale(cos(angle) * radius)).add(shot.up.scale(sin(angle) * radius))
                if (last != null && fade > 0f) {
                    val alpha = STREAK_ALPHA * fade * nearFade(point, camera)
                    if (alpha > 0.01f) pixelLine(pose, buffer, camera, last, point, argbOf(AIR, alpha))
                }
                last = point
                along += STREAK_STEP
            }
        }
        if (!rims) return
        discs(shot, t) { center, radius, kind, strength, front, rimmed ->
            if (!rimmed) return@discs
            val ring = if (kind == Refraction.Kind.WAVE) radius * front else radius
            val alpha = RIM_ALPHA * strength * nearFade(center, camera)
            if (ring > 0.02 && alpha > 0.01f) Refraction.rimOnly(pose, buffer, camera, center, ring, argbOf(AIR, alpha))
        }
    }

    /**
     * Walks the discs of the shot at [t]: centre, radius, kind, strength, how far a wave has run, and
     * whether it has a rim — the wake has none: it is air trembling, not a shape.
     */
    private inline fun discs(shot: Shot, t: Float, disc: (Vec3, Double, Refraction.Kind, Float, Float, Boolean) -> Unit) {
        val ticks = shot.shot.ticks

        // Out of the fingertip: a ring of bent air widening and gone.
        val muzzle = t / MUZZLE_TICKS
        if (muzzle < 1f) disc(shot.shot.from, MUZZLE_RADIUS * shot.size, Refraction.Kind.WAVE, 1f - muzzle, ease(muzzle) * WAVE_REACH, true)

        // The wake: the air it tore through trembles, widens and settles, oldest first.
        val travelled = shot.length * (t / ticks).coerceAtMost(1f)
        var along = WAKE_STEP
        while (along < travelled) {
            val life = shot.since(t, along) / WAKE_TICKS
            if (life in 0f..1f) {
                val tremble = shot.side.scale(sin(t * TREMBLE_RATE + along * 7) * TREMBLE).add(shot.up.scale(cos(t * TREMBLE_RATE * 1.3 + along * 5) * TREMBLE))
                val radius = LENS_RADIUS * shot.size * (1 + life * WAKE_SPREAD)
                disc(shot.at(along).add(tremble), radius, Refraction.Kind.LENS, WAKE_STRENGTH * (1 - life) * (1 - life), 1f, false)
            }
            along += WAKE_STEP
        }

        // The round itself, while it is still on its way.
        if (t < ticks) disc(shot.at(travelled), LENS_RADIUS * shot.size, Refraction.Kind.LENS, 1f, 1f, true)

        // Where it ends: a burst on a body, a smaller one on a wall, a puff in the air.
        val end = (t - ticks) / BURST_TICKS
        if (end in 0f..1f) {
            val radius = shot.size * when (shot.shot.end) {
                End.BODY -> BURST_BODY
                End.WALL -> BURST_WALL
                End.AIR -> BURST_AIR
            }
            // Pulled back off the face it stopped at, so the wall does not swallow half of it.
            val center = shot.shot.to.subtract(shot.dir.scale(if (shot.shot.end == End.WALL) WALL_STANDOFF else 0.0))
            disc(center, radius, Refraction.Kind.WAVE, 1f - end, ease(end) * WAVE_REACH, true)
        }
    }

    /** Light and lenses thin out over the last blocks before the eye: the shooter sees the track from inside. */
    private fun nearFade(at: Vec3, camera: Vec3): Float = ((at.distanceTo(camera) - NEAR_START) / (NEAR_END - NEAR_START)).toFloat().coerceIn(0f, 1f)

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c)
    }

    private val UP = Vec3(0.0, 1.0, 0.0)

    /** The pale grey-blue of air, for rims and streaks. */
    private const val AIR = 0xD6DEE6

    /** The wake's rim colour: next to nothing, so its edge adds no light. */
    private const val WAKE_RIM = 0x101214

    /** How much bigger everything of a powered shot is. */
    private const val POWERED = 1.5

    private const val LENS_RADIUS = 0.15
    private const val MUZZLE_RADIUS = 0.8
    private const val MUZZLE_TICKS = 4f
    /** How far out a wave's ring runs before it is gone, as a share of its disc. */
    private const val WAVE_REACH = 0.85f

    private const val WAKE_STEP = 0.5
    private const val WAKE_TICKS = 8f
    private const val WAKE_SPREAD = 2.5
    private const val WAKE_STRENGTH = 0.55f
    private const val TREMBLE = 0.03
    private const val TREMBLE_RATE = 3f

    private const val STREAK_STEP = 0.3
    private const val STREAK_TICKS = 10f
    private const val STREAK_RADIUS = 0.08
    private const val STREAK_TWIST = 3.2
    private const val STREAK_ALPHA = 0.2f
    private const val RIM_ALPHA = 0.6f

    private const val BURST_TICKS = 6f
    private const val BURST_BODY = 1.7
    private const val BURST_WALL = 0.9
    private const val BURST_AIR = 0.6
    private const val WALL_STANDOFF = 0.15

    private const val NEAR_START = 1.0
    private const val NEAR_END = 2.5

    /** Long enough for the wake of the first block and the burst to die after the bullet lands. */
    private const val LINGER_TICKS = 12
}
