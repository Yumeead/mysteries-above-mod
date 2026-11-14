package me.vangoo.mysteries.ability.context

import me.vangoo.domain.valueobjects.RgbColor
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * The vocabulary of shapes: what is seen when an ability fires.
 *
 * ## Why this is a service after all, despite the rule about facades
 *
 * The phase 03 rule — "a method gets into the context only if it adds something beyond a single
 * vanilla call". Shapes pass it, and not narrowly:
 *
 * - they **last**, i.e. they must schedule themselves — and the scheduler exists only in the mod;
 * - they know the **caster's pathway colour** ([pathwayColor]), so an ability need not drag
 *   `PathwayBranding` and its own pathway into every call;
 * - they are **reused**: in the plugin 20 shapes accounted for 869 call sites, and that is
 *   exactly why the shapes lived in one place there rather than in ability classes.
 *
 * A puff of particles at the moment of a cast does **not** belong here — that is one
 * `sendParticles`, and it stays at the call site. The boundary runs along duration and reuse,
 * not along the word "visual".
 *
 * ## Why this is not a port of `IVisualEffectsContext`
 *
 * In the plugin there were 14 methods here (`playCircleEffect`, `playVortexEffect`,
 * `playCubeEffect`…), and the list was dictated by what Spigot could draw at all: a circle and
 * a spiral had to be *approximated* with a scatter of droplets. Half of that vocabulary was
 * ground decals, which the mod dropped deliberately. So the vocabulary is assembled **from the
 * effects that were written**, not from someone else's list: a shape gets here when a second
 * ability asks for it.
 */
interface Effects {

    /** The caster's pathway colour. The default for any of their shapes; `null` in an argument means exactly it. */
    val pathwayColor: RgbColor

    /**
     * An aura on [target]'s body: strands rising from the feet to the shoulders and travelling
     * with the target. It schedules itself and fades after [durationTicks] — no session needed.
     *
     * [counterFlow] is an optional particle that falls downwards while the strands go up (for
     * water it is spray). Without a counter-flow the aura reads as rotation.
     */
    fun bodyAura(
        target: LivingEntity,
        durationTicks: Int,
        color: RgbColor? = null,
        counterFlow: ParticleOptions? = null,
    )

    /**
     * A line of light from [from] to [to]: it holds for [durationTicks], then fades on its own.
     * [width] 0 is a thin ray, 1 a pillar a block across. It is a solid shaft of light in hazy air,
     * white-hot at its heart and soft at its edges, and it keeps its colour against a bright sky.
     * It reaches [to] `BEAM_REACH_TICKS` after the call, and lights the air near where it lands.
     * [sourceWidth] is its width at [from], evenly in between: a shaft out of the sun is the sun's breadth up there.
     */
    fun beam(
        level: ServerLevel,
        from: Vec3,
        to: Vec3,
        durationTicks: Int = BEAM_TICKS,
        width: Float = 0f,
        color: RgbColor? = null,
        sourceWidth: Float = width,
    )

    /**
     * Real light at [at] for [durationTicks] — a light block, so the place is lit for everyone. It
     * dims before it goes out. Only air is lit: if [at] is taken, the block above is tried.
     */
    fun flare(level: ServerLevel, at: BlockPos, durationTicks: Int, brightness: Int = FULL_BRIGHTNESS)

    /** A flash that whitens the screens of those within [radius] who look at [at] — by up to [strength]. */
    fun flash(level: ServerLevel, at: Vec3, radius: Double, strength: Float = 1f, color: RgbColor? = null)

    companion object {
        const val BEAM_TICKS = 6
        const val FULL_BRIGHTNESS = 15
    }
}
