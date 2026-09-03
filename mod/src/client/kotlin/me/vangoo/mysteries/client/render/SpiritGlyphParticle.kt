package me.vangoo.mysteries.client.render

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.Camera
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3

/**
 * The spirit glyph: the same texture as in [SpiritMoteParticle], but **steady** — and, if it
 * was placed on a player, one that **travels with them**.
 *
 * ## Why a spark will not do for a shape
 *
 * The spirit mote is deliberately alive: a random lifetime (16–28 ticks), a random size, a
 * flare at the start, a fade at the end, negative gravity. A scatter, an aura and a trail need
 * that — they are supposed to shimmer. A steady shape is hurt by it: a halo made of motes read
 * as a rising cloud.
 *
 * ## The constructor mixes in the spread itself — so a different one is used
 *
 * The vanilla `Particle(level, x, y, z, xd, yd, zd)` **does not take the velocity as given**: it
 * mixes in a random spread and renormalises the result. Beside it is
 * `SingleQuadParticle(level, x, y, z, sprite)` — the same parent **without** velocity and
 * without spread. The glyph is built through that one.
 *
 * ## The main point: the glyph latches onto its owner
 *
 * A mote is static **in the world**: once placed, it stays where it was placed. A shape above
 * the head inevitably lags behind because of that, and neither of the two obvious cures worked:
 *
 * | attempt | why it failed |
 * |---|---|
 * | a shorter lifetime plus redrawing every tick | the trail got shorter but stayed |
 * | giving the mote the player's velocity | the packet arrives with a delay, so the ring simply settled steadily behind the head |
 *
 * Both treated the consequence. The cause is that the shape **belongs to an entity**, not to a
 * point in the world — so the glyph holds the entity: every tick it puts itself where the owner
 * is **now**.
 *
 * ## The offset arrives in the packet rather than being derived from the position
 *
 * And even that is not enough if the offset is derived from the place of birth: the server sends
 * a coordinate, and while the packet is in flight the player moves, so the glyph latches with an
 * offset shifted by exactly the distance covered in that time. Every generation latches with its
 * own, and instead of a ring one gets a handful of rings trailing behind — the same trail, only
 * shorter.
 *
 * So the offset is **passed explicitly**, in the packet's velocity field: in the `count = 0`
 * form the three deltas reach the client unchanged, and there is nothing to move the glyph with
 * anyway — it does not fly. After that the position in the packet is needed for exactly one
 * thing: finding the owner.
 *
 * The result is a shape rigidly latched to its owner: network delay does not affect it at all,
 * because nothing in it is expressed in world coordinates.
 *
 * ponytail: the owner is found as the **nearest player** within [LATCH_RANGE] blocks — this is
 * unambiguous for a shape wrapping the head (a halo has a radius of 0.28) and costs zero new
 * packets. If a shape latching onto an arbitrary entity from a distance is ever needed, its
 * identifier will have to travel in an options type of our own: `ColorParticleOption` has no
 * room for it.
 */
class SpiritGlyphParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    /** The offset from the owner's crown. It arrived in the packet — it is not derived from the position. */
    private val offset: Vec3,
    options: ColorParticleOption,
    sprite: TextureAtlasSprite,
    // The constructor **without** velocity: that is the one that mixes in no spread.
) : SingleQuadParticle(level, x, y, z, sprite) {

    /**
     * Whom to hold. `null` — the glyph was not placed on a player, so it simply stands still.
     *
     * The distance is measured to the **crown**, not with `Entity.distanceToSqr`, which counts
     * from the **feet**. That is what the first revision of the latching broke on: a halo hangs
     * ~2.1 blocks above the feet, i.e. beyond any sensible threshold, so the owner was never
     * found — the glyph silently stayed static, and the trail went nowhere.
     */
    private val owner: AbstractClientPlayer? = level.players()
        // A zero offset means "the shape stands in the world", not "on the crown": those are
        // placed with the `count = 1` form and zero deltas. Without this check a door drawn a
        // step away from the player would silently latch onto their head and travel with them —
        // and it would look not like a bug but like "the effect is somehow in the wrong place".
        .takeIf { offset != Vec3.ZERO }
        ?.minByOrNull { headOf(it).distanceToSqr(x, y, z) }
        ?.takeIf { headOf(it).distanceToSqr(x, y, z) <= LATCH_RANGE * LATCH_RANGE }

    init {
        setColor(options.red, options.green, options.blue)
        lifetime = LIFETIME
        gravity = 0f
        friction = 1f
        hasPhysics = false
        quadSize = SIZE
        alpha = 1f
        pin(0f)
    }

    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT

    /** Glows on its own: the shape of another's power must not darken in the shade. */
    override fun getLightCoords(partialTick: Float): Int = LightCoordsUtil.FULL_BRIGHT

    /** Age only: the glyph's place is computed at render time, not here. */
    override fun tick() {
        if (age++ >= lifetime || owner?.isRemoved == true) remove()
    }

    /**
     * The glyph's place is computed **at render time**, not on the tick.
     *
     * This was the residual trail. The owner was found, the offset arrived exact, the glyph
     * honestly put itself on the tick where the owner was **in that tick** — but particles tick
     * separately from the player's movement, so the glyph lagged steadily by exactly one tick.
     * At a speed of 0.2 blocks/tick and two generations, that gave the short streak no cure on
     * the tick side could take away.
     *
     * Here the glyph takes the interpolated crown with the same [partialTick] the player's model
     * itself is rendered with, and sets `xo == x` so the render's lerp does not drag it back.
     * After that the shape moves **frame for frame** with its owner: there is nothing left to lag
     * behind.
     */
    override fun extract(state: QuadParticleRenderState, camera: Camera, partialTick: Float) {
        pin(partialTick)
        super.extract(state, camera, partialTick)
    }

    /** Puts the glyph on the owner's interpolated crown. With no owner — leaves it where it is. */
    private fun pin(partialTick: Float) {
        val rider = owner ?: return
        val t = partialTick.toDouble()
        x = Mth.lerp(t, rider.xOld, rider.x) + offset.x
        y = Mth.lerp(t, rider.yOld, rider.y) + rider.bbHeight + offset.y
        z = Mth.lerp(t, rider.zOld, rider.z) + offset.z
        // Without this the renderer would lerp from the previous place — that is, backwards again.
        xo = x
        yo = y
        zo = z
    }

    class Provider(private val sprites: SpriteSet) : ParticleProvider<ColorParticleOption> {
        override fun createParticle(
            options: ColorParticleOption,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            xd: Double,
            yd: Double,
            zd: Double,
            random: RandomSource,
            // The deltas in this packet form are not a velocity but the **offset from the crown**:
            // the glyph flies nowhere, so the field is free, and the offset must arrive exact.
        ): Particle = SpiritGlyphParticle(level, x, y, z, Vec3(xd, yd, zd), options, sprites.get(random))
    }

    private companion object {
        /**
         * The owner's crown — the point the latching distance is measured from.
         *
         * `Entity.distanceToSqr` counts from the **feet**, and that is what the first revision of
         * the latching broke on: a halo hangs ~2.1 blocks higher, i.e. beyond any sensible
         * threshold, so the owner was never found.
         */
        fun headOf(player: AbstractClientPlayer): Vec3 =
            Vec3(player.x, player.y + player.bbHeight, player.z)

        /**
         * The glyph latches onto its owner, so a long lifetime no longer hurts: there is no trail
         * any more. Four ticks — two generations when redrawing once every two ticks, so the ring
         * does not blink at the seam.
         */
        const val LIFETIME = 4

        /** Steady and a little smaller than a spark at its peak: the form comes from the ring, not the blob. */
        const val SIZE = 0.075f

        /** A metre from the crown: a shape on the head, not a neighbour who happened to walk past. */
        const val LATCH_RANGE = 1.0

    }
}
