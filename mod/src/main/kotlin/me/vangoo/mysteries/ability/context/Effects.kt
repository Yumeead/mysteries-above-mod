package me.vangoo.mysteries.ability.context

import me.vangoo.domain.valueobjects.RgbColor
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.entity.LivingEntity

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
}
