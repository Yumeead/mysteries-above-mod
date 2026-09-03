package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.EvidenceLog
import me.vangoo.mysteries.item.ModComponents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Error, Seq. 7 (Cryptologist): **Decryption**.
 *
 * The first Error power whose output is **knowledge** rather than an effect on someone. The
 * Marauder took a thing, the Swindler took attention; the Cryptologist takes a truth someone
 * tried to keep.
 *
 * ## Decryption is not divination, and that is modelled rather than said
 *
 * The wiki draws exactly one line around this power: *"a Cryptologist needs enough information
 * for deduction and they can't make wild guesses."* So the answer's **depth is computed from how
 * much evidence actually exists** — nothing here is ever invented. Two traces give a vague
 * answer, nine give the whole truth, and the thresholds fall as the Sequence improves.
 *
 * Knowledge is the only Error resource priced in sanity: a deeper reading costs more of it.
 *
 * ## The mode is chosen by the crosshair, not by a menu
 *
 * living target → block under the crosshair → the item in hand → the zone around you. One `if`
 * chain, and the player steers it with their gaze. The plugin asked for the mode in a chest GUI.
 *
 * ## Not `DecryptPatterns` of the Door
 *
 * That one is a passive that reports powers used nearby, right now. This is an active that reads
 * traces already left. They share a translation of *decrypt* and nothing else — do not merge them.
 */
class Decryption : ActiveAbility(AbilityIdentity.of("error_decryption")) {

    override val displayName: Component = Component.literal("Дешифрування")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE))
            .roundToInt()
            .coerceAtLeast(MIN_COOLDOWN_SECONDS)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви читаєте те, що інші лишили по собі. Що читати — обирає ваш погляд.\n" +
            "▪ Жива ціль — її стан і чи бралась вона за силу\n" +
            "▪ Блок — хто тут був і куди пішов\n" +
            "▪ Річ у руці — що вона насправді несе\n" +
            "▪ Порожнеча перед вами — усе таємне в межах ${ZONE_RADIUS} бл.\n" +
            "▪ Глибина відповіді залежить від того, скільки слідів існує: " +
            "${threshold(DEPTH_1, sequence)} / ${threshold(DEPTH_2, sequence)} / " +
            "${threshold(DEPTH_3, sequence)}\n" +
            "▪ Глибше читання коштує глузду; відкат ${cooldownSeconds(sequence)} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val gazedEntity = context.targeting.lookingAt(TARGET_RANGE)
        val held = context.caster.getItemInHand(InteractionHand.MAIN_HAND)

        return when {
            gazedEntity != null -> readCreature(context, gazedEntity)
            gazedBlock(context) != null -> readPlace(context, gazedBlock(context)!!)
            !held.isEmpty -> readThing(context, held)
            else -> readZone(context)
        }
    }

    // ── Mode A: a living target ──────────────────────────────────────────────────

    /**
     * What state the target is in and whether they have been working.
     *
     * Their pathway and Sequence are **never** printed: that is the Sequence-8 tier of
     * `SuperiorObservation`, and repeating it here would make the strengthened passive worthless.
     */
    private fun readCreature(context: CastContext, target: LivingEntity): AbilityOutcome {
        val effects = target.activeEffects.toList()
        val powers = EvidenceLog.by(target.uuid)
        val depth = depth(context.sequence, effects.size + powers.size)

        val lines = mutableListOf<String>()
        lines += if (effects.isEmpty()) {
            "жодних діючих станів"
        } else {
            "діє: " + effects.joinToString(", ") { it.effect.value().displayName.string }
        }
        if (depth >= 2) {
            lines += if (powers.isEmpty()) {
                "за силу останнім часом не бралась"
            } else {
                "недавно бралась за силу: ${powers.size}"
            }
        }
        if (depth >= 3) {
            val sanity = context.beyonders.of(target)?.sanityLoss
            lines += when {
                sanity == null -> "розум звичайний — тут нема чого зриватись"
                sanity.isExtreme() || sanity.isCritical() -> "розум на межі зриву"
                sanity.isSevere() || sanity.isSerious() -> "розум помітно розхитаний"
                else -> "розум тримається"
            }
        }

        halo(context, target.position().add(0.0, target.bbHeight * 0.5, 0.0))
        return report(context, depth, "${target.name.string}", lines)
    }

    // ── Mode B: the place ────────────────────────────────────────────────────────

    /** Who was here, and — deep enough — where they are now. */
    private fun readPlace(context: CastContext, pos: BlockPos): AbilityOutcome {
        val traces = EvidenceLog.near(pos, TRACE_RADIUS)
        if (traces.isEmpty()) return AbilityOutcome.failed("Це місце нічого не пам'ятає")

        val depth = depth(context.sequence, traces.size)
        val lines = mutableListOf("слідів: ${traces.size} за останні 10 хв")

        val culprit = traces.groupingBy { it.actorId }.eachCount().maxByOrNull { it.value }?.key
        val name = traces.first { it.actorId == culprit }.actorName
        if (depth >= 2) lines += "найбільше наслідив: $name"
        if (depth >= 3) {
            val walker = context.level.getPlayerInAnyDimension(culprit!!)
            if (walker != null && walker.level() === context.level) {
                lines += "і зараз він за ${walker.position().distanceTo(Vec3.atCenterOf(pos)).roundToInt()} бл. звідси"
                trail(context, Vec3.atCenterOf(pos), walker.position().add(0.0, 1.0, 0.0))
            } else {
                lines += "але слід уривається — його тут уже немає"
            }
        }

        traces.map { it.pos }.distinct().take(MAX_MARKS).forEach { mark(context, Vec3.atCenterOf(it)) }
        return report(context, depth, "Місце", lines)
    }

    // ── Mode C: the thing in hand ────────────────────────────────────────────────

    /**
     * What a thing carries beyond being itself.
     *
     * The evidence here is the item's own data: vanilla keeps everything a stack was given —
     * a brew, enchantments, a name, damage — as components patched over the prototype, and the
     * count of those patches is literally "how much this thing has to hide".
     */
    private fun readThing(context: CastContext, stack: ItemStack): AbilityOutcome {
        val patches = stack.componentsPatch.size()
        val depth = depth(context.sequence, patches)
        val lines = mutableListOf<String>()

        val brew = stack.get(ModComponents.POTION_BREW)
        if (brew != null) {
            val pathway = brew.resolvePathway()
            lines += if (pathway == null) {
                "у пляшці — шлях, якого цей світ ще не знає: ${brew.pathway}, Посл. ${brew.sequence}"
            } else {
                "у пляшці: ${pathway.name}, Посл. ${brew.sequence} — ${pathway.sequenceName(brew.sequence)}"
            }
            if (depth >= 2 && pathway != null) {
                val roster = pathway.abilitiesFor(brew.sequence)
                lines += if (roster.isEmpty()) {
                    "сили за нею ще не записані"
                } else {
                    "дає: " + roster.joinToString(", ") { it.identity.id }
                }
            }
            if (depth >= 3) {
                val mine = context.beyonder
                lines += when {
                    mine.pathway.name != brew.pathway -> "пити не можна: це чужий шлях, і він вас зламає"
                    brew.sequence != mine.sequenceLevel - 1 -> "пити рано: це не ваша наступна Послідовність"
                    else -> "готове до вас: це рівно ваш наступний крок"
                }
            }
        } else {
            lines += if (patches == 0) {
                "ця річ не приховує нічого — вона рівно те, чим здається"
            } else {
                "ця річ несе прихованого: $patches"
            }
            if (depth >= 2) {
                val enchantments = stack.enchantments
                if (!enchantments.isEmpty) {
                    lines += "чари: " + enchantments.keySet().joinToString(", ") {
                        it.value().description.string
                    }
                }
                if (stack.isDamaged) lines += "нею вже працювали: ${stack.damageValue} зносу"
            }
        }

        scripture(context)
        return report(context, depth, stack.hoverName.string, lines)
    }

    // ── Mode D: the zone ─────────────────────────────────────────────────────────

    /** Everything hidden around: how many of us are here, where power was used, what was opened. */
    private fun readZone(context: CastContext): AbilityOutcome {
        val origin = context.caster.blockPosition()
        val traces = EvidenceLog.near(origin, ZONE_RADIUS)
        val beyonders = context.targeting.playersNear(ZONE_RADIUS.toDouble())
            .count { context.beyonders.isBeyonder(it.uuid) }

        val depth = depth(context.sequence, traces.size + beyonders)
        val lines = mutableListOf<String>()

        lines += when (beyonders) {
            0 -> "більше нікого з нашого роду поруч немає"
            else -> "поруч таких, як ви: $beyonders"
        }
        if (depth >= 2) {
            val powers = traces.filter { it.kind == EvidenceLog.Trace.POWER }
            lines += if (powers.isEmpty()) {
                "силу тут не застосовували"
            } else {
                "застосувань сили поблизу: ${powers.size}"
            }
        }
        if (depth >= 3) {
            val touched = traces.filter { it.kind == EvidenceLog.Trace.TOUCHED }
            lines += if (touched.isEmpty()) {
                "сховищ ніхто не чіпав"
            } else {
                "сховищ потривожено: ${touched.map { it.pos }.distinct().size}"
            }
        }

        traces.map { it.pos }.distinct().take(MAX_MARKS).forEach { mark(context, Vec3.atCenterOf(it)) }
        wave(context)
        return report(context, depth, "Округа", lines)
    }

    // ── Depth, price and the report ──────────────────────────────────────────────

    /** How deep the reading goes: entirely a function of how much there was to read. */
    private fun depth(sequence: Sequence, evidence: Int): Int = when {
        evidence >= threshold(DEPTH_3, sequence) -> 3
        evidence >= threshold(DEPTH_2, sequence) -> 2
        else -> 1
    }

    /** The thresholds fall as the Sequence improves: the same scraps say more to a sharper mind. */
    private fun threshold(base: Int, sequence: Sequence): Int =
        (base - (GATE_SEQUENCE - sequence.level)).coerceAtLeast(1)

    private fun report(context: CastContext, depth: Int, subject: String, lines: List<String>): AbilityOutcome {
        val loss = SANITY_BY_DEPTH[depth - 1]
        if (loss > 0) context.beyonder.increaseSanityLoss(loss)

        val text = Component.literal("[$subject] ")
            .withStyle(ChatFormatting.AQUA)
            .append(Component.literal(lines.joinToString("; ")).withStyle(ChatFormatting.GRAY))
        context.messaging.tell(context.caster, text)

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.9f + 0.2f * depth,
        )
        return AbilityOutcome.done("Прочитано, глибина $depth")
    }

    // ── What the eye sees ────────────────────────────────────────────────────────

    private fun gazedBlock(context: CastContext): BlockPos? {
        val hit = context.caster.pick(TARGET_RANGE, 1.0f, false)
        return (hit as? BlockHitResult)?.takeIf { it.type == HitResult.Type.BLOCK }?.blockPos
    }

    private fun mote(context: CastContext): ParticleOptions = ModParticles.mote(context.effects.pathwayColor)

    private fun mark(context: CastContext, at: Vec3) {
        context.level.sendParticles(
            context.caster, mote(context), true, true,
            at.x, at.y + 0.6, at.z, 5, 0.2, 0.3, 0.2, 0.0,
        )
    }

    /** A ring around what is being read. */
    private fun halo(context: CastContext, around: Vec3) {
        val mote = mote(context)
        for (i in 0 until HALO_POINTS) {
            val angle = TAU * i / HALO_POINTS
            context.level.sendParticles(
                mote,
                around.x + HALO_RADIUS * Math.cos(angle),
                around.y,
                around.z + HALO_RADIUS * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    /** Lines of unseen writing rising around the reader. */
    private fun scripture(context: CastContext) {
        val mote = mote(context)
        var height = 0.0
        while (height <= SCRIPTURE_HEIGHT) {
            val angle = TAU * height
            context.level.sendParticles(
                mote,
                context.origin.x + SCRIPTURE_RADIUS * Math.cos(angle),
                context.origin.y + height,
                context.origin.z + SCRIPTURE_RADIUS * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
            height += SCRIPTURE_STEP
        }
    }

    /** A ring running outward to the edge of what was read. */
    private fun wave(context: CastContext) {
        val mote = mote(context)
        var radius = 2.0
        while (radius <= ZONE_RADIUS) {
            for (i in 0 until WAVE_POINTS) {
                val angle = TAU * i / WAVE_POINTS
                context.level.sendParticles(
                    context.caster, mote, true, true,
                    context.origin.x + radius * Math.cos(angle),
                    context.origin.y + 0.4,
                    context.origin.z + radius * Math.sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
            radius += WAVE_STEP
        }
    }

    private fun trail(context: CastContext, from: Vec3, to: Vec3) {
        val mote = mote(context)
        val level: ServerLevel = context.level
        val steps = (from.distanceTo(to) / TRAIL_STEP).toInt().coerceIn(1, MAX_TRAIL_STEPS)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            level.sendParticles(context.caster, mote, true, true, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private companion object {
        const val TARGET_RANGE = 20.0
        const val ZONE_RADIUS = 24
        const val TRACE_RADIUS = 8
        const val BASE_COOLDOWN_SECONDS = 45
        const val MIN_COOLDOWN_SECONDS = 20

        /** The Sequence the thresholds are stated for; stronger ones need less. */
        const val GATE_SEQUENCE = 7
        const val DEPTH_1 = 2
        const val DEPTH_2 = 5
        const val DEPTH_3 = 9

        /** Knowledge is priced in sanity, and the deeper it goes the more it costs. */
        val SANITY_BY_DEPTH = intArrayOf(0, 2, 5)

        const val MAX_MARKS = 8
        const val HALO_POINTS = 16
        const val HALO_RADIUS = 1.1
        const val SCRIPTURE_HEIGHT = 2.0
        const val SCRIPTURE_RADIUS = 0.8
        const val SCRIPTURE_STEP = 0.08
        const val WAVE_POINTS = 24
        const val WAVE_STEP = 3.0
        const val TRAIL_STEP = 0.6
        const val MAX_TRAIL_STEPS = 80
        const val TAU = 2.0 * Math.PI
    }
}
