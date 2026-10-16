package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
            val next = context.modes.cycle(context.caster.uuid, this, context.sequence)
            return AbilityOutcome.Awaiting(Component.literal("Питання маятника: ${next?.name?.string}"))
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

        val at = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 2.0, at.z, 30, 0.6, 0.4, 0.6, 0.02)
        context.level.playSound(
            null, at.x, at.y, at.z,
            if (lucky) SoundEvents.PLAYER_LEVELUP else SoundEvents.ELDER_GUARDIAN_CURSE,
            SoundSource.PLAYERS, 0.8f, if (lucky) 2.0f else 1.0f,
        )

        return AbilityOutcome.done(
            if (lucky) {
                "✦ Зірки прихильні: квапливість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            } else {
                "✦ Зірки застерігають: слабкість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            },
        )
    }

    // ── The pendulum: the question picked as the mode ───────────────────────────────

    private enum class Question(val label: String) {
        ADVANCE("Чи готовий я до просування?"),
        BEYONDERS("Чи є поблизу інші Потойбічні?"),
    }

    private fun pendulum(context: CastContext): AbilityOutcome {
        chime(context)
        val question = Question.entries.first { it.name.lowercase() == mode(context) }
        val answer = when (question) {
            Question.ADVANCE -> advanceAnswer(context)
            Question.BEYONDERS -> beyondersAnswer(context)
        }
        return AbilityOutcome.done("❓ ${question.label} — $answer")
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

    private fun chime(context: CastContext) {
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.4f, 1.5f,
        )
    }

    private companion object {
        const val BASE_COST = 70
        const val SENSE_RADIUS = 30.0
        const val BASE_COOLDOWN_SECONDS = 60
        const val OMEN_TICKS = 10 * 60 * TICKS_PER_SECOND
    }
}
