package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The client half of Punishment (`pathways/justiciar/Punishments.kt`): what the mark looks like, for as long as it
 * lasts (the `MARK` lash, Paladin to target).
 *
 * - **Dawn**: a burst of morning light behind the Paladin, its rays turning slowly and breathing; everyone sees it.
 * - **The Paladin grows**: in the marked one's eyes alone, the Paladin's body swells to [LOOM] again its size
 *   ([loom], from `PaladinLoomMixin`). Everyone else sees them as they are.
 * - **Unseen shackles**: two faint rings of light round the target's ankles, trembling a little.
 * - **The choosing**: a ring of dawn running out from the target the moment it is marked.
 *
 * Everything comes up over a second and goes out over one.
 */
internal object Marks {

    private class Mark(val paladin: Int, val target: Int, val rgb: Int) {
        var age = 0
    }

    private val marks = mutableListOf<Mark>()
    private val LIFE = WorldEffectPayload.Lash.Shape.MARK.life

    fun accept(payload: WorldEffectPayload.Lash) {
        marks.removeAll { it.target == payload.to }
        marks += Mark(payload.from, payload.to, payload.color and 0xFFFFFF)
    }

    fun tick() {
        marks.removeAll { ++it.age >= LIFE }
    }

    fun clear() = marks.clear()

    /** From the render-state extraction of every living body: the Paladin who marked this client's player looms. */
    fun loom(entity: LivingEntity, state: LivingEntityRenderState) {
        if (marks.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val me = minecraft.player ?: return
        val mark = marks.firstOrNull { it.paladin == entity.id && it.target == me.id } ?: return
        val age = mark.age + minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        state.scale *= 1f + LOOM * strength(age)
    }

    fun submit(context: LevelRenderContext) {
        if (marks.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos
        val dawns = ArrayList<Dawn>()
        val strokes = Strokes()
        for (mark in marks) {
            val age = mark.age + partial
            val shown = strength(age)
            val paladin = level.getEntity(mark.paladin)
            if (paladin != null && !Whips.firstPerson(paladin)) dawns += dawn(paladin, camera, age, shown, partial)
            val target = level.getEntity(mark.target) ?: continue
            shackles(strokes, target, mark.rgb, age, shown, partial)
            choosing(strokes, target, mark.rgb, age, partial)
        }
        if (dawns.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(DAWN)) { pose, buffer ->
                for (d in dawns) d.write(pose, buffer, camera)
            }
        }
        if (!strokes.isEmpty) strokes.submit(context)
    }

    /** Behind the Paladin from wherever the eye is, about their shoulders, turning and breathing. */
    private fun dawn(paladin: Entity, camera: Vec3, age: Float, shown: Float, partial: Float): Dawn {
        val centre0 = paladin.getPosition(partial).add(0.0, paladin.bbHeight * DAWN_HEIGHT, 0.0)
        val toEye = camera.subtract(centre0).normalize()
        val centre = centre0.subtract(toEye.scale(DAWN_BEHIND))
        val right = Vec3(0.0, 1.0, 0.0).cross(toEye).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val up = toEye.cross(right).normalize()
        val size = DAWN_SIZE * (0.6 + 0.4 * shown) * (1 + DAWN_BREATH * sin(age * 0.09))
        val turn = age * DAWN_SPIN
        fun corner(a: Double): Vec3 = centre.add(right.scale(cos(a) * size / 2 * Math.sqrt(2.0))).add(up.scale(sin(a) * size / 2 * Math.sqrt(2.0)))
        return Dawn(
            corner(turn + PI * 1.25), corner(turn + PI * 1.75), corner(turn + PI * 0.25), corner(turn + PI * 0.75),
            argbOf(0xFFFFFF, DAWN_ALPHA * shown), toEye,
        )
    }

    /** Two faint rings at the ankles, a hair apart, trembling. */
    private fun shackles(strokes: Strokes, target: Entity, rgb: Int, age: Float, shown: Float, partial: Float) {
        val feet = target.getPosition(partial)
        val pale = Strands.mix(rgb, 0xFFFFFF, 0.6f)
        for ((i, height) in SHACKLE_HEIGHTS.withIndex()) {
            val tremble = SHACKLE_TREMBLE * sin(age * 0.7 + i * 2.1)
            ring(strokes, feet.add(0.0, height + tremble, 0.0), target.bbWidth / 2.0 + SHACKLE_GAP, argbOf(pale, SHACKLE_ALPHA * shown * (0.7f + 0.3f * sin(age * 0.2f + i))))
        }
    }

    /** The moment of choosing: a ring of dawn running out from the target and thinning away. */
    private fun choosing(strokes: Strokes, target: Entity, rgb: Int, age: Float, partial: Float) {
        val t = age / CHOOSING_TICKS
        if (t >= 1f) return
        val out = 1 - (1 - t) * (1 - t)
        ring(strokes, target.getPosition(partial).add(0.0, 0.1, 0.0), 0.4 + CHOOSING_REACH * out, argbOf(Strands.mix(rgb, DAWN_RGB, 0.5f), 1 - t))
    }

    private fun ring(strokes: Strokes, centre: Vec3, radius: Double, argb: Int) {
        var last = centre.add(radius, 0.0, 0.0)
        for (i in 1..RING_STEPS) {
            val a = 2 * PI * i / RING_STEPS
            val next = centre.add(cos(a) * radius, 0.0, sin(a) * radius)
            strokes.hot(last, next, argb, PIXEL * 1.5)
            last = next
        }
    }

    /** Up over [APPEAR_TICKS], down over the last [VANISH_TICKS], eased both ways. */
    private fun strength(age: Float): Float {
        val up = (age / APPEAR_TICKS).coerceIn(0f, 1f)
        val down = ((LIFE - age) / VANISH_TICKS).coerceIn(0f, 1f)
        return (1 - (1 - up) * (1 - up)) * (1 - (1 - down) * (1 - down))
    }

    private class Dawn(val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3, val argb: Int, val normal: Vec3) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val us = floatArrayOf(0f, 1f, 1f, 0f)
            val vs = floatArrayOf(1f, 1f, 0f, 0f)
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

    private val DAWN = modId("textures/effect/punishment_dawn.png")
    private const val FULL_BRIGHT = 0xF000F0
    private const val DAWN_RGB = 0xFFB07A

    private const val APPEAR_TICKS = 20f
    private const val VANISH_TICKS = 20f

    /** How much bigger the Paladin stands in the marked one's eyes. */
    private const val LOOM = 0.9f

    private const val DAWN_HEIGHT = 0.7
    private const val DAWN_BEHIND = 0.45
    private const val DAWN_SIZE = 3.4
    private const val DAWN_BREATH = 0.05
    private const val DAWN_SPIN = 0.006
    private const val DAWN_ALPHA = 1f

    private val SHACKLE_HEIGHTS = doubleArrayOf(0.12, 0.2)
    private const val SHACKLE_GAP = 0.06
    private const val SHACKLE_TREMBLE = 0.015
    private const val SHACKLE_ALPHA = 0.55f

    private const val CHOOSING_TICKS = 16f
    private const val CHOOSING_REACH = 2.6
    private const val RING_STEPS = 32
}
