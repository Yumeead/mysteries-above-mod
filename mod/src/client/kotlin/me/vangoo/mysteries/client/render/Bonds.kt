package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brand of Restraint's chains (`EdictKind.BONDS`) and the iron ring on a Beyonder's head (`EdictKind.CROWN`).
 *
 * Three chains break out of the ground round the victim one after another, each running up from its own
 * anchor and coiling round its own band of the body (ankles; belt and arms; chest). They coil loose and red-hot,
 * snap tight on [Edict.bondSnap] with a little overshoot (the server's ratchet lands on that tick), and cool
 * to iron lit by the place. Shackles close on the ankles with the first chain. While the term runs a glint
 * now and then travels a chain; at the end the links come apart and fall, fading.
 *
 * A chain is drawn link by link, face-on and edge-on by turns, each a quad turned to the eye about the chain.
 */
internal object Bonds {

    private class Bond(val entity: Int, val crown: Boolean, val rgb: Int, val ticks: Int) {
        var age = 0
    }

    private val bonds = mutableListOf<Bond>()

    fun accept(payload: WorldEffectPayload.Edict) {
        bonds += Bond(payload.entity, payload.kind == EdictKind.CROWN, payload.color and 0xFFFFFF, payload.ticks)
    }

    fun tick() {
        val level = Minecraft.getInstance().level
        bonds.removeAll { bond ->
            bond.age++
            if (level != null && !bond.crown) {
                for (chain in 0 until Edict.BOND_CHAINS) {
                    if (bond.age - 1 == start(chain)) level.getEntity(bond.entity)?.let { burst(level, anchor(it, chain, 1f)) }
                }
            }
            bond.age >= bond.ticks
        }
    }

    fun clear() = bonds.clear()

    fun submit(context: LevelRenderContext) {
        if (bonds.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = context.levelState().cameraRenderState.pos
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val live = bonds.mapNotNull { bond -> level.getEntity(bond.entity)?.let { bond to it } }
        if (live.isEmpty()) return
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE)) { pose, buffer ->
            for ((bond, entity) in live) {
                val age = bond.age + partial
                val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(entity.position().add(0.0, entity.bbHeight * 0.5, 0.0)))
                val draw = Draw(pose, buffer, camera, bond, age, light)
                if (bond.crown) crown(draw, entity, partial) else chains(draw, entity, partial)
            }
        }
    }

    private class Draw(val pose: PoseStack.Pose, val buffer: VertexConsumer, val camera: Vec3, val bond: Bond, val age: Float, val light: Int)

    private fun chains(draw: Draw, entity: Entity, partial: Float) {
        for (chain in 0 until Edict.BOND_CHAINS) {
            val grown = draw.age - start(chain)
            if (grown <= 0f) continue
            val path = path(entity, chain, partial, draw.age)
            val shown = path.length * easeOut((grown / Edict.BOND_REACH_TICKS).coerceAtMost(1f))
            val snap = draw.age - Edict.bondSnap(chain)
            links(draw, path, shown, chain, iron(snap))
        }
        // The shackles close with the first chain.
        val shut = draw.age - Edict.bondSnap(0)
        if (shut >= 0f) {
            val feet = entity.getPosition(partial)
            val close = 1 + SHACKLE_LOOSE * (1 - easeOut((shut / SNAP_TICKS).coerceAtMost(1f)))
            ring(draw, feet.add(0.0, SHACKLE_HEIGHT, 0.0), (entity.bbWidth / 2 + SHACKLE_GAP) * close, SHACKLE_BAND, iron(shut), 0)
        }
    }

    /** The ring on the head: drops from above the head onto the brow and closes, after the last chain. */
    private fun crown(draw: Draw, entity: Entity, partial: Float) {
        val shut = draw.age - Edict.CROWN_SNAP
        if (shut < -CROWN_DROP_TICKS) return
        val drop = if (shut < 0) -shut / CROWN_DROP_TICKS else 0f
        val top = entity.getPosition(partial).add(0.0, entity.bbHeight - CROWN_BELOW_TOP + CROWN_FALL * easeIn(drop), 0.0)
        val radius = (entity.bbWidth / 2 * CROWN_WIDTH) * (1 + SHACKLE_LOOSE * drop)
        ring(draw, top, radius, CROWN_BAND, iron(shut), 1)
    }

    /** Hot gold at first, then cooling to iron lit by the place: how far the iron has cooled, 0 → 1. */
    private fun iron(sinceSnap: Float): Float = ((sinceSnap - HOT_TICKS) / COOL_TICKS).coerceIn(0f, 1f)

    /** A chain's shape: up out of the ground from its anchor, then round and round its band of the body. */
    private class Path(val points: List<Vec3>, val lengths: DoubleArray) {
        val length: Double get() = lengths.last()

        /** The point [at] blocks along the chain, and the chain's direction there. */
        fun at(at: Double): Pair<Vec3, Vec3> {
            var i = 1
            while (i < lengths.size - 1 && lengths[i] < at) i++
            val a = points[i - 1]
            val b = points[i]
            val t = ((at - lengths[i - 1]) / (lengths[i] - lengths[i - 1]).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
            return a.lerp(b, t) to b.subtract(a)
        }
    }

    private fun path(entity: Entity, chain: Int, partial: Float, age: Float): Path {
        val feet = entity.getPosition(partial)
        val height = entity.bbHeight.toDouble()
        val (bottom, top) = BANDS[chain]
        // Loose while it coils, then snapped tight with a little overshoot: the body is squeezed.
        val snap = age - Edict.bondSnap(chain)
        val squeeze = when {
            snap < 0f -> 1f
            else -> 1 - easeOutBack((snap / SNAP_TICKS).coerceAtMost(1f))
        }
        val radius = entity.bbWidth / 2.0 + RING_GAP[chain] + LOOSE * squeeze
        val start = anchor(entity, chain, partial)
        val turn0 = ANCHOR_ANGLE[chain]

        val points = ArrayList<Vec3>(RISE_STEPS + COIL_STEPS + 1)
        val coilStart = feet.add(cos(turn0) * radius, height * bottom, sin(turn0) * radius)
        for (i in 0..RISE_STEPS) {
            // Out of the ground in an arc that leans in towards the body.
            val t = i.toDouble() / RISE_STEPS
            points += start.lerp(coilStart, t).add(0.0, sin(t * PI) * RISE_ARC, 0.0)
        }
        for (i in 1..COIL_STEPS) {
            val t = i.toDouble() / COIL_STEPS
            val angle = turn0 + t * TURNS[chain] * 2 * PI
            points += feet.add(cos(angle) * radius, height * (bottom + (top - bottom) * t), sin(angle) * radius)
        }
        val lengths = DoubleArray(points.size)
        for (i in 1 until points.size) lengths[i] = lengths[i - 1] + points[i].distanceTo(points[i - 1])
        return Path(points, lengths)
    }

    /** Where chain [chain] breaks out of the ground, beside the body. */
    private fun anchor(entity: Entity, chain: Int, partial: Float): Vec3 {
        val angle = ANCHOR_ANGLE[chain]
        val reach = entity.bbWidth / 2.0 + ANCHOR_REACH
        return entity.getPosition(partial).add(cos(angle) * reach, 0.0, sin(angle) * reach)
    }

    /** The links of [path] up to [shown] blocks from the ground: face-on and edge-on by turns. */
    private fun links(draw: Draw, path: Path, shown: Double, chain: Int, cooled: Float) {
        // Chest first, ankles last; the last is gone with the term.
        val breaking = draw.age - (draw.bond.ticks - Edict.BREAK_TICKS - (Edict.BOND_CHAINS - 1 - chain) * BREAK_STAGGER)
        val count = (shown / LINK_STEP).toInt()
        val glint = glint(draw.age, chain, count)
        val random = java.util.Random(draw.bond.entity * 131L + chain)
        for (i in 0 until count) {
            val (centre, along) = path.at(i * LINK_STEP + LINK_LENGTH / 2)
            val dir = along.normalize().scale(LINK_LENGTH / 2)
            var a = centre.subtract(dir)
            var b = centre.add(dir)
            var alpha = 1f
            val drift = Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5)
            if (breaking >= 0f) {
                // The links come apart and fall, each its own way.
                val fall = Vec3(0.0, -FALL * breaking * breaking, 0.0).add(drift.scale(breaking * DRIFT))
                a = a.add(fall)
                b = b.add(fall)
                alpha = 1 - breaking / Edict.BREAK_TICKS
            }
            val glinting = kotlin.math.abs(i - glint) <= 1
            quad(draw, a, b, LINK_WIDTH, i % 2, look(maxOf(1 - cooled, if (glinting) GLINT else 0f), alpha))
        }
    }

    /** Which link the glint is on (or far off the chain): it travels a chain every so often. */
    private fun glint(age: Float, chain: Int, count: Int): Int {
        val phase = (age + chain * GLINT_STAGGER) % GLINT_EVERY
        return if (phase < GLINT_TICKS) (phase / GLINT_TICKS * count).toInt() else -10
    }

    /** A band of iron round [centre]: eight sides, the shackle texture along them. */
    private fun ring(draw: Draw, centre: Vec3, radius: Double, band: Double, cooled: Float, salt: Int) {
        val breaking = draw.age - (draw.bond.ticks - Edict.BREAK_TICKS)
        val alpha = if (breaking >= 0f) 1 - breaking / Edict.BREAK_TICKS else 1f
        val fall = if (breaking >= 0f) -FALL * breaking * breaking else 0.0
        val look = look(1 - cooled, alpha)
        for (side in 0 until RING_SIDES) {
            val a0 = 2 * PI * side / RING_SIDES + salt
            val a1 = 2 * PI * (side + 1) / RING_SIDES + salt
            val p = centre.add(cos(a0) * radius, fall, sin(a0) * radius)
            val q = centre.add(cos(a1) * radius, fall, sin(a1) * radius)
            val up = Vec3(0.0, band / 2, 0.0)
            val normal = p.add(q).scale(0.5).subtract(centre).normalize()
            val u0 = u(BAND + side % 2, look.hot)
            val argb = argbOf(look.rgb, look.alpha * nearFade(p, draw.camera))
            corner(draw, p.add(up), u0, 0f, argb, look.hot, normal)
            corner(draw, p.subtract(up), u0, 1f, argb, look.hot, normal)
            corner(draw, q.subtract(up), u0 + CELL, 1f, argb, look.hot, normal)
            corner(draw, q.add(up), u0 + CELL, 0f, argb, look.hot, normal)
        }
    }

    /** How a piece of iron looks: [hot] is the red-hot texture at full light, the rest is iron lit by the place. */
    private class Look(val rgb: Int, val alpha: Float, val hot: Boolean)

    /**
     * Iron at [heat] (1 white-hot, 0 cold): the hot texture darkening towards dull red as it cools, then plain iron.
     * A glint is a little heat passing along cold iron.
     */
    private fun look(heat: Float, alpha: Float): Look =
        if (heat > 0f) Look(Strands.mix(COOLING, 0xFFFFFF, heat), alpha, true) else Look(0xFFFFFF, alpha, false)

    /** The left edge of texture cell [cell] (8 pixels each), in the iron half or the hot half of the sheet. */
    private fun u(cell: Int, hot: Boolean): Float = (cell + if (hot) HOT_CELLS else 0) * CELL

    /** One link: a quad from [a] to [b], turned to the eye about its own axis, textured with link cell [cell]. */
    private fun quad(draw: Draw, a: Vec3, b: Vec3, width: Double, cell: Int, look: Look) {
        val mid = a.add(b).scale(0.5)
        val toEye = draw.camera.subtract(mid)
        val side = b.subtract(a).cross(toEye).let { if (it.lengthSqr() < 1e-10) return else it.normalize().scale(width / 2) }
        val normal = toEye.normalize()
        val u0 = u(cell, look.hot)
        val argb = argbOf(look.rgb, look.alpha * nearFade(mid, draw.camera))
        corner(draw, a.add(side), u0, 0f, argb, look.hot, normal)
        corner(draw, a.subtract(side), u0, 1f, argb, look.hot, normal)
        corner(draw, b.subtract(side), u0 + CELL, 1f, argb, look.hot, normal)
        corner(draw, b.add(side), u0 + CELL, 0f, argb, look.hot, normal)
    }

    private fun corner(draw: Draw, at: Vec3, u: Float, v: Float, argb: Int, hot: Boolean, normal: Vec3) {
        val camera = draw.camera
        draw.buffer.addVertex(draw.pose, (at.x - camera.x).toFloat(), (at.y - camera.y).toFloat(), (at.z - camera.z).toFloat())
            .setColor(argb)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(if (hot) FULL_BRIGHT else draw.light)
            .setNormal(draw.pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    /** The ground breaking where a chain comes out of it. */
    private fun burst(level: ClientLevel, at: Vec3) {
        val ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z))
        if (ground.isAir) return
        val random = level.random
        repeat(DUST) {
            level.addParticle(
                BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z,
                (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3,
            )
        }
    }

    private fun start(chain: Int): Int = chain * Edict.BOND_STAGGER_TICKS

    /** The victim seeing their own bonds in first person: what passes by the eye melts away. */
    private fun nearFade(at: Vec3, camera: Vec3): Float = ((at.distanceTo(camera) - 0.3) / 0.4).toFloat().coerceIn(0f, 1f)

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)
    private fun easeIn(t: Float): Float = t * t
    private fun easeOutBack(t: Float): Float {
        val x = t - 1
        return 1 + 2.7f * x * x * x + 1.7f * x * x
    }

    private val TEXTURE = modId("textures/effect/bonds.png")
    private const val FULL_BRIGHT = 0xF000F0

    /** The band of the body each chain coils round, as shares of its height, and how many times. */
    private val BANDS = listOf(0.04 to 0.24, 0.40 to 0.60, 0.62 to 0.80)
    private val TURNS = doubleArrayOf(1.6, 1.3, 1.1)
    private val RING_GAP = doubleArrayOf(0.06, 0.16, 0.1)
    private val ANCHOR_ANGLE = doubleArrayOf(0.4, 0.4 + 2 * PI / 3, 0.4 + 4 * PI / 3)
    private const val ANCHOR_REACH = 0.9
    private const val RISE_STEPS = 8
    private const val COIL_STEPS = 40
    private const val RISE_ARC = 0.35

    /** How far out a chain coils before it snaps tight, and the ticks the snap takes. */
    private const val LOOSE = 0.35
    private const val SNAP_TICKS = 3f

    /** Links: 32 pixels a block, an 8 × 8 cell, overlapping by two pixels so they interlock. */
    private const val LINK_LENGTH = 0.25
    private const val LINK_WIDTH = 0.25
    private const val LINK_STEP = 0.19

    private const val SHACKLE_HEIGHT = 0.12
    private const val SHACKLE_GAP = 0.04
    private const val SHACKLE_BAND = 0.18
    private const val SHACKLE_LOOSE = 0.5
    private const val RING_SIDES = 8

    private const val CROWN_BELOW_TOP = 0.2
    private const val CROWN_WIDTH = 1.1
    private const val CROWN_BAND = 0.14
    private const val CROWN_FALL = 0.8
    private const val CROWN_DROP_TICKS = 3f

    /** Ticks the iron stays hot after its snap, and the ticks it takes to cool. */
    private const val HOT_TICKS = 6f
    private const val COOL_TICKS = 14f

    /** How hot a passing glint makes the cold iron, as a share of full heat; and the red that hot iron cools to. */
    private const val GLINT = 0.45f
    private const val COOLING = 0x5A2A1C

    /** The sheet: 64 × 8, cells of 8 pixels (link face-on, link edge-on, two of band), then the same four red-hot. */
    private const val CELL = 0.125f
    private const val BAND = 2
    private const val HOT_CELLS = 4

    private const val GLINT_EVERY = 50f
    private const val GLINT_TICKS = 8f
    private const val GLINT_STAGGER = 17

    private const val BREAK_STAGGER = 2
    private const val FALL = 0.02
    private const val DRIFT = 0.03
    private const val DUST = 10
}
