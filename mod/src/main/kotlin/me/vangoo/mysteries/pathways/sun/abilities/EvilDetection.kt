package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Sun, Seq. 8 (Light Seeker): **Evil Detection**.
 *
 * The unclean nearby glow through walls. The passive asks no permission and has no switch: the
 * Seeker sees what they see.
 *
 * ## The outline is a vanilla effect, not a ledger of our own
 *
 * The most direct way is `setGlowingTag(true)`, but it **has no term**: having switched the
 * outline on, we would have to remember on whom, so as to switch it off when the target leaves
 * the radius. And there is nowhere to remember: there is one ability instance for the whole
 * server, so a field in it would mix up the targets of every Seeker at once.
 *
 * The vanilla [MobEffects.GLOWING] removes that need entirely: it **expires by itself**. The tick
 * refreshes it for [GLOW_TICKS], i.e. a little longer than its own period, and a target that has
 * left the radius fades in a second and a half with not a line of bookkeeping.
 *
 * ## The price of transparency
 *
 * Everyone sees the outline, not only the Seeker. That is deliberate: light does not show
 * **you**, it **marks** — and whoever is marked has the right to notice. The plugin dragged in
 * the `glowingentities` library here for a private outline; the mod drags in nothing.
 */
class EvilDetection :
    PermanentPassiveAbility(AbilityIdentity.of("sun_evil_detection")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Виявлення зла")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Темні шляхи й нежить поруч світяться крізь стіни.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Контур бачать усі — світло позначає, а не підказує вам на вухо",
    )

    override fun tick(context: AbilityContext) {
        context.targeting.livingNear(range(context.sequence))
            .filter { it !== context.caster && it.isProfane(context.beyonders) }
            .forEach { profane ->
                profane.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, false))
            }
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private companion object {
        const val BASE_RANGE = 20
        /** One and a half ticks' worth: the outline does not blink between scans, nor does it outlive leaving the radius. */
        const val GLOW_TICKS = 30
    }
}
