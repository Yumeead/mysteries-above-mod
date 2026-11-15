package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.Vec3
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Fool, Seq. 9 (Seer): **The Arts of Divination**.
 *
 * Door's own [me.vangoo.mysteries.pathways.door.abilities.DivinationArts] already owns the
 * "divination as intel about the world and other people" niche — tool-in-hand, four methods, an
 * `AntiDivination` resistance roll on the one method that looks at someone else. The plugin's
 * Fool version is a *different* class with the same generic name, and two of its four methods
 * (dowsing for ore, dream-spectating a player) are the exact same verb Door and `TravellersDoor`
 * already own. Carrying them across would give the pathway two divination powers with nothing
 * to tell them apart.
 *
 * What is left once those two are cut is genuinely Fool's own: the Seer reads **their own** fate,
 * not the world's. Neither method here ever looks at another player, so there is no resistance to
 * roll and no `AntiDivination` to check against.
 *
 * ## The pendulum's questions are modes
 *
 * The plugin asked which question in a chest menu after the cast. Here the question is the
 * ability's mode, picked ahead on the wheel (or by sneak + cast), and the chain answers it at
 * once. Two of the plugin's six are left, both about the Seer's own fate and surroundings: am I
 * ready to advance, and am I alone. The ingredient question returns with brewing.
 *
 * ## The answer is the Seer's alone
 *
 * It hangs in the air before them — words by the swinging chain, stars over their gaze — and
 * nobody beside them sees it: a fate read aloud to the whole street would not be divination.
 * The sounds go to the Seer's ears only, for the same reason: a fanfare for a lucky omen would
 * give the omen away.
 */
class DivinationArts : ActiveAbility(AbilityIdentity.of("fool_divination_arts")) {

    override val displayName: Component = Component.literal("Мистецтво ворожіння")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.IRON_CHAIN)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Спосіб ворожіння обирає інструмент у руці.\n" +
            "▪ Далекогляд — астрологія: зірки віщують удачу або біду\n" +
            "▪ Залізний ланцюг — маятник: відповідає на питання, обране режимом\n" +
            "▪ Присід + застосування — інше питання\n" +
            "▪ Відкат $BASE_COOLDOWN_SECONDS с",
    )

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Question.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.label)) }

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.caster.isShiftKeyDown) {
            // The question now asked is on the HUD beside the power's name.
            context.modes.cycle(context.caster.uuid, this, context.sequence)
            return AbilityOutcome.Awaiting()
        }
        return when (context.caster.mainHandItem.item) {
            Items.SPYGLASS -> astrology(context)
            Items.IRON_CHAIN -> pendulum(context)
            else -> AbilityOutcome.failed("Ворожать інструментом: далекогляд або залізний ланцюг")
        }
    }

    // ── Astrology: an honest coin, the full price paid before the draw is known ────

    private fun astrology(context: CastContext): AbilityOutcome {
        val lucky = context.level.random.nextBoolean()
        val effect = if (lucky) MobEffects.HASTE else MobEffects.WEAKNESS
        context.caster.addEffect(MobEffectInstance(effect, OMEN_TICKS, 0))

        // The stars come out over the Seer's gaze: in a row if they favour, scattered and dim if they warn.
        val sky = ahead(context, SKY_REACH).add(0.0, SKY_RISE, 0.0)
        val omen = if (lucky) {
            Component.literal("✦ ✦ ✦ ✦ ✦\nЗірки прихильні")
        } else {
            Component.literal("✦   ✧ ✦     ✧\nЗірки застерігають")
        }
        privateText(context.caster, sky, omen, ANSWER_TICKS, if (lucky) FAVOUR else WARNING)
        privateSound(
            context.caster, sky,
            if (lucky) SoundEvents.AMETHYST_BLOCK_CHIME else SoundEvents.ELDER_GUARDIAN_CURSE,
            0.8f, if (lucky) 1.6f else 1.0f,
        )
        return AbilityOutcome.Done()
    }

    // ── The pendulum: the question picked as the mode ───────────────────────────────

    private enum class Question(val label: String) {
        ADVANCE("Чи готовий я до просування?"),
        BEYONDERS("Чи є поблизу інші Потойбічні?"),
    }

    private fun pendulum(context: CastContext): AbilityOutcome {
        val question = Question.entries.first { it.name.lowercase() == mode(context) }
        val answer = when (question) {
            Question.ADVANCE -> advanceAnswer(context)
            Question.BEYONDERS -> beyondersAnswer(context)
        }
        // Below the gaze, where the chain hangs from the hand: the answer is read off the pendulum.
        val chain = ahead(context, CHAIN_REACH).add(0.0, CHAIN_DROP, 0.0)
        privateText(context.caster, chain, Component.literal(answer), ANSWER_TICKS)
        privateSound(context.caster, chain, SoundEvents.NOTE_BLOCK_BELL.value(), 0.4f, 1.5f)
        return AbilityOutcome.Done()
    }

    /** [reach] blocks ahead of the eye, along the look flattened to the horizon. */
    private fun ahead(context: CastContext, reach: Double): Vec3 {
        val flat = Vec3(context.look.x, 0.0, context.look.z).let { if (it.lengthSqr() < 1e-4) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        return context.eye.add(flat.scale(reach))
    }

    private fun advanceAnswer(context: CastContext): String {
        val beyonder = context.beyonder
        return when {
            beyonder.canAdvance() -> "Так — ви готові до ритуалу просування, знайдіть відповідне зілля"
            beyonder.mastery.value < 50.0 ->
                "Ні — ваше засвоєння занадто низьке (${beyonder.mastery}), потрібно більше практики"
            beyonder.spirituality.current * 100 / beyonder.spirituality.maximum < 80 ->
                "Майже — засвоєння високе (${beyonder.mastery}), та духовність ще не відновилась"
            else -> "Майже — засвоєння ${beyonder.mastery}, ще трохи практики"
        }
    }

    /** Counts, never names: the pendulum swings, it does not point. */
    private fun beyondersAnswer(context: CastContext): String {
        val count = context.targeting.playersNear(SENSE_RADIUS)
            .count { it.uuid != context.caster.uuid && context.beyonders.of(it) != null }
        return when (count) {
            0 -> "Ні — навколо лише звичайні люди"
            1 -> "Так — поруч відчувається один Потойбічний"
            else -> "Так — поблизу Потойбічних: $count, будьте обережні"
        }
    }

    private companion object {
        const val BASE_COST = 70
        const val SENSE_RADIUS = 30.0
        const val BASE_COOLDOWN_SECONDS = 60
        const val OMEN_TICKS = 10 * 60 * TICKS_PER_SECOND
        const val ANSWER_TICKS = 4 * TICKS_PER_SECOND
        const val SKY_REACH = 2.5
        const val SKY_RISE = 1.4
        const val CHAIN_REACH = 1.2
        const val CHAIN_DROP = -0.45
        val FAVOUR: RgbColor = RgbColor.of(255, 214, 122)
        val WARNING: RgbColor = RgbColor.of(196, 84, 84)
    }
}
