package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import kotlin.math.roundToInt

/**
 * Visionary, Seq. 7 (Psychiatrist): **Telepathy**.
 *
 * Touch a player, and their mind is isolated for a few seconds. Let the Psychiatrist in — hold
 * shift until the isolation lifts — and three fragments of the deep mind come loose along with
 * a moment of clarity; resist, and only surface thoughts leak out, along with the pain of the
 * intrusion.
 *
 * ## Consent is read once, at the deadline
 *
 * The plugin's `monitorSneaking` watched for a sneak toggle mid-window. Reading
 * [ServerPlayer.isShiftKeyDown] once when the isolation ends says the same thing — "were you
 * still holding it when I asked" — for one scheduled callback instead of a subscription that
 * has to be told apart from a stray toggle earlier in the window.
 *
 * ## One resource instead of five
 *
 * The plugin's "greed analysis" scored five ore types against their crafted uses. What
 * survives is the beat, not the spreadsheet: how much of the rarest thing a person hoards says
 * enough on its own, and vanilla's own [Stats] already keeps the count — nothing to log by hand.
 */
class Telepathy : ActiveAbility(AbilityIdentity.of("visionary_telepathy")) {

    override val displayName: Component = Component.literal("Телепатія")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE)).roundToInt()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Вимагає дотику й трьох інгредієнтів. Ізолює розум цілі на ${WAIT_SECONDS}с.\n" +
            "▪ Тримає [Shift] до кінця — впускає вас, дізнаєтесь 3 таємниці\n" +
            "▪ Ні — опирається, ви бачите лише уривки, а вона відчуває біль\n" +
            "▪ Інгредієнти: свічка, світлопил, ферментоване око павука",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE) as? ServerPlayer
            ?: return AbilityOutcome.failed("Потрібно підійти впритул до живої цілі")
        if (target.uuid == context.caster.uuid) return AbilityOutcome.failed("Не можна читати свої думки")

        val inventory = context.caster.inventory
        if (!INGREDIENTS.all { inventory.countItem(it) > 0 }) {
            return AbilityOutcome.failed("Немає інгредієнтів (свічка + світлопил + ферментоване око)")
        }
        INGREDIENTS.forEach { ingredient ->
            inventory.clearOrCountMatchingItems({ stack -> stack.`is`(ingredient) }, 1, inventory)
        }

        val multiplier = SequenceScaler.calculateMultiplier(context.sequence.level, SequenceScaler.ScalingStrategy.MODERATE)
        val waitTicks = (WAIT_SECONDS * TICKS_PER_SECOND * multiplier).roundToInt()

        target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, waitTicks, 255))
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, waitTicks, 255))
        context.level.playSound(null, target.x, target.y, target.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.5f)

        context.messaging.tell(target, Component.literal("░░░ 👁 ВАШ РОЗУМ ІЗОЛЬОВАНО 👁 ░░░"))
        context.messaging.tell(target, Component.literal("Хтось торкнувся вашої свідомості."))
        context.messaging.tell(target, Component.literal("Тримайте [SHIFT] — згода. Нічого не робіть — опір."))
        context.messaging.tell(context.caster, Component.literal("Контакт встановлено. Очікування реакції..."))

        val casterId = context.caster.uuid
        val targetId = target.uuid
        val sequence = context.sequence
        context.scheduling.after(waitTicks) {
            resolve(context.messaging, context.beyonders, context.level, casterId, targetId, sequence, multiplier)
        }

        return AbilityOutcome.done("")
    }

    private fun resolve(
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        beyonders: me.vangoo.mysteries.ability.context.Beyonders,
        level: net.minecraft.server.level.ServerLevel,
        casterId: java.util.UUID,
        targetId: java.util.UUID,
        sequence: Sequence,
        multiplier: Double,
    ) {
        val target = level.server.playerList.getPlayer(targetId) ?: return
        val accepted = target.isShiftKeyDown

        if (accepted) {
            val sanityBonus = (-10 * multiplier).roundToInt()
            val regenTicks = (8 * TICKS_PER_SECOND * multiplier).roundToInt()
            val regenAmplifier = (multiplier.roundToInt() - 1).coerceIn(0, 2)
            beyonders.of(targetId)?.increaseSanityLoss(sanityBonus)
            target.addEffect(MobEffectInstance(MobEffects.REGENERATION, regenTicks, regenAmplifier))
            messaging.tell(targetId, Component.literal("✔ Ви впустили Візіонера. Розум прояснився."))
            level.playSound(null, target.x, target.y, target.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f)
            messaging.tell(casterId, Component.literal("Ціль відкрилась. Вихоплено фрагменти пам'яті."))
            report(messaging, casterId, target, deepSecrets(target), true)
        } else {
            val sanityPenalty = (10 * multiplier).roundToInt()
            val debuffTicks = (2 * TICKS_PER_SECOND * multiplier).roundToInt()
            val debuffAmplifier = (multiplier.roundToInt() - 1).coerceIn(0, 3)
            beyonders.of(targetId)?.increaseSanityLoss(sanityPenalty)
            target.hurtServer(level, level.damageSources().magic(), 1.0f * multiplier.toFloat())
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, debuffTicks, debuffAmplifier))
            target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, debuffTicks, debuffAmplifier))
            messaging.tell(targetId, Component.literal("✖ Ви виштовхнули вторгнення силою."))
            level.playSound(null, target.x, target.y, target.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 1f)
            messaging.tell(casterId, Component.literal("Ціль опиралась. Доступні лише уривки."))
            report(messaging, casterId, target, surfaceThoughts(target), false)
        }
    }

    private fun report(
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        casterId: java.util.UUID,
        target: ServerPlayer,
        facts: List<String>,
        deep: Boolean,
    ) {
        val type = if (deep) "ГЛИБИННІ ТАЄМНИЦІ" else "ПОВЕРХНЕВІ ДУМКИ"
        messaging.tell(casterId, Component.literal("━━━ $type · ${target.name.string} ━━━"))
        facts.shuffled().take(3).forEach { messaging.tell(casterId, Component.literal("• $it")) }
    }

    private fun surfaceThoughts(target: ServerPlayer): List<String> = listOf(
        "⌚ Час у грі: ${target.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / TICKS_PER_SECOND / 3600} год.",
        "🗡 Тримає в руці: ${target.mainHandItem.hoverName.string}",
        "☠ Кількість смертей: ${target.stats.getValue(Stats.CUSTOM.get(Stats.DEATHS))}",
    )

    private fun deepSecrets(target: ServerPlayer): List<String> {
        val mined = target.stats.getValue(Stats.BLOCK_MINED, Blocks.DIAMOND_ORE) +
            target.stats.getValue(Stats.BLOCK_MINED, Blocks.DEEPSLATE_DIAMOND_ORE)
        val used = target.stats.getValue(Stats.ITEM_CRAFTED, Items.DIAMOND_PICKAXE) +
            target.stats.getValue(Stats.ITEM_CRAFTED, Items.DIAMOND_SWORD)
        return listOf(
            "Рівень досвіду: ${target.experienceLevel}",
            "Вбито гравців: ${target.stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS))}",
            "💰 Алмазів видобуто: $mined, використано на реманент: $used",
            "Місце останньої смерті: ${target.lastDeathLocation.map { it.pos() }.orElse(null) ?: "невідомо"}",
        )
    }

    private companion object {
        const val RANGE = 2.0
        const val BASE_COST = 80
        const val BASE_COOLDOWN_SECONDS = 60
        const val WAIT_SECONDS = 5
        val INGREDIENTS = listOf(Items.FERMENTED_SPIDER_EYE, Items.GLOWSTONE_DUST, Items.CANDLE)
    }
}
