package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Lash.Shape
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The client half of the Judge's Verdict (`pathways/justiciar/abilities/Verdict.kt`): what each sentence looks like
 * once spoken.
 *
 * | sentence | what is seen |
 * |---|---|
 * | Exile | a ring of bent air running out from the Judge, dust thrown up off the ground under it |
 * | Imprison | transparent walls closing round the target layer by layer, a slow viscous liquid running down them |
 * | Confinement | a transparent cell standing up round the target, rippling wherever the target strikes it |
 * | Death | the Judge's afterimages along the dash, then a blow that bends the air at the target's head |
 * | Flog | no whip at all: at each stroke a cut opens across the body and the air there flinches |
 *
 * Walls and cuts are flat colour ([RenderTypes.textBackground], translucent, lit by themselves); bent air is the shared
 * [Refraction] material, its rims alone under a shader pack.
 */
internal object Verdicts {

    private class Standing(val kind: EdictKind, val entity: Int, val rgb: Int, val ticks: Int, val at: Vec3) {
        var age = 0
    }

    private class Line(val shape: Shape, val from: Int, val to: Int, val rgb: Int) {
        var age = 0

        /** Where the Judge was each tick of the dash, the oldest first. */
        val trail = ArrayList<Vec3>()
    }

    /** A ripple of a struck wall: where, which way the wall faces, how old. */
    private class Ripple(val at: Vec3, val normal: Vec3) {
        var age = 0
    }

    private val standing = mutableListOf<Standing>()
    private val lines = mutableListOf<Line>()
    private val ripples = mutableListOf<Ripple>()

    /** Whether any bent air is to be drawn this frame: the frame's colour must be copied for it. */
    val bending: Boolean get() = standing.any { it.kind == EdictKind.EXILE } || lines.isNotEmpty() || ripples.isNotEmpty()

    fun accept(payload: WorldEffectPayload.Edict) {
        val entity = Minecraft.getInstance().level?.getEntity(payload.entity) ?: return
        standing += Standing(payload.kind, payload.entity, payload.color and 0xFFFFFF, payload.ticks, entity.position())
    }

    fun accept(payload: WorldEffectPayload.Lash) {
        lines += Line(payload.shape, payload.from, payload.to, payload.color and 0xFFFFFF)
    }

    fun tick() {
        val level = Minecraft.getInstance().level ?: return clear()
        standing.removeAll { edict ->
            edict.age++
            when (edict.kind) {
                EdictKind.EXILE -> if (edict.age <= DUST_TICKS) dust(level, edict)
                EdictKind.CONFINE -> level.getEntity(edict.entity)?.let { struck(edict, it) }
                else -> Unit
            }
            edict.age >= edict.ticks
        }
        lines.removeAll { line ->
            line.age++
            if (line.shape == Shape.DASH && line.age <= Edict.DEATH_DASH_TICKS + 1) level.getEntity(line.from)?.let { line.trail += it.position() }
            if (line.shape == Shape.FLOG && line.age % Edict.FLOG_STROKE_TICKS == 1 && line.age < Edict.FLOG_STROKES * Edict.FLOG_STROKE_TICKS) {
                level.getEntity(line.to)?.let { tear(level, it) }
            }
            line.age >= line.shape.life
        }
        ripples.removeAll { ++it.age >= RIPPLE_TICKS }
    }

    fun clear() {
        standing.clear()
        lines.clear()
        ripples.clear()
    }

    fun submit(context: LevelRenderContext) {
        if (standing.isEmpty() && lines.isEmpty() && ripples.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos
        val strokes = Strokes()
        val plainAir = ShaderPacks.inUse || !Refraction.ready

        // Walls and cuts. Built here and only written in the callback: the callback runs later, when the frame is
        // drawn, so lines added to [strokes] from inside it would come after [strokes] was already submitted.
        val flat = Flat()
        for (edict in standing) {
            val age = edict.age + partial
            when (edict.kind) {
                EdictKind.IMPRISON -> level.getEntity(edict.entity)?.let { imprison(flat, strokes, edict, it, age, partial) }
                EdictKind.CONFINE -> confine(flat, strokes, edict, age, camera)
                else -> Unit
            }
        }
        for (line in lines) {
            if (line.shape != Shape.FLOG) continue
            val target = level.getEntity(line.to) ?: continue
            cuts(flat, strokes, camera, line, target, line.age + partial, partial)
        }
        if (flat.quads.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.textBackground()) { pose, buffer ->
                for (q in flat.quads) q.write(pose, buffer, camera)
            }
        }
        if (flat.runes.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(RUNES)) { pose, buffer ->
                for (r in flat.runes) r.write(pose, buffer, camera)
            }
        }

        // Afterimages of the dash.
        for (line in lines) {
            if (line.shape != Shape.DASH) continue
            val judge = level.getEntity(line.from) ?: continue
            afterimages(context, strokes, line, judge, line.age + partial, partial, camera)
        }

        // Bent air: the Exile's wave, the blow of Death, the whip's flinch, the cell's ripples.
        val discs = ArrayList<Disc>()
        for (edict in standing) if (edict.kind == EdictKind.EXILE) exileWave(discs, edict, level, edict.age + partial, partial)
        for (line in lines) airOf(discs, line, level, line.age + partial, partial)
        for (ripple in ripples) {
            val t = (ripple.age + partial) / RIPPLE_TICKS
            discs += Disc(ripple.at, RIPPLE_RADIUS, Refraction.Kind.WAVE, (1 - t) * 0.8f, easeOut(t))
        }
        if (!plainAir && discs.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), Refraction.TYPE) { pose, buffer ->
                for (d in discs) Refraction.disc(pose, buffer, camera, d.at, d.radius, d.kind, d.strength, AIR, d.front)
            }
        }
        if (discs.isNotEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning()) { pose, buffer ->
                for (d in discs) {
                    val ring = if (d.kind == Refraction.Kind.WAVE) d.radius * d.front else d.radius
                    val alpha = (if (plainAir) RIM_PLAIN else RIM_ALPHA) * d.strength
                    if (ring > 0.05 && alpha > 0.01f) Refraction.rimOnly(pose, buffer, camera, d.at, ring, argbOf(AIR, alpha))
                }
            }
        }
        if (!strokes.isEmpty) strokes.submit(context)
    }

    private class Disc(val at: Vec3, val radius: Double, val kind: Refraction.Kind, val strength: Float, val front: Float)

    // ---- Exile ----

    private fun exileWave(discs: MutableList<Disc>, edict: Standing, level: ClientLevel, age: Float, partial: Float) {
        val judge = level.getEntity(edict.entity)
        val centre = (judge?.getPosition(partial) ?: edict.at).add(0.0, (judge?.bbHeight ?: 1.8f) * 0.5, 0.0)
        val t = (age / Edict.EXILE_TICKS).coerceIn(0f, 1f)
        discs += Disc(centre, EXILE_REACH, Refraction.Kind.WAVE, 1 - t, easeOut(t))
        // A second, fainter ring a breath behind the first: the force comes as a blow, not a puff.
        val late = ((age - EXILE_ECHO) / Edict.EXILE_TICKS).coerceIn(0f, 1f)
        if (age > EXILE_ECHO) discs += Disc(centre, EXILE_REACH * 0.7, Refraction.Kind.WAVE, (1 - late) * 0.6f, easeOut(late))
    }

    /** Dust thrown off the ground under the running ring. */
    private fun dust(level: ClientLevel, edict: Standing) {
        val reach = EXILE_REACH * 0.8 * easeOut(edict.age.toFloat() / DUST_TICKS)
        val random = level.random
        repeat(DUST_PER_TICK) {
            val angle = random.nextDouble() * 2 * PI
            val x = edict.at.x + cos(angle) * reach
            val z = edict.at.z + sin(angle) * reach
            val ground = level.getBlockState(BlockPos.containing(x, edict.at.y - 0.5, z))
            if (ground.isAir) return@repeat
            level.addParticle(BlockParticleOption(ParticleTypes.BLOCK, ground), x, edict.at.y + 0.1, z, cos(angle) * 0.25, 0.2, sin(angle) * 0.25)
        }
    }

    // ---- Imprison ----

    /**
     * Each layer closes from half again its size onto its place in [Edict.IMPRISON_LAYER_TICKS], the innermost first;
     * the liquid runs down the faces slowly. At the end the layers let go, outermost first, swelling and fading.
     */
    private fun imprison(flat: Flat, strokes: Strokes, edict: Standing, body: Entity, age: Float, partial: Float) {
        val feet = body.getPosition(partial)
        for (layer in 0 until Edict.IMPRISON_LAYERS) {
            val since = age - layer * Edict.IMPRISON_LAYER_TICKS
            if (since < 0f) continue
            val shut = easeOut((since / CLOSE_TICKS).coerceAtMost(1f))
            val release = ((age - (edict.ticks - RELEASE_TICKS + (Edict.IMPRISON_LAYERS - 1 - layer) * 2)) / RELEASE_TICKS).coerceIn(0f, 1f)
            val grow = (1 + (1 - shut) * 0.6) * (1 + release * 0.25)
            val fade = (1 - release) * shut
            if (fade <= 0f) continue
            val half = (body.bbWidth / 2.0 + LAYER_GAP * (layer + 1)) * grow
            val top = (body.bbHeight + LAYER_GAP * (layer + 1)) * grow
            val box = Box(feet.add(-half, -0.02, -half), feet.add(half, top, half))
            box(flat, box, argbOf(WALL, WALL_ALPHA * fade))
            edges(strokes, box, argbOf(EDGE, EDGE_ALPHA * fade))
            // The liquid: streaks sliding down each face, slow and each its own pace.
            if (layer == 0 && shut >= 1f) liquid(strokes, box, edict.entity, age, fade)
        }
    }

    private fun liquid(strokes: Strokes, box: Box, seed: Int, age: Float, fade: Float) {
        val random = Random(seed * 31L)
        val height = box.max.y - box.min.y
        for (face in 0 until 4) {
            repeat(DRIPS_PER_FACE) {
                val along = 0.15 + random.nextDouble() * 0.7
                val speed = 0.01 + random.nextDouble() * 0.015
                val length = 0.25 + random.nextDouble() * 0.35
                val y = box.max.y - ((age * speed + random.nextDouble()) % 1.0) * (height + length)
                val p = box.onFace(face, along)
                val a = Vec3(p.x, (y + length).coerceAtMost(box.max.y), p.z)
                val b = Vec3(p.x, y.coerceAtLeast(box.min.y), p.z)
                if (a.y > b.y) strokes.cold(a, b, argbOf(LIQUID, LIQUID_ALPHA * fade), FULL_BRIGHT, PIXEL * 1.5)
            }
        }
    }

    // ---- Confinement ----

    /**
     * A dome of light round where the target stood, [DOME_RADIUS] across: it grows up out of the ground in a ring of
     * light over [FORM_TICKS], a belt of runes turning slowly round its lower half, its skin clear face-on and bright
     * at the rim (as a bubble is), and where the target strikes it light runs out across the skin from the blow.
     * At the end it thins away.
     */
    private fun confine(flat: Flat, strokes: Strokes, edict: Standing, age: Float, camera: Vec3) {
        val form = easeOut((age / FORM_TICKS).coerceAtMost(1f))
        val out = ((age - (edict.ticks - DOME_FADE_TICKS)) / DOME_FADE_TICKS).coerceIn(0f, 1f)
        val life = 1 - out * out
        if (life <= 0f) return
        val c = edict.at.add(0.0, -DOME_SINK, 0.0)
        val spin = age * DOME_SPIN
        fun at(theta: Double, phi: Double) = c.add(cos(phi) * cos(theta) * DOME_RADIUS, sin(phi) * DOME_RADIUS, cos(phi) * sin(theta) * DOME_RADIUS)
        // How much of row [f] (0 ground, 1 crown) has grown: a soft edge riding up as the dome forms.
        fun grown(f: Double): Float = ((form * (1 + FORM_EDGE) - f) / FORM_EDGE).toFloat().coerceIn(0f, 1f)
        fun skin(p: Vec3, f: Double): Int {
            val n = p.subtract(c).normalize()
            val facing = kotlin.math.abs(n.dot(camera.subtract(p).normalize()))
            val rim = ((1 - facing) * (1 - facing)).toFloat()
            val alpha = (SKIN_BASE + SKIN_RIM * rim + glowAt(p)) * grown(f) * life
            return argbOf(SKIN, alpha)
        }
        for (j in 0 until DOME_ROWS) {
            val f0 = j.toDouble() / DOME_ROWS
            val f1 = (j + 1.0) / DOME_ROWS
            if (grown(f0) <= 0f) break
            val phi0 = f0 * PI / 2
            val phi1 = f1 * PI / 2
            for (k in 0 until DOME_COLUMNS) {
                val t0 = 2 * PI * k / DOME_COLUMNS + spin
                val t1 = 2 * PI * (k + 1) / DOME_COLUMNS + spin
                val a = at(t0, phi0); val b = at(t1, phi0); val d = at(t0, phi1); val e = at(t1, phi1)
                flat.quads += Quad(a, b, e, d, 0, intArrayOf(skin(a, f0), skin(b, f0), skin(e, f1), skin(d, f1)))
                // The belt of runes, low on the dome, where it turns widest.
                if (f0 < BELT_TOP) {
                    val v0 = (f0 / BELT_TOP).toFloat()
                    val v1 = (f1 / BELT_TOP).toFloat().coerceAtMost(1f)
                    val pulse = 0.75f + 0.25f * sin(age * 0.15f + k * 0.7f)
                    val glow = (glowAt(a) + glowAt(e)) / 2
                    val alpha = (RUNE_ALPHA * pulse + glow) * grown(f0) * life
                    // The band goes round twice: once round, its pixels would come out twice as wide as they are tall.
                    val turn = DOME_COLUMNS / BELT_TURNS
                    flat.runes += Rune(a, b, e, d, (k % turn).toFloat() / turn, (k % turn + 1f) / turn, 1 - v0, 1 - v1, argbOf(RUNE, alpha))
                }
            }
        }
        // The ring where the dome stands on the ground, and while it forms, the ring of light growing up it.
        ring(strokes, c, DOME_RADIUS, 0.0, argbOf(RUNE, GROUND_RING_ALPHA * life))
        if (form < 1f) {
            val phi = form * PI / 2
            ring(strokes, c, DOME_RADIUS * cos(phi), DOME_RADIUS * sin(phi), argbOf(Strands.mix(RUNE, WHITE, 0.6f), FORMING_ALPHA * (1 - form)))
        }
    }

    private fun ring(strokes: Strokes, c: Vec3, radius: Double, height: Double, argb: Int) {
        if (radius < 0.05) return
        var last = c.add(radius, height, 0.0)
        for (i in 1..RING_STEPS) {
            val a = 2 * PI * i / RING_STEPS
            val next = c.add(cos(a) * radius, height, sin(a) * radius)
            strokes.hot(last, next, argb, PIXEL * 2)
            last = next
        }
    }

    /** Light running out across the dome's skin from where it was struck: a ring widening from the blow, dimming. */
    private fun glowAt(p: Vec3): Float {
        var glow = 0f
        for (ripple in ripples) {
            val t = ripple.age.toFloat() / RIPPLE_TICKS
            val front = RIPPLE_SPREAD * t
            val off = p.distanceTo(ripple.at) - front
            glow += RIPPLE_GLOW * (1 - t) * kotlin.math.exp(-(off * off) / RIPPLE_WIDTH).toFloat()
        }
        return glow
    }

    /** Where the confined body presses the dome, it ripples there: once per press, not every tick. */
    private fun struck(edict: Standing, body: Entity) {
        if (edict.age < FORM_TICKS) return
        val c = edict.at
        val out = body.position().subtract(c).multiply(1.0, 0.0, 1.0)
        if (out.length() < DOME_RADIUS - body.bbWidth / 2.0 - PRESS - 0.4) return
        if (ripples.any { it.age < RIPPLE_TICKS / 2 }) return
        val dir = out.normalize()
        val height = (body.y - c.y + body.bbHeight * 0.5).coerceIn(0.3, DOME_RADIUS * 0.8)
        ripples += Ripple(c.add(dir.scale(sqrt(DOME_RADIUS * DOME_RADIUS - height * height))).add(0.0, height, 0.0), dir)
    }

    // ---- Death ----

    /**
     * A pale double of the Judge at every place the dash passed through, and between them, each fading a little
     * after it was left; a streak of light along the path. In one's own first person the doubles are behind the eye.
     */
    private fun afterimages(context: LevelRenderContext, strokes: Strokes, line: Line, judge: Entity, age: Float, partial: Float, camera: Vec3) {
        val trail = line.trail
        if (trail.size < 2) return
        for (i in 0 until trail.size - 1) {
            for (half in 0..1) {
                val left = i + half * 0.5f
                // Each image fades [IMAGE_TICKS] after the Judge left it.
                if (age - left > IMAGE_TICKS) continue
                val at = trail[i].lerp(trail[i + 1], half * 0.5)
                if (at.distanceToSqr(judge.getPosition(partial)) < 0.36) continue
                Lashes.double(context, judge, at, partial, camera)
            }
            val fade = (1 - (age - i) / IMAGE_TICKS).coerceIn(0f, 1f)
            if (fade > 0f) {
                val lift = Vec3(0.0, judge.bbHeight * 0.55, 0.0)
                strokes.hot(trail[i].add(lift), trail[i + 1].add(lift), argbOf(Strands.mix(line.rgb, WHITE, 0.6f), 0.6f * fade), PIXEL * 2)
            }
        }
    }

    // ---- Flog ----

    /**
     * A cut across the body at each stroke, laid on the side the eye sees: a dark red gash with a pale edge, opening
     * along its length in a tick and closing to a scar as it fades. Each stroke its own angle.
     */
    private fun cuts(flat: Flat, strokes: Strokes, camera: Vec3, line: Line, body: Entity, age: Float, partial: Float) {
        val feet = body.getPosition(partial)
        val toEye = camera.subtract(feet).multiply(1.0, 0.0, 1.0).let { if (it.lengthSqr() < 1e-6) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        val right = Vec3(toEye.z, 0.0, -toEye.x)
        val face = feet.add(toEye.scale(body.bbWidth / 2.0 + 0.05))
        for (stroke in 0 until Edict.FLOG_STROKES) {
            val since = age - stroke * Edict.FLOG_STROKE_TICKS
            if (since < 0f) continue
            val fade = 1 - easeIn(((since - WELT_HOLD_TICKS) / WELT_FADE_TICKS).coerceIn(0f, 1f))
            if (fade <= 0f) continue
            // Each stroke its own sweep: across the body at a slant, bowed, alternating sides.
            val random = Random(line.from * 13L + line.to * 7L + stroke)
            val tilt = (0.3 + random.nextDouble() * 0.45) * (if (stroke % 2 == 0) 1 else -1)
            val centre = face.add(0.0, body.bbHeight * (0.4 + random.nextDouble() * 0.35), 0.0)
            val across = right.scale(cos(tilt)).add(0.0, sin(tilt), 0.0).scale(WELT_LENGTH / 2)
            val bow = Vec3(0.0, 1.0, 0.0).subtract(across.normalize().scale(across.normalize().y)).normalize().scale(WELT_BOW * (if (random.nextBoolean()) 1 else -1))
            val p0 = centre.subtract(across)
            val p1 = centre.add(bow)
            val p2 = centre.add(across)
            // The whip runs along the welt in [WELT_DRAW_TICKS], easing out: the head leads, the welt opens behind it.
            val drawn = easeOut((since / WELT_DRAW_TICKS).coerceAtMost(1f))
            val points = (0..WELT_STEPS).map { i ->
                val t = drawn * i / WELT_STEPS
                p0.scale((1 - t) * (1 - t).toDouble()).add(p1.scale(2.0 * t * (1 - t))).add(p2.scale((t * t).toDouble()))
            }
            // Hot at first, cooling into a red welt: white-gold, the pathway's gold, then crimson.
            val heat = (1 - since / WELT_COOL_TICKS).coerceIn(0f, 1f)
            val hue = if (heat > 0.5f) Strands.mix(line.rgb, WHITE, (heat - 0.5f) * 2) else Strands.mix(WELT, line.rgb, heat * 2)
            for (i in 0 until WELT_STEPS) {
                val s0 = i.toDouble() / WELT_STEPS * drawn
                val s1 = (i + 1.0) / WELT_STEPS * drawn
                // Thickest in the middle, to a point at both ends.
                val w0 = WELT_WIDTH * Math.pow(sin(PI * s0), 0.6)
                val w1 = WELT_WIDTH * Math.pow(sin(PI * s1), 0.6)
                strip(flat, camera, points[i], points[i + 1], w0, w1, argbOf(hue, fade))
                if (heat > 0f) strokes.hot(points[i], points[i + 1], argbOf(Strands.mix(line.rgb, WHITE, 0.5f), 0.5f * heat * fade), PIXEL * 5 * (w0 + w1) / (2 * WELT_WIDTH))
            }
            // The head of the stroke, while it runs: a spark.
            if (drawn < 1f) strokes.hot(points.last().add(0.0, -0.06, 0.0), points.last().add(0.0, 0.06, 0.0), argbOf(WHITE, 1f), PIXEL * 4)
        }
    }

    /** A strip from [a] to [b] turned to the eye, [w0] wide at [a] and [w1] at [b]. */
    private fun strip(flat: Flat, camera: Vec3, a: Vec3, b: Vec3, w0: Double, w1: Double, argb: Int) {
        val mid = a.add(b).scale(0.5)
        val side = b.subtract(a).cross(camera.subtract(mid)).let { if (it.lengthSqr() < 1e-10) return else it.normalize() }
        flat.quads += Quad(a.subtract(side.scale(w0 / 2)), b.subtract(side.scale(w1 / 2)), b.add(side.scale(w1 / 2)), a.add(side.scale(w0 / 2)), argb)
    }

    /** Cloth and blood torn loose at a stroke. */
    private fun tear(level: ClientLevel, body: Entity) {
        val random = level.random
        val bits = BlockParticleOption(ParticleTypes.BLOCK, Blocks.WOOL.red().defaultBlockState())
        repeat(TEAR_BITS) {
            level.addParticle(
                bits, body.x + (random.nextDouble() - 0.5) * body.bbWidth, body.y + body.bbHeight * (0.35 + random.nextDouble() * 0.4),
                body.z + (random.nextDouble() - 0.5) * body.bbWidth, (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2,
            )
        }
        level.addParticle(ParticleTypes.CRIT, body.x, body.y + body.bbHeight * 0.6, body.z, 0.0, 0.1, 0.0)
    }

    /** The air of the lines: the blow of Death at the head, the flinch of each stroke of the whip. */
    private fun airOf(discs: MutableList<Disc>, line: Line, level: ClientLevel, age: Float, partial: Float) {
        val target = level.getEntity(line.to) ?: return
        val head = target.getPosition(partial).add(0.0, target.bbHeight * 0.85, 0.0)
        when (line.shape) {
            Shape.DASH -> {
                val t = ((age - Edict.DEATH_DASH_TICKS) / BLOW_TICKS).coerceIn(0f, 1f)
                if (age >= Edict.DEATH_DASH_TICKS && t < 1f) {
                    discs += Disc(head, BLOW_RADIUS, Refraction.Kind.WAVE, 1 - t, easeOut(t))
                    discs += Disc(head, BLOW_RADIUS * 0.45, Refraction.Kind.LENS, (1 - t) * 0.8f, 1f)
                }
            }
            Shape.FLOG -> for (stroke in 0 until Edict.FLOG_STROKES) {
                val t = ((age - stroke * Edict.FLOG_STROKE_TICKS) / FLINCH_TICKS).coerceIn(0f, 1f)
                if (age >= stroke * Edict.FLOG_STROKE_TICKS && t < 1f) {
                    discs += Disc(target.getPosition(partial).add(0.0, target.bbHeight * 0.55, 0.0), FLINCH_RADIUS, Refraction.Kind.WAVE, 1 - t, easeOut(t))
                }
            }
            else -> Unit
        }
    }

    // ---- shapes ----

    private class Box(val min: Vec3, val max: Vec3) {
        /** A point at share [t] along the bottom edge of side [face] (0 north, 1 east, 2 south, 3 west). */
        fun onFace(face: Int, t: Double): Vec3 = when (face) {
            0 -> Vec3(min.x + (max.x - min.x) * t, min.y, min.z)
            1 -> Vec3(max.x, min.y, min.z + (max.z - min.z) * t)
            2 -> Vec3(max.x - (max.x - min.x) * t, min.y, max.z)
            else -> Vec3(min.x, min.y, max.z - (max.z - min.z) * t)
        }
    }

    /** The four walls and the roof of [box], both faces of each, in one flat colour. */
    private fun box(flat: Flat, box: Box, argb: Int) {
        for (face in 0 until 4) {
            val a = box.onFace(face, 0.0)
            val b = box.onFace(face, 1.0)
            flat.quads += Quad(a, b, b.withY(box.max.y), a.withY(box.max.y), argb)
        }
        val y = box.max.y
        flat.quads += Quad(Vec3(box.min.x, y, box.min.z), Vec3(box.max.x, y, box.min.z), Vec3(box.max.x, y, box.max.z), Vec3(box.min.x, y, box.max.z), argb)
    }

    private fun edges(strokes: Strokes, box: Box, argb: Int) {
        for (face in 0 until 4) {
            val a = box.onFace(face, 0.0)
            val b = box.onFace(face, 1.0)
            strokes.cold(a, b, argb, FULL_BRIGHT, PIXEL * 1.5)
            strokes.cold(a.withY(box.max.y), b.withY(box.max.y), argb, FULL_BRIGHT, PIXEL * 1.5)
            strokes.cold(a, a.withY(box.max.y), argb, FULL_BRIGHT, PIXEL * 1.5)
        }
    }

    /** A flat strip from [a] to [b], [width] across, turned to the eye. */
    private fun ribbon(flat: Flat, camera: Vec3, a: Vec3, b: Vec3, width: Double, argb: Int) {
        val mid = a.add(b).scale(0.5)
        val side = b.subtract(a).cross(camera.subtract(mid)).let { if (it.lengthSqr() < 1e-10) return else it.normalize().scale(width / 2) }
        flat.quads += Quad(a.subtract(side), b.subtract(side), b.add(side), a.add(side), argb)
    }

    /** Flat quads of one frame, gathered before the frame's geometry is written. */
    private class Flat {
        val quads = ArrayList<Quad>()
        val runes = ArrayList<Rune>()
    }

    /** A flat quad of colour, lit by itself, wound both ways so either face shows; [colors], if given, per corner. */
    private class Quad(val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3, val argb: Int, val colors: IntArray? = null) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            for (order in arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))) {
                for (i in order) {
                    val p = corners[i]
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                        .setColor(colors?.get(i) ?: argb).setLight(FULL_BRIGHT)
                }
            }
        }
    }

    /** A quad of the rune band ([u0]..[u1] round it, [v0] at [a]/[b], [v1] at [c]/[d]), both faces, lit by itself. */
    private class Rune(val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3, val u0: Float, val u1: Float, val v0: Float, val v1: Float, val argb: Int) {
        fun write(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3) {
            val corners = arrayOf(a, b, c, d)
            val us = floatArrayOf(u0, u1, u1, u0)
            val vs = floatArrayOf(v0, v0, v1, v1)
            for (order in arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 2, 1, 0))) {
                for (i in order) {
                    val p = corners[i]
                    buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat())
                        .setColor(argb).setUv(us[i], vs[i]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f)
                }
            }
        }
    }

    private fun Vec3.withY(y: Double) = Vec3(x, y, z)

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)
    private fun easeIn(t: Float): Float = t * t

    private const val WHITE = 0xFFFFFF
    private const val FULL_BRIGHT = 0xF000F0
    private const val AIR = 0xE8F0FF
    private const val RIM_ALPHA = 0.25f
    private const val RIM_PLAIN = 0.5f

    private const val EXILE_REACH = 8.0
    private const val EXILE_ECHO = 3f
    private const val DUST_TICKS = 6
    private const val DUST_PER_TICK = 10

    private const val WALL = 0xDCE8F5
    private const val EDGE = 0xF2D58A
    private const val LIQUID = 0xEAF4FF
    private const val WALL_ALPHA = 0.12f
    private const val EDGE_ALPHA = 0.7f
    private const val LIQUID_ALPHA = 0.45f
    private const val LAYER_GAP = 0.14
    private const val DRIPS_PER_FACE = 3
    private const val RELEASE_TICKS = 8

    private const val CLOSE_TICKS = 8f

    private val RUNES = me.vangoo.mysteries.modId("textures/effect/confinement_runes.png")
    private const val DOME_RADIUS = 5.0
    private const val DOME_SINK = 0.1
    private const val DOME_ROWS = 14
    private const val DOME_COLUMNS = 48
    private const val DOME_SPIN = 0.006
    private const val FORM_TICKS = 22f
    private const val FORM_EDGE = 0.2
    private const val DOME_FADE_TICKS = 20f
    private const val BELT_TOP = 0.29
    private const val BELT_TURNS = 2
    private const val SKIN = 0xF3E2B0
    private const val SKIN_BASE = 0.03f
    private const val SKIN_RIM = 0.35f
    private const val RUNE = 0xFFD04A
    private const val RUNE_ALPHA = 0.8f
    private const val GROUND_RING_ALPHA = 0.55f
    private const val FORMING_ALPHA = 0.9f
    private const val RING_STEPS = 64
    private const val RIPPLE_SPREAD = 3.0
    private const val RIPPLE_GLOW = 0.6f
    private const val RIPPLE_WIDTH = 0.35
    private const val PRESS = 0.05
    private const val RIPPLE_TICKS = 16
    private const val RIPPLE_RADIUS = 1.6

    private const val IMAGE_TICKS = 8f
    private const val BLOW_TICKS = 8f
    private const val BLOW_RADIUS = 1.8

    private const val WELT = 0x8E1A1A
    private const val WELT_LENGTH = 0.85
    private const val WELT_BOW = 0.12
    private const val WELT_WIDTH = 0.075
    private const val WELT_STEPS = 12
    private const val WELT_DRAW_TICKS = 3f
    private const val WELT_COOL_TICKS = 10f
    private const val WELT_HOLD_TICKS = 8f
    private const val WELT_FADE_TICKS = 14f
    private const val FLINCH_TICKS = 6f
    private const val FLINCH_RADIUS = 0.9
    private const val TEAR_BITS = 6
}
