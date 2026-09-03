package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.roundToInt

/**
 * WhiteTower, Seq. 9 (Reader): **Enhanced Mind**.
 *
 * A Reader cannot look without reading. Take a closer look (sneak) — and whatever the gaze fell
 * on tells about itself: how much health and armour it has, whether it is a Beyonder and of which
 * pathway, how much hardness this stone has and whether it glows.
 *
 * ## Why this replaces `EnhancedMentalAttributes` (547 lines)
 *
 * The plugin's passive was a bag of everything: an experience multiplier, a chance to improve an
 * enchantment, duplicating a potion at a brewing stand, a "treasure sense", an "analysis", a
 * "trace". Six mechanics, none of them visible, and four separate ticks for them — 5, 10, 40 and
 * 600 ticks. The player saw nothing but numbers that occasionally got better.
 *
 * The Reader has **one** mechanic, and it shows exactly what a Reader is: they know more than
 * others about whatever they are looking at. Knowing someone's pathway and Sequence is no trifle
 * but the most valuable intelligence in a game where power depends on both.
 *
 * One item survived the bag, and precisely the one expressible in the game in a single line: the
 * vanilla [Attributes.LUCK] — "the attentive find better", and it works in every loot table in
 * the game with no handler at all.
 *
 * ## Why a tick rather than an event
 *
 * A gaze does not "arrive" anywhere: an event "the player looked at something" does not exist in
 * the game and cannot. This is the same case as `ResistanceToHorror` — a clock fits here. But it
 * only ticks while sneaking, so in ordinary play it costs one comparison twice a second.
 */
class EnhancedMind :
    PermanentPassiveAbility(AbilityIdentity.of("whitetower_enhanced_mind")),
    Ticking {

    /** Twice a second: the line above the hotbar hangs longer, so more often is unnecessary. */
    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Загострений розум")

    private val luckModifier = modifierId(identity.id, "luck")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви не вмієте просто дивитись — ви читаєте.\n" +
            "▪ Присядьте, і ціль під прицілом розповість про себе: життя, броню, шлях і " +
            "Послідовність, якщо перед вами Потойбічний\n" +
            "▪ Удача: +${luck(sequence)} (уважний знаходить краще)",
    )

    override fun onActivate(context: AbilityContext) {
        context.caster.grantAttribute(Attributes.LUCK, luckModifier, luck(context.sequence).toDouble())
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.LUCK, luckModifier)
    }

    override fun tick(context: AbilityContext) {
        // Reading is the player's choice rather than a background: otherwise the line above the
        // hotbar is occupied at all times.
        if (!context.caster.isShiftKeyDown) return
        val note = read(context) ?: return
        context.messaging.actionBar(context.caster, note)
    }

    /** A living target first: the stone behind it is less interesting than it is. */
    private fun read(context: AbilityContext): Component? =
        context.targeting.lookingAt(RANGE)?.let { creature(context, it) } ?: block(context)

    private fun creature(context: AbilityContext, target: LivingEntity): Component {
        val vitals = "${target.name.string} · ❤ ${target.health.roundToInt()}/${target.maxHealth.roundToInt()}" +
            if (target.armorValue > 0) " · броня ${target.armorValue}" else ""

        val beyonder = context.beyonders.of(target) ?: return Component.literal(vitals)
        val sequenceName = beyonder.pathway.sequenceName(beyonder.sequenceLevel)
        return Component.literal("$vitals · ${beyonder.pathway.name} ${beyonder.sequenceLevel} ($sequenceName)")
    }

    private fun block(context: AbilityContext): Component? {
        val hit = context.caster.pick(RANGE, 1.0f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null

        val pos = hit.blockPos
        val state = context.level.getBlockState(pos)
        val hardness = state.getDestroySpeed(context.level, pos)
        val light = state.lightEmission

        return Component.literal(
            state.block.name.string +
                " · міцність ${if (hardness < 0) "нескінченна" else "%.1f".format(hardness)}" +
                if (light > 0) " · світло $light" else "",
        )
    }

    private fun luck(sequence: Sequence): Int =
        scale(BASE_LUCK, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val RANGE = 24.0
        /** +1 at Seq. 9, +2 near the top: luck in vanilla is a coarse quantity. */
        const val BASE_LUCK = 1
    }
}
