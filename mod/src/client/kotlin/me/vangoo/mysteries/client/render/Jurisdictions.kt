package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Jurisdiction
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.IronBarsBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The client half of the Sheriff's Area of Jurisdiction (`pathways/justiciar/abilities/AreaOfJurisdiction.kt`).
 *
 * The bound is a square of iron posts, a block of bars each, every [POST_STEP] blocks, with a rune of light turning over
 * each and a sagging cord of light from rune to rune. None of it stands all the time: a territory is as wide as a town
 * and lasts until it is replaced, and a fence round it would be noise.
 *
 * - **The Sheriff's own** ([Jurisdiction.Kind.OWN]): a wave of light runs over the ground from their feet as it is laid,
 *   runes coming up under it and going out behind; where it reaches the edge the posts rise, stand a moment and sink.
 *   After that a post rises only near the Sheriff, out of the ground, and sinks again as they walk away. Crossing the
 *   bound they hear the law close behind them (higher going in, lower going out), and light runs along the edge both
 *   ways from where they crossed.
 * - **Someone walking in** ([Jurisdiction.Kind.REVEAL]): the same wave, from where they stepped in, and nothing after.
 *
 * Nothing appears at once and nothing goes out at once.
 */
internal object Jurisdictions {

    /** A post at the block ([x], [z]) on the edge; [axis] is the bars' run: along the edge, or a pillar at a corner. */
    private class Post(val x: Int, val z: Int, val axis: Axis) {
        val centre = Vec3(x + 0.5, 0.0, z + 0.5)
        var ground = Double.NaN
        var near = 0f
        var nearBefore = 0f
    }

    private enum class Axis { X, Z, PILLAR }

    /** Ground heights over the square, filled as the wave asks for them: a wave touches each column once or twice. */
    private class Heights(val centre: BlockPos, val half: Int) {
        private val side = 2 * half + 1
        private val cells = IntArray(side * side) { UNKNOWN }

        fun at(level: ClientLevel, x: Int, z: Int): Int? {
            val i = x - (centre.x - half)
            val k = z - (centre.z - half)
            if (i !in 0 until side || k !in 0 until side) return null
            val cached = cells[k * side + i]
            if (cached != UNKNOWN) return cached
            // An unloaded column has no heightmap and answers sea level: asked again once it loads.
            if (!level.hasChunkAt(x, z)) return null
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z).also { cells[k * side + i] = it }
        }
    }

    /** A wave of light over the square from [Jurisdiction.from]; the posts rise where it meets the edge. */
    private class Wave(val law: Jurisdiction, val posts: List<Post>, val heights: Heights) {
        var age = 0
        val fromX = floor(law.from.x).toInt()
        val fromZ = floor(law.from.z).toInt()

        /** Ticks until the front reaches [x], [z]. */
        fun arrival(x: Int, z: Int): Float = max(abs(x - fromX), abs(z - fromZ)) / Jurisdiction.WAVE_SPEED

        val life: Int = run {
            val far = law.half + max(abs(fromX - law.centre.x), abs(fromZ - law.centre.z))
            (far / Jurisdiction.WAVE_SPEED + TRAIL / Jurisdiction.WAVE_SPEED + POST_RISE + POST_HOLD + POST_FALL).toInt() + 1
        }
    }

    private class Territory(val law: Jurisdiction) {
        val posts = posts(law.centre, law.half)
        var age = 0
        var cleared = -1
        var inside: Boolean? = null
        var pulse: Pulse? = null

        fun contains(p: Vec3): Boolean =
            abs(p.x - (law.centre.x + 0.5)) <= law.half + 0.5 && abs(p.z - (law.centre.z + 0.5)) <= law.half + 0.5
    }

    /** Light running both ways along the edge from where the Sheriff crossed it. */
    private class Pulse(val at: Vec3, val alongZ: Boolean) {
        var age = 0
    }

    private val territories = mutableListOf<Territory>()
    private val waves = mutableListOf<Wave>()

    fun accept(payload: Jurisdiction) {
        val minecraft = Minecraft.getInstance()
        when (payload.kind) {
            Jurisdiction.Kind.OWN -> {
                val territory = Territory(payload)
                territories += territory
                waves += Wave(payload, territory.posts, Heights(payload.centre, payload.half))
            }
            Jurisdiction.Kind.REVEAL -> {
                waves += Wave(payload, posts(payload.centre, payload.half), Heights(payload.centre, payload.half))
                // The ground answers the step: a low chime, heard by the one who stepped in alone.
                minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.9f))
            }
            Jurisdiction.Kind.CLEAR -> territories.filter { it.law.id == payload.id && it.cleared < 0 }.forEach { it.cleared = it.age }
        }
    }

    fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = minecraft.player
        waves.removeAll { ++it.age >= it.life }
        territories.removeAll { territory ->
            territory.age++
            if (territory.cleared >= 0 && territory.age - territory.cleared >= CLEAR_TICKS) return@removeAll true
            if (level == null || player == null) return@removeAll false
            val feet = player.position()
            for (post in territory.posts) {
                post.nearBefore = post.near
                val distance = horizontal(feet, post.centre)
                val wanted = if (territory.cleared >= 0) 0f else smooth(((VIEW_FAR - distance) / (VIEW_FAR - VIEW_NEAR)).toFloat())
                post.near += (wanted - post.near) * NEAR_EASE
                if (post.near < 0.002f && wanted == 0f) post.near = 0f
                // The ground under a post, looked up from where the Sheriff is: in a cave it is the cave's floor.
                if (wanted > 0f) post.ground = groundNear(level, post.x, post.z, player.blockY) ?: post.ground
                if (post.near > 0.6f && level.random.nextFloat() < MOTE_CHANCE) {
                    level.addParticle(
                        ModParticles.mote(RgbColor(territory.law.color and 0xFFFFFF)),
                        post.x + 0.5 + (level.random.nextDouble() - 0.5) * 0.6, post.ground + 0.2, post.z + 0.5 + (level.random.nextDouble() - 0.5) * 0.6,
                        0.0, 0.02, 0.0,
                    )
                }
            }
            // Crossing the bound: the law closes or opens behind the Sheriff, and light runs along the edge.
            val inside = territory.contains(feet)
            val was = territory.inside
            territory.inside = inside
            if (was != null && was != inside && territory.cleared < 0) {
                minecraft.soundManager.play(SimpleSoundInstance.forUI(ModSounds.RATCHET, if (inside) 1.25f else 0.8f, 0.55f))
                territory.pulse = crossing(territory, feet)
            }
            territory.pulse?.let { if (++it.age >= PULSE_TICKS) territory.pulse = null }
            false
        }
    }

    fun clear() {
        territories.clear()
        waves.clear()
        // Leaving a world may come before a resource reload: the models are looked up afresh.
        parts.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (territories.isEmpty() && waves.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos

        // Everything is laid out here; the callbacks below only write it (they run later, when the frame is drawn).
        val runes = ArrayList<Quad>()
        val films = ArrayList<Quad>()
        val strokes = Strokes()
        val shown = ArrayList<Shown>()

        for (wave in waves) ground(level, wave, wave.age + partial, camera, runes, films)

        // Each post at the most any wave or the Sheriff's nearness raises it.
        val raised = HashMap<Post, Float>()
        val glowing = HashMap<Post, Float>()
        val colours = HashMap<Post, Int>()
        for (wave in waves) {
            val age = wave.age + partial
            for (post in wave.posts) {
                val rise = waveRise(age - wave.arrival(post.x, post.z))
                if (rise > (raised[post] ?: 0f)) {
                    raised[post] = rise
                    colours[post] = wave.law.color
                    if (post.ground.isNaN()) wave.heights.at(level, post.x, post.z)?.let { post.ground = it.toDouble() }
                }
            }
        }
        for (territory in territories) {
            val gone = if (territory.cleared < 0) 1f else 1 - ((territory.age - territory.cleared + partial) / CLEAR_TICKS).coerceIn(0f, 1f)
            val pulse = territory.pulse
            for (post in territory.posts) {
                val near = (post.nearBefore + (post.near - post.nearBefore) * partial) * gone
                val rise = max(near, (raised[post] ?: 0f) * gone)
                if (rise <= 0f) continue
                raised[post] = rise
                colours[post] = territory.law.color
                if (pulse != null) {
                    val age = pulse.age + partial
                    val reach = horizontal(post.centre, pulse.at)
                    glowing[post] = max(0f, 1 - abs(reach.toFloat() - age * PULSE_SPEED) / PULSE_WIDTH) * (1 - age / PULSE_TICKS)
                }
            }
            if (pulse != null) {
                val age = pulse.age + partial
                val reach = min(age * PULSE_SPEED, PULSE_REACH).toDouble()
                val fade = 1 - age / PULSE_TICKS
                val along = if (pulse.alongZ) Vec3(0.0, 0.0, reach) else Vec3(reach, 0.0, 0.0)
                val colour = argbOf(Strands.mix(territory.law.color, WHITE, 0.5f), fade * PULSE_ALPHA)
                strokes.hot(pulse.at, pulse.at.add(along), colour, PIXEL * 2)
                strokes.hot(pulse.at, pulse.at.subtract(along), colour, PIXEL * 2)
            }
        }

        // The posts, their runes and the cord between neighbours, in the order they stand round the square.
        val bob = (level.gameTime % 24000L) + partial
        for (post in raised.keys) {
            val rise = raised[post] ?: continue
            if (post.ground.isNaN() || horizontal(post.centre, camera) > DRAW_REACH) continue
            val sink = (1 - easeOut(rise)) * (POST_HEIGHT + SINK_EXTRA)
            val base = post.ground - sink
            shown += Shown(post, base)
            val flare = glowing[post] ?: 0f
            val top = Vec3(post.x + 0.5, base + POST_HEIGHT + RUNE_ABOVE + RUNE_BOB * sin(bob * 0.07 + post.x * 0.9 + post.z * 1.3), post.z + 0.5)
            val size = RUNE_SIZE * rise * (1 + 0.5f * flare)
            val colour = Strands.mix(colours[post] ?: RUNE, WHITE, 0.1f + 0.6f * flare)
            runes += billboard(top, camera, size, rune(post.x, post.z), argbOf(colour, rise.coerceAtMost(1f)))
            // Three small runes circling the bars as a ward, unhurried, each at its own height.
            val spin = bob * ORBIT_SPEED + (post.x + post.z) * 0.7
            for (k in 0 until ORBIT_RUNES) {
                val angle = spin + k * 2 * Math.PI / ORBIT_RUNES
                val at = Vec3(
                    post.x + 0.5 + kotlin.math.cos(angle) * ORBIT_RADIUS,
                    base + ORBIT_LOW + k * ORBIT_STEP + 0.12 * sin(angle * 2),
                    post.z + 0.5 + sin(angle) * ORBIT_RADIUS,
                )
                runes += billboard(at, camera, ORBIT_SIZE * rise, rune(post.x + k, post.z - k), argbOf(colour, rise * ORBIT_ALPHA))
            }
        }
        for (territory in territories) cords(territory.posts, raised, shown, strokes, bob, territory.law.color)
        for (wave in waves) if (wave.law.kind == Jurisdiction.Kind.REVEAL) cords(wave.posts, raised, shown, strokes, bob, wave.law.color)

        val collector = context.submitNodeCollector()
        val poses = context.poseStack()
        for (s in shown) {
            val post = s.post
            for (storey in 0 until POST_HEIGHT.toInt()) {
                val y = s.base + storey
                val pos = BlockPos(post.x, floor(post.ground).toInt() + storey, post.z)
                poses.pushPose()
                poses.translate(post.x - camera.x, y - camera.y, post.z - camera.z)
                collector.submitBlockModel(
                    poses, Sheets.cutoutBlockItemSheet(), parts(post.axis, storey), BlockModelRenderState.EMPTY_TINTS,
                    LightCoordsUtil.getLightCoords(level, pos), OverlayTexture.NO_OVERLAY, 0,
                )
                poses.popPose()
            }
        }
        if (films.isNotEmpty()) {
            collector.submitCustomGeometry(poses, RenderTypes.textBackground()) { pose, buffer -> for (q in films) q.writePlain(pose, buffer, camera) }
        }
        if (runes.isNotEmpty()) {
            collector.submitCustomGeometry(poses, RenderTypes.entityTranslucentEmissive(RUNES)) { pose, buffer -> for (q in runes) q.write(pose, buffer, camera) }
        }
        if (!strokes.isEmpty) strokes.submit(context)
    }

    private class Shown(val post: Post, val base: Double)

    /**
     * The wave over the ground: a film of light on every column the front has just passed, runes coming up in some of
     * them, and a low veil standing on the front itself. All of it fades behind the front over [TRAIL] blocks.
     */
    private fun ground(level: ClientLevel, wave: Wave, age: Float, camera: Vec3, runes: MutableList<Quad>, films: MutableList<Quad>) {
        val law = wave.law
        val front = age * Jurisdiction.WAVE_SPEED
        val fade = 1 - ((age - (wave.life - POST_FALL)) / POST_FALL).coerceIn(0f, 1f)
        val first = max(0, floor(front - TRAIL).toInt())
        val last = floor(front).toInt()
        val film = Strands.mix(law.color, WHITE, 0.55f)
        val camX = floor(camera.x).toInt()
        val camZ = floor(camera.z).toInt()
        for (ring in first..last) {
            val behind = front - ring
            val strength = (1 - behind / TRAIL).coerceIn(0f, 1f).let { it * it } * fade
            if (strength <= 0.01f) continue
            forRing(wave.fromX, wave.fromZ, ring) { x, z ->
                if (abs(x - law.centre.x) > law.half || abs(z - law.centre.z) > law.half) return@forRing
                if (abs(x - camX) > GROUND_REACH || abs(z - camZ) > GROUND_REACH) return@forRing
                val top = wave.heights.at(level, x, z) ?: return@forRing
                val y = top + LIFT
                films += Quad.flat(x, y, z, argbOf(film, strength * FILM_ALPHA))
                // A sigil of the law in one cell of every few, its pixels the size of the block's.
                if (hash(x, z) % RUNE_EVERY == 0) {
                    runes += Quad.flatRune(x, y + 0.005, z, RUNE_CELLS, rune(x, z), argbOf(Strands.mix(law.color, WHITE, 0.2f), strength))
                }
                // The front itself: a low veil on the cell's far side, away from where the wave set out.
                if (ring == last && ring > 0) {
                    val dx = x - wave.fromX
                    val dz = z - wave.fromZ
                    val bottom = argbOf(film, strength * VEIL_ALPHA)
                    if (abs(dx) == ring) {
                        val fx = if (dx > 0) x + 1.0 else x.toDouble()
                        films += Quad.veil(Vec3(fx, y, z.toDouble()), Vec3(fx, y, z + 1.0), VEIL_HEIGHT, bottom)
                    }
                    if (abs(dz) == ring) {
                        val fz = if (dz > 0) z + 1.0 else z.toDouble()
                        films += Quad.veil(Vec3(x.toDouble(), y, fz), Vec3(x + 1.0, y, fz), VEIL_HEIGHT, bottom)
                    }
                }
            }
        }
    }

    /** Cords of light between neighbouring raised posts, sagging, a brighter glint running along them. */
    private fun cords(posts: List<Post>, raised: Map<Post, Float>, shown: List<Shown>, strokes: Strokes, clock: Float, color: Int) {
        if (posts.size < 2) return
        val bases = shown.associate { it.post to it.base }
        for (i in posts.indices) {
            val a = posts[i]
            val b = posts[(i + 1) % posts.size]
            val ra = raised[a] ?: continue
            val rb = raised[b] ?: continue
            val baseA = bases[a] ?: continue
            val baseB = bases[b] ?: continue
            val strength = min(ra, rb)
            if (strength < 0.3f) continue
            val from = Vec3(a.x + 0.5, baseA + POST_HEIGHT + RUNE_ABOVE - 0.15, a.z + 0.5)
            val to = Vec3(b.x + 0.5, baseB + POST_HEIGHT + RUNE_ABOVE - 0.15, b.z + 0.5)
            val sag = from.distanceTo(to) * CORD_SAG
            var last = from
            for (k in 1..CORD_STEPS) {
                val t = k.toDouble() / CORD_STEPS
                val next = from.add(to.subtract(from).scale(t)).add(0.0, -sag * 4 * t * (1 - t), 0.0)
                // A glint that runs along every cord the same way round the square.
                val glint = max(0f, 1 - abs(((clock * CORD_GLINT_SPEED) % 1f) - t.toFloat()) * 6)
                val alpha = (strength - 0.3f) / 0.7f * (CORD_ALPHA + 0.35f * glint)
                // Plain translucency lit by itself, not added light: added gold burns white against a day sky.
                strokes.cold(last, next, argbOf(Strands.mix(color, WHITE, 0.1f + 0.6f * glint), alpha), FULL_BRIGHT, PIXEL * 1.5)
                last = next
            }
        }
    }

    /** Where on the edge the Sheriff crossed it: the nearer side of the square, at their feet. */
    private fun crossing(territory: Territory, feet: Vec3): Pulse {
        val cx = territory.law.centre.x + 0.5
        val cz = territory.law.centre.z + 0.5
        val bound = territory.law.half + 0.5
        val offX = bound - abs(feet.x - cx)
        val offZ = bound - abs(feet.z - cz)
        return if (abs(offX) < abs(offZ)) {
            Pulse(Vec3(cx + bound * sign(feet.x - cx), feet.y + LIFT, feet.z), alongZ = true)
        } else {
            Pulse(Vec3(feet.x, feet.y + LIFT, cz + bound * sign(feet.z - cz)), alongZ = false)
        }
    }

    /** The posts round the square, in order: every [POST_STEP] blocks from each corner, the corners pillars. */
    private fun posts(centre: BlockPos, half: Int): List<Post> {
        val x0 = centre.x - half
        val x1 = centre.x + half
        val z0 = centre.z - half
        val z1 = centre.z + half
        val out = ArrayList<Post>()
        fun side(fromX: Int, fromZ: Int, stepX: Int, stepZ: Int, axis: Axis) {
            var i = 0
            while (i < 2 * half) {
                out += Post(fromX + stepX * i, fromZ + stepZ * i, if (i == 0) Axis.PILLAR else axis)
                i += POST_STEP
            }
        }
        if (half == 0) return listOf(Post(centre.x, centre.z, Axis.PILLAR))
        side(x0, z0, 1, 0, Axis.X)
        side(x1, z0, 0, 1, Axis.Z)
        side(x1, z1, -1, 0, Axis.X)
        side(x0, z1, 0, -1, Axis.Z)
        return out
    }

    /** The top of the ground at [x], [z], searched from a little above [fromY] down: a cave's floor in a cave. */
    private fun groundNear(level: ClientLevel, x: Int, z: Int, fromY: Int): Double? {
        val pos = BlockPos.MutableBlockPos()
        for (y in fromY + GROUND_UP downTo fromY - GROUND_DOWN) {
            pos.set(x, y, z)
            val state = level.getBlockState(pos)
            if (state.getCollisionShape(level, pos).isEmpty) continue
            pos.set(x, y + 1, z)
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty) continue
            return y + 1.0
        }
        return null
    }

    /** All the cells at Chebyshev distance [ring] from ([cx], [cz]). */
    private inline fun forRing(cx: Int, cz: Int, ring: Int, cell: (Int, Int) -> Unit) {
        if (ring == 0) {
            cell(cx, cz)
            return
        }
        for (d in -ring..ring) {
            cell(cx + d, cz - ring)
            cell(cx + d, cz + ring)
        }
        for (d in -ring + 1 until ring) {
            cell(cx - ring, cz + d)
            cell(cx + ring, cz + d)
        }
    }

    /** How far a post stands out of the ground [t] ticks after the wave reached it. */
    private fun waveRise(t: Float): Float = when {
        t < 0f -> 0f
        t < POST_RISE -> smooth(t / POST_RISE)
        t < POST_RISE + POST_HOLD -> 1f
        else -> 1 - smooth((t - POST_RISE - POST_HOLD) / POST_FALL)
    }

    // ── Models ──────────────────────────────────────────────────────────────────

    private val parts = HashMap<Pair<Axis, Int>, List<BlockStateModelPart>>()

    /**
     * The bars of one storey of a post. A model handed in by hand is not culled against its neighbours, so the faces
     * between the two storeys are left out: drawn, they show as a seam across the post.
     */
    private fun parts(axis: Axis, storey: Int): List<BlockStateModelPart> = parts.getOrPut(axis to storey) {
        val state: BlockState = Blocks.IRON_BARS.defaultBlockState().let {
            when (axis) {
                Axis.X -> it.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
                Axis.Z -> it.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true)
                Axis.PILLAR -> it
            }
        }
        val raw = ArrayList<BlockStateModelPart>()
        Minecraft.getInstance().modelManager.blockStateModelSet.get(state).collectParts(RandomSource.create(0), raw)
        val hidden = buildSet {
            if (storey + 1 < POST_HEIGHT.toInt()) add(Direction.UP)
            if (storey > 0) add(Direction.DOWN)
        }
        raw.map { part ->
            object : BlockStateModelPart by part {
                override fun getQuads(side: Direction?) = if (side in hidden) emptyList() else part.getQuads(side)
            }
        }
    }

    // ── Geometry ────────────────────────────────────────────────────────────────

    /** A quad, both faces unless flat on the ground; textured (runes) or plain light (films and veils). */
    private class Quad(
        val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3,
        val u0: Float, val u1: Float, val v0: Float, val v1: Float,
        val argbBottom: Int, val argbTop: Int, val normal: Vec3, val twoSided: Boolean,
    ) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val us = floatArrayOf(u0, u1, u1, u0)
            val vs = floatArrayOf(v0, v0, v1, v1)
            for (order in orders()) for (i in order) {
                val p = corners[i]
                buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                    .setColor(argbBottom).setUv(us[i], vs[i]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT).setNormal(pose, normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            }
        }

        fun writePlain(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val colours = intArrayOf(argbBottom, argbBottom, argbTop, argbTop)
            for (order in orders()) for (i in order) {
                val p = corners[i]
                buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                    .setColor(colours[i]).setLight(FULL_BRIGHT)
            }
        }

        private fun orders() = if (twoSided) BOTH else UP_ONLY

        companion object {
            private val BOTH = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))
            private val UP_ONLY = arrayOf(intArrayOf(3, 2, 1, 0))

            /** A cell of the ground, lit from within. */
            fun flat(x: Int, y: Double, z: Int, argb: Int) = Quad(
                Vec3(x.toDouble(), y, z.toDouble()), Vec3(x + 1.0, y, z.toDouble()), Vec3(x + 1.0, y, z + 1.0), Vec3(x.toDouble(), y, z + 1.0),
                0f, 1f, 0f, 1f, argb, argb, UP, twoSided = false,
            )

            /** Rune [index] lying on the ground from the cell, [cells] blocks across. */
            fun flatRune(x: Int, y: Double, z: Int, cells: Int, index: Int, argb: Int) = Quad(
                Vec3(x.toDouble(), y, z.toDouble()), Vec3(x.toDouble() + cells, y, z.toDouble()),
                Vec3(x.toDouble() + cells, y, z.toDouble() + cells), Vec3(x.toDouble(), y, z.toDouble() + cells),
                index / RUNE_COUNT.toFloat(), (index + 1) / RUNE_COUNT.toFloat(), 0f, 1f, argb, argb, UP, twoSided = false,
            )

            /** A band of light standing on [a]-[b], [height] high, [bottom] at the foot and nothing at the top. */
            fun veil(a: Vec3, b: Vec3, height: Double, bottom: Int) = Quad(
                a, b, b.add(0.0, height, 0.0), a.add(0.0, height, 0.0),
                0f, 1f, 0f, 1f, bottom, bottom and 0xFFFFFF, UP, twoSided = true,
            )
        }
    }

    /** Rune [index], [size] across, at [centre], turned to the eye. */
    private fun billboard(centre: Vec3, camera: Vec3, size: Double, index: Int, argb: Int): Quad {
        val toEye = camera.subtract(centre).normalize()
        val right = UP.cross(toEye).let { if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize() }.scale(size / 2)
        val up = toEye.cross(right).normalize().scale(size / 2)
        return Quad(
            centre.subtract(right).subtract(up), centre.add(right).subtract(up), centre.add(right).add(up), centre.subtract(right).add(up),
            index / RUNE_COUNT.toFloat(), (index + 1) / RUNE_COUNT.toFloat(), 1f, 0f, argb, argb, toEye, twoSided = true,
        )
    }

    private fun rune(x: Int, z: Int): Int = (hash(x, z) ushr 3) % RUNE_COUNT
    private fun hash(x: Int, z: Int): Int = ((x * 73856093) xor (z * 19349663)) and Int.MAX_VALUE
    private fun horizontal(a: Vec3, b: Vec3): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
    private fun sign(v: Double): Double = if (v < 0) -1.0 else 1.0
    private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    private fun easeOut(t: Float): Float = t.coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) }

    private val RUNES = modId("textures/effect/jurisdiction_sigils.png")
    private const val RUNE_COUNT = 8
    private val UP = Vec3(0.0, 1.0, 0.0)
    private const val WHITE = 0xFFFFFF
    private const val RUNE = 0xFFD04A
    private const val FULL_BRIGHT = 0xF000F0
    private const val UNKNOWN = Int.MIN_VALUE

    private const val POST_STEP = 4
    private const val POST_HEIGHT = 2.0
    private const val SINK_EXTRA = 0.3
    private const val POST_RISE = 10f
    private const val POST_HOLD = 30f
    private const val POST_FALL = 20f
    private const val RUNE_SIZE = 1.0
    private const val ORBIT_RUNES = 3
    private const val ORBIT_RADIUS = 0.75
    private const val ORBIT_LOW = 0.4
    private const val ORBIT_STEP = 0.5
    private const val ORBIT_SIZE = 0.5
    private const val ORBIT_ALPHA = 0.85f
    private const val ORBIT_SPEED = 0.035
    private const val RUNE_ABOVE = 0.55
    private const val RUNE_BOB = 0.08

    /** The Sheriff's nearness: posts stand fully nearer than this, and are gone past [VIEW_FAR]. */
    private const val VIEW_NEAR = 5.0
    private const val VIEW_FAR = 10.0
    private const val NEAR_EASE = 0.18f
    private const val GROUND_UP = 6
    private const val GROUND_DOWN = 16
    private const val MOTE_CHANCE = 0.06f

    private const val TRAIL = 6f
    private const val LIFT = 0.02
    private const val FILM_ALPHA = 0.16f
    private const val VEIL_ALPHA = 0.7f
    private const val VEIL_HEIGHT = 1.6
    private const val RUNE_EVERY = 5
    private const val RUNE_CELLS = 1
    private const val GROUND_REACH = 72
    private const val DRAW_REACH = 96.0

    private const val CORD_STEPS = 8
    private const val CORD_SAG = 0.06
    private const val CORD_ALPHA = 0.5f
    private const val CORD_GLINT_SPEED = 0.012f

    private const val PULSE_TICKS = 24f
    private const val PULSE_SPEED = 1.2f
    private const val PULSE_REACH = 10f
    private const val PULSE_WIDTH = 2.5f
    private const val PULSE_ALPHA = 0.85f
    private const val CLEAR_TICKS = 20
}
