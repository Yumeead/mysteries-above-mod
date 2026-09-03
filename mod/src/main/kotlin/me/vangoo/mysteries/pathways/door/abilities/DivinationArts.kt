package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DivinationOdds
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Door, Seq. 7 (Astrologer): **The Arts of Divination**.
 *
 * The plugin's largest ability on this pathway — 813 lines — and almost all of it was a **menu**:
 * `openChoiceMenu` picked the method, a second menu the pendulum's question, a third what to
 * dowse for, a fourth whom to spy on. There was not a single mechanic in it.
 *
 * ## The tool in hand instead of a menu
 *
 * The way of divining is chosen by the item in the main hand — the same move `RitualMagic` made
 * with a real altar. This is not a way around the missing screens (phase 11): a diviner **must**
 * have a tool, so asking about it with a separate list would be asking twice.
 *
 * | tool | method |
 * |---|---|
 * | spyglass | astrology — the stars foretell luck or misfortune |
 * | iron chain | the spiritual pendulum — a "yes" or "no" about what is in front of you |
 * | stick | dowsing — leads to the most valuable ore your Sequence can reach |
 * | amethyst cluster | the crystal ball — where one of the living is and what they are worth |
 *
 * ## What is absent: dream scrying
 *
 * The plugin's fifth method put the diviner into spectator mode over a chosen player. That is
 * exactly what `TravellersDoor` does at Seq. 5, only with two Sequences' head start — i.e. the
 * pathway would have two powers with the same verb, and would get the stronger one later. What
 * was genuinely valuable in it ("find out where someone is and how they are doing") stayed in
 * the crystal ball.
 */
class DivinationArts : ActiveAbility(AbilityIdentity.of("door_divination_arts")) {

    override val displayName: Component = Component.literal("Мистецтво ворожіння")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.AMETHYST_CLUSTER)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Спосіб ворожіння обирає інструмент у руці.\n" +
            "▪ Далекогляд — астрологія: зірки віщують удачу або біду\n" +
            "▪ Залізний ланцюг — маятник: «так» чи «ні» про те, що перед вами\n" +
            "▪ Палиця — лоза: веде до ${rodQuarry(sequence)?.name ?: "руди"} в ${ROD_RADIUS} бл.\n" +
            "▪ Аметистова друза — куля: де хтось із живих і чого він вартий\n" +
            "▪ Відкат ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome =
        when (context.caster.mainHandItem.item) {
            Items.SPYGLASS -> astrology(context)
            Items.IRON_CHAIN -> pendulum(context)
            Items.STICK -> diviningRod(context)
            Items.AMETHYST_CLUSTER -> crystalBall(context)
            else -> AbilityOutcome.failed(
                "Ворожать інструментом: далекогляд, залізний ланцюг, палиця або аметистова друза",
            )
        }

    // ── Astrology ───────────────────────────────────────────────────────────────

    /**
     * An honest coin: half for haste, half for weakness. The wager here is not in the effect's
     * strength but in the fact that the Astrologer does not know what they will draw — and pays
     * the full cast price for it.
     */
    private fun astrology(context: CastContext): AbilityOutcome {
        val kind = context.level.random.nextBoolean()
        val effect = if (kind) MobEffects.HASTE else MobEffects.WEAKNESS
        context.caster.addEffect(MobEffectInstance(effect, OMEN_TICKS, 0))

        val at = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 2.0, at.z, 30, 0.6, 0.4, 0.6, 0.02)
        context.level.playSound(
            null, at.x, at.y, at.z,
            if (kind) SoundEvents.PLAYER_LEVELUP else SoundEvents.ELDER_GUARDIAN_CURSE,
            SoundSource.PLAYERS, 0.8f, if (kind) 2.0f else 1.0f,
        )

        return AbilityOutcome.done(
            if (kind) {
                "✦ Зірки прихильні: квапливість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            } else {
                "✦ Зірки застерігають: слабкість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            },
        )
    }

    // ── The pendulum ────────────────────────────────────────────────────────────

    /**
     * The pendulum's question is not chosen — it is set by what the diviner is looking at.
     *
     * The plugin had six questions, and four of them asked about subsystems the mod does not
     * have (a journal of a place's events, potion recipes, readiness for a ritual). Two remain,
     * the ones the game can honestly check, and it is precisely the gaze that tells them apart:
     * look at someone and you ask about them, look at nothing and you ask about the surroundings.
     */
    private fun pendulum(context: CastContext): AbilityOutcome {
        chime(context)

        val watched = context.targeting.playerLookingAt(PENDULUM_RANGE)
        if (watched != null) {
            val theirs = context.beyonders.of(watched.uuid)
                ?: return AbilityOutcome.done("❓ Сильніший за вас? — Ні: перед вами звичайна людина")
            val mine = context.beyonder.sequenceLevel
            return AbilityOutcome.done(
                when {
                    theirs.sequenceLevel < mine ->
                        "❓ Сильніший за вас? — Так, і помітно: маятник рветься з рук"
                    theirs.sequenceLevel == mine -> "❓ Сильніший за вас? — Ні: рівний"
                    else -> "❓ Сильніший за вас? — Ні: слабший"
                },
            )
        }

        val others = context.targeting.playersNear(PENDULUM_RANGE)
            .count { context.beyonders.isBeyonder(it.uuid) }
        return AbilityOutcome.done(
            when (others) {
                0 -> "❓ Є поблизу Потойбічні? — Ні: навколо лише звичайні люди"
                1 -> "❓ Є поблизу Потойбічні? — Так: один"
                else -> "❓ Є поблизу Потойбічні? — Так: $others, будьте обережні"
            },
        )
    }

    // ── Dowsing ─────────────────────────────────────────────────────────────────

    /**
     * The rod looks for **the most valuable thing the Sequence can reach** and leads to it for
     * ten seconds.
     *
     * The plugin's nine-target menu is not needed here for the same reason the main one is not:
     * a choice between "look for coal" and "look for diamonds" is not a choice. The rod leads to
     * the better thing anyway, and what grows with the Sequence is the limits of what it can see.
     */
    private fun diviningRod(context: CastContext): AbilityOutcome {
        val quarry = rodQuarry(context.sequence)
            ?: return AbilityOutcome.failed("На вашому рівні лоза ще нічого не чує")

        val found = nearest(context.level, context.caster.blockPosition(), context.sequence)
            ?: return AbilityOutcome.failed("Нічого не знайдено в радіусі $ROD_RADIUS бл.")

        val (tier, at) = found
        val target = Vec3(at.x + 0.5, at.y + 0.5, at.z + 0.5)
        val glyph = ModParticles.glyph(context.effects.pathwayColor)
        val messaging = context.messaging

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.3f,
        )

        context.session {
            sustain(periodTicks = ROD_PERIOD_TICKS, durationTicks = ROD_TICKS) { diviner ->
                val level = diviner.level() as? ServerLevel ?: return@sustain
                val from = diviner.eyePosition
                val heading = target.subtract(from)
                val distance = heading.length()
                val step = heading.normalize()
                for (i in 1..ROD_NEEDLE_GLYPHS) {
                    val point = from.add(step.scale(i * ROD_NEEDLE_SPACING))
                    level.sendParticles(glyph, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
                }
                messaging.actionBar(
                    diviner,
                    Component.literal("🔍 ${tier.name}: ${distance.roundToInt()} бл."),
                )
            }
        }

        return AbilityOutcome.done("Лоза веде до: ${quarry.name}")
    }

    /** The best this Sequence can sense at all; `null` — no tier is unlocked. */
    private fun rodQuarry(sequence: Sequence): Quarry? =
        QUARRIES.firstOrNull { sequence.level <= it.unlockSequence }

    /**
     * One pass over the cube instead of nine.
     *
     * The plugin scanned a radius of 50 **separately for each target material** — that is, up to
     * a million block reads per material. Here there is one pass, and "more valuable" is decided
     * by the tier number: the first occurrence with a lower number displaces what was found, an
     * equal number only if it is closer.
     *
     * ponytail: a solid 41³ cube scan; if it ever becomes expensive, the hint is to walk chunk
     * sections and skip those with no relevant block at all.
     */
    private fun nearest(level: ServerLevel, from: BlockPos, sequence: Sequence): Pair<Quarry, BlockPos>? {
        val allowed = QUARRIES.filter { sequence.level <= it.unlockSequence }
        if (allowed.isEmpty()) return null
        val lookup = HashMap<Block, Quarry>()
        allowed.forEach { quarry -> quarry.blocks.forEach { lookup[it] = quarry } }

        var bestQuarry: Quarry? = null
        var bestPos: BlockPos? = null
        var bestDistance = Double.MAX_VALUE

        val corner = from.offset(-ROD_RADIUS, -ROD_RADIUS, -ROD_RADIUS)
        val opposite = from.offset(ROD_RADIUS, ROD_RADIUS, ROD_RADIUS)
        for (pos in BlockPos.betweenClosed(corner, opposite)) {
            val quarry = lookup[level.getBlockState(pos).block] ?: continue
            val distance = from.distSqr(pos)
            val best = bestQuarry
            val better = best == null ||
                quarry.rank < best.rank ||
                (quarry.rank == best.rank && distance < bestDistance)
            if (better) {
                bestQuarry = quarry
                bestPos = pos.immutable()
                bestDistance = distance
            }
        }
        return bestQuarry?.let { it to bestPos!! }
    }

    // ── The crystal ball ────────────────────────────────────────────────────────

    /**
     * The ball shows **one of the living**, not a chosen one: it chooses, the diviner does not.
     * That is precisely why it is separate from the pendulum — that one asks about what you are
     * looking at.
     *
     * The resistance ([AntiDivination]) is rolled here and only here: the other methods look at
     * nobody, so there is nobody to resist them.
     */
    private fun crystalBall(context: CastContext): AbilityOutcome {
        val others = context.level.server.playerList.players.filter { it.uuid != context.caster.uuid }
        if (others.isEmpty()) return AbilityOutcome.done(omen(context))

        val seen = others[context.level.random.nextInt(others.size)]
        val at = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 30, 0.5, 0.5, 0.5, 0.0)
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f,
        )

        val odds = resistance(context, seen)
        if (odds != null && context.level.random.nextDouble() >= odds.successProbability()) {
            return AbilityOutcome.done(
                "🔮 Бачення каламутне (шанс ${odds.formattedPercent()}) — ${omen(context)}",
            )
        }

        return AbilityOutcome.done("🔮 ${reveal(context, seen)}")
    }

    /**
     * `null` — the target does not resist at all, and there is nothing to roll.
     *
     * Two conditions, both from the plugin: the shield is on **and** the target has grown into it
     * ([AntiDivination.UNLOCK_SEQUENCE]).
     */
    private fun resistance(context: CastContext, seen: ServerPlayer): DivinationOdds? {
        val theirs: Beyonder = context.beyonders.of(seen.uuid) ?: return null
        if (theirs.sequenceLevel > AntiDivination.UNLOCK_SEQUENCE) return null
        if (!context.beyonders.isToggled(seen.uuid, AntiDivination.IDENTITY)) return null
        return DivinationOdds(context.beyonder.sequenceLevel, theirs.sequenceLevel)
    }

    private fun reveal(context: CastContext, seen: ServerPlayer): String {
        val where = seen.level().dimension().identifier().path
        val vitals = "❤ ${seen.health.roundToInt()}/${seen.maxHealth.roundToInt()}"
        val who = context.beyonders.of(seen.uuid)?.let {
            " · ${it.pathway.name} ${it.sequenceLevel} (${it.pathway.sequenceName(it.sequenceLevel)})"
        } ?: " · без ознак потойбічності"
        return "${seen.name.string}: $where ${seen.blockX} ${seen.blockY} ${seen.blockZ} · $vitals$who"
    }

    /** The ball says nothing about people — then it speaks of the sky. A cheap truth instead of emptiness. */
    private fun omen(context: CastContext): String {
        val day = context.level.gameTime / DAY_TICKS
        val sky = if (context.level.isRaining) "дощ" else "ясно"
        return "у небі $sky, іде ${day + 1}-й день світу"
    }

    private fun chime(context: CastContext) {
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.4f, 1.5f,
        )
        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            at.x, at.y + 1.4, at.z, 12, 0.3, 0.3, 0.3, 0.0,
        )
    }

    /**
     * What the rod can sense. [rank] is the worth (0 — the most valuable), [unlockSequence] the
     * weakest Sequence this is already available to.
     */
    private class Quarry(
        val name: String,
        val rank: Int,
        val unlockSequence: Int,
        vararg val blocks: Block,
    )

    private companion object {
        const val COOLDOWN_SECONDS = 60
        const val OMEN_TICKS = 10 * 60 * TICKS_PER_SECOND
        const val PENDULUM_RANGE = 30.0
        const val ROD_RADIUS = 20
        const val ROD_TICKS = 10 * TICKS_PER_SECOND
        const val ROD_PERIOD_TICKS = 10
        const val ROD_NEEDLE_GLYPHS = 5
        const val ROD_NEEDLE_SPACING = 0.6
        const val DAY_TICKS = 24_000L

        /**
         * From the most valuable to the cheapest. The block lists are here by hand rather than by
         * tag: vanilla 26.2 has tags for only three ores (`IRON_ORES`, `GOLD_ORES`,
         * `COPPER_ORES`), so half the tiers would remain a list anyway, and a mixture of tags and
         * lists reads worse than just a list.
         */
        val QUARRIES = listOf(
            Quarry("Стародавні уламки", 0, 5, Blocks.ANCIENT_DEBRIS),
            Quarry("Алмази", 1, 7, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            Quarry("Смарагди", 2, 8, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            Quarry("Золото", 3, 9, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            Quarry("Лазурит", 4, 9, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            Quarry("Редстоун", 5, 9, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            Quarry("Залізо", 6, 9, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            Quarry("Мідь", 7, 9, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            Quarry("Вугілля", 8, 9, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        )
    }
}
