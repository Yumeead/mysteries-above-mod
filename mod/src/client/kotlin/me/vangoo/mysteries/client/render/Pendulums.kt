package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Pendulum.Swing
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Seer's spirit pendulum: an iron chain hanging from the hand, leaning and swinging towards
 * what it seeks — harder the nearer that is. Everyone near sees it sway; only the Seer knows what
 * it looks for. It trembles when the mark is a Sequence above, hangs dead when the mark is out of
 * reach, and spins before it snaps when the mark is far above or shielded.
 *
 * The server sends a beat every few ticks (where the mark is now); the swing itself is this client's.
 * Drawn as pixel lines in plain translucency — a dark chain cannot be drawn in light.
 */
object Pendulums {

    private class Chain(var yaw: Float, var strength: Float, var swing: Swing, var left: Int, val born: Int)

    /** By the holder's entity id: a new beat renews the chain rather than hanging a second one. */
    private val chains = HashMap<Int, Chain>()
    private var clock = 0

    fun accept(payload: WorldEffectPayload.Pendulum) {
        val chain = chains[payload.holder]
        if (chain == null || payload.swing == Swing.SNAP && chain.swing != Swing.SNAP) {
            chains[payload.holder] = Chain(payload.yaw, payload.strength, payload.swing, payload.ticks, clock)
            return
        }
        chain.yaw = payload.yaw
        chain.strength = payload.strength
        chain.swing = payload.swing
        chain.left = payload.ticks
    }

    fun tick() {
        clock++
        chains.values.removeIf { --it.left < 0 }
    }

    fun clear() = chains.clear()

    fun submit(context: LevelRenderContext) {
        if (chains.isEmpty()) return
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val hanging = chains.mapNotNull { (id, chain) -> (level.getEntity(id) as? LivingEntity)?.let { it to chain } }
        if (hanging.isEmpty()) return
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
            for ((holder, chain) in hanging) {
                val anchor = anchorOf(holder, partial, client)
                val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(anchor))
                draw(pose, buffer, camera, anchor, chain, clock + partial, light)
            }
        }
    }

    /**
     * Where the chain hangs from: in the hand. Seen from one's own eyes in the first person, ahead and
     * to the lower right of the view, where a held thing is; otherwise beside the body's right side.
     */
    private fun anchorOf(holder: LivingEntity, partial: Float, client: Minecraft): Vec3 {
        if (holder === client.cameraEntity && client.options.cameraType == CameraType.FIRST_PERSON) {
            val look = holder.getViewVector(partial)
            val right = look.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }
            return holder.getEyePosition(partial).add(look.scale(FIRST_AHEAD)).add(right.scale(FIRST_RIGHT)).add(0.0, FIRST_DROP, 0.0)
        }
        val yaw = Math.toRadians(Mth.rotLerp(partial, holder.yBodyRotO, holder.yBodyRot).toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val right = Vec3(-cos(yaw), 0.0, -sin(yaw))
        return holder.getPosition(partial).add(0.0, HAND_HEIGHT, 0.0).add(right.scale(HAND_SIDE)).add(forward.scale(HAND_AHEAD))
    }

    private fun draw(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, anchor: Vec3, chain: Chain, t: Float, light: Int) {
        val age = t - chain.born
        val yaw = Math.toRadians(chain.yaw.toDouble())
        var toward = Vec3(-sin(yaw), 0.0, cos(yaw))
        var theta = when (chain.swing) {
            Swing.SEEK -> LEAN * chain.strength + (BASE_SWING + SWING * chain.strength) * sin(t * RATE)
            // Half the lean, and a tremble over it: the way is only half there.
            Swing.FOG -> LEAN * 0.5 * chain.strength + BASE_SWING * sin(t * RATE) + TREMBLE * sin(t * 1.7) * cos(t * 2.3)
            Swing.STILL -> 0.0
            // Spinning ever wider, round and round, until it snaps.
            Swing.SNAP -> {
                val spin = (age * SPIN).toDouble()
                toward = Vec3(-sin(spin), 0.0, cos(spin))
                SPIN_LEAN * (age / SPIN_UP).coerceAtMost(1f)
            }
        }
        if (chain.swing == Swing.FOG) toward = toward.yRot((WOBBLE * sin(t * 0.9)).toFloat())
        val hang = toward.scale(sin(theta)).add(0.0, -cos(theta), 0.0)
        val ink = argbOf(CHAIN_DARK, 1f)
        var last = anchor
        for (link in 1..LINKS) {
            val next = anchor.add(hang.scale(LENGTH * link / LINKS))
            pixelLine(pose, buffer, camera, last, next, if (link % 2 == 0) ink else argbOf(CHAIN_LIGHT, 1f), light = light)
            last = next
        }
        // The weight at the end: a short bar across, two pixels thick.
        val across = toward.cross(UP).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }.scale(BOB / 2)
        pixelLine(pose, buffer, camera, last.subtract(across), last.add(across), ink, width = PIXEL * 2, light = light)
    }

    private val UP = Vec3(0.0, 1.0, 0.0)

    private const val LINKS = 8
    private const val LENGTH = 0.5
    private const val BOB = 0.12
    private const val CHAIN_DARK = 0x3A3D44
    private const val CHAIN_LIGHT = 0x6E737D

    /** How far it leans towards the mark at full strength, and how it swings (radians, per tick). */
    private const val LEAN = 0.45
    private const val BASE_SWING = 0.12
    private const val SWING = 0.3
    private const val RATE = 0.35f
    private const val TREMBLE = 0.12
    private const val WOBBLE = 0.35
    private const val SPIN = 0.9f
    private const val SPIN_LEAN = 1.1
    private const val SPIN_UP = 10f

    private const val HAND_HEIGHT = 0.8
    private const val HAND_SIDE = 0.38
    private const val HAND_AHEAD = 0.3
    /** In the first person: left of the held item, which is drawn over the world and would hide it. */
    private const val FIRST_AHEAD = 0.85
    private const val FIRST_RIGHT = 0.12
    private const val FIRST_DROP = -0.1
}
