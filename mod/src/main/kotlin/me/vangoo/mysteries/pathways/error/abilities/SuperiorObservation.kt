package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.EvidenceLog
import me.vangoo.mysteries.pathways.error.ErrorTags
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.Container
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * Error, Seq. 9 (Marauder): **Superior Observation**.
 *
 * The Marauder sees where the valuable things lie — in a wall and in someone else's pocket. The
 * ability is registered here only, and it grows **inside itself** as the Sequence improves: a
 * read of intent from a single glance (Seq. 8), traces of someone else's work (7), a sense of
 * hoards fifty blocks out (6). It is built that way in the plugin too, and for the same reason:
 * otherwise a second "observation" would appear at every Sequence and the first would never go.
 *
 * ## Ore is looked for by the palette, not by brute force
 *
 * The plugin called `getBlockAt` over a 13×13×13 cube — **2 197 lookups per Marauder every five
 * seconds**, on the main thread. Meanwhile the game already holds the answer to "could there be a
 * diamond in this piece at all": the chunk section's palette. `LevelChunkSection.maybeHas` reads
 * it in one check and a section with no ore is dropped whole — we never step inside. In stone
 * without veins that is zero lookups.
 *
 * ## What is found is shown by direction, not by a number
 *
 * The plugin printed "Valuable ore nearby (3)" to the action bar — a number instead of a form:
 * the player knew ore existed and not where. A mark on the block itself does not cure that, and
 * it was measured why: a particle is depth-tested, so one placed inside a vein is hidden by the
 * rock in front of it and shows exactly nothing. So the sense **pulls**: one mote per find, a
 * metre and a half from the eyes, on the ray towards it, and only the caster sees them.
 *
 * Someone else's pockets stay a line of text, and that is not a concession: highlighting a player
 * would give the thief away to the very person they are sizing up.
 *
 * ## The period is declared, not counted
 *
 * The plugin had a `private int tickCounter` field on an object shared by the whole pathway: with
 * N Marauders it advanced N× per round, and five seconds quietly became 5/N. [tickPeriodTicks]
 * cannot have that flaw by construction — there is no counter at all.
 */
class SuperiorObservation :
    PermanentPassiveAbility(AbilityIdentity.of("error_observation")),
    Ticking {

    /**
     * Half a second. The gaze tier is a live read — a slower beat would show the intent of a
     * creature the player is no longer looking at.
     */
    override val tickPeriodTicks: Int = TICKS_PER_SECOND / 2

    override val displayName: Component = Component.literal("Покращена спостережливість")

    override fun description(sequence: Sequence): Component = Component.literal(
        buildString {
            append("Цінне саме впадає вам у око.\n")
            append("▪ Руда за $ORE_RADIUS бл. тягне порошинку в свій бік — бачите тільки ви\n")
            append("▪ Кишені гравців за ${POCKET_RADIUS.toInt()} бл. читаються самі")
            if (sequence.level <= GAZE_TIER) {
                append("\n▪ Погляд на істоту за ${GAZE_RANGE.toInt()} бл. читає її намір: ")
                append("чи цілиться вона у вас і чи вона Потойбічна")
                append("\n▪ Природу Потойбічного, сильнішого за вас, розгледіти не вдається")
            }
        },
    )

    /**
     * Four tiers on three beats.
     *
     * The rates differ by an order of magnitude — a glance must be live, a fifty-block sweep of
     * hoards must not run twenty times a minute — and they are told apart by the **mod's own
     * clock**, not by a counter in a field. That distinction is the whole point: a global tick
     * cannot desync on relog, and there are no per-player counters to skew when a second Marauder
     * logs in.
     */
    override fun tick(context: AbilityContext) {
        val clock = context.scheduling.tick
        val level = context.sequence.level

        if (level <= EVIDENCE_TIER) markTraces(context)
        if (clock % TICKS_PER_SECOND == 0L) markOres(context, oreRadius(level))
        if (level <= TREASURE_TIER && clock % (TREASURE_PERIOD_SECONDS * TICKS_PER_SECOND) == 0L) {
            senseTreasure(context)
        }

        val gazed = if (level <= GAZE_TIER) readGaze(context) else false
        if (!gazed && clock % TICKS_PER_SECOND == 0L) readPockets(context)
    }

    /**
     * Seq. 6: hoards give themselves away for half a hundred blocks.
     *
     * Containers are found through the chunks' own block-entity maps rather than by walking a
     * cube: at fifty blocks a cube is a million cells, and this runs on a clock. Each find is
     * named by how rich it is, which is what the wiki asks for — "their approximate value, and
     * possible types".
     */
    private fun senseTreasure(context: AbilityContext) {
        val level = context.level
        val caster = context.caster
        val origin = caster.blockPosition()
        val chunkReach = SectionPos.blockToSectionCoord(TREASURE_RADIUS) + 1
        val centerX = SectionPos.blockToSectionCoord(origin.x)
        val centerZ = SectionPos.blockToSectionCoord(origin.z)
        val squared = TREASURE_RADIUS.toDouble() * TREASURE_RADIUS
        val mote = ModParticles.mote(context.effects.pathwayColor)

        var marked = 0
        var richest = 0
        for (dx in -chunkReach..chunkReach) {
            for (dz in -chunkReach..chunkReach) {
                val chunk = level.chunkSource.getChunkNow(centerX + dx, centerZ + dz) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities) {
                    if (blockEntity !is Container || pos.distSqr(origin) > squared) continue
                    val worth = (0 until blockEntity.containerSize)
                        .map(blockEntity::getItem)
                        .count { !it.isEmpty && it.typeHolder().`is`(ErrorTags.VALUABLE) }
                    if (worth == 0) continue

                    richest = maxOf(richest, worth)
                    if (marked++ >= MAX_TREASURE_MARKS) continue
                    val bearing = Vec3.atCenterOf(pos).subtract(caster.eyePosition).normalize()
                        .scale(MARKER_DISTANCE)
                    val at = caster.eyePosition.add(bearing)
                    level.sendParticles(caster, mote, true, true, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.0)
                }
            }
        }

        if (marked == 0) return
        context.messaging.actionBar(
            context.caster,
            Component.literal("Схованок поблизу: $marked, найбагатша — ${richness(richest)}"),
        )
    }

    /** How rich a hoard feels from the outside: the sense gives a weight, not an inventory. */
    private fun richness(valuables: Int): String = when {
        valuables >= RICH_THRESHOLD -> "багата"
        valuables >= FAIR_THRESHOLD -> "середня"
        else -> "дрібниця"
    }

    private fun oreRadius(level: Int): Int = if (level <= TREASURE_TIER) DEEP_ORE_RADIUS else ORE_RADIUS

    /**
     * Seq. 7: someone else's fresh traces catch the eye by themselves.
     *
     * Unlike the ore marks these sit **on the blocks**, and they may: a broken or opened block is
     * an exposed surface, so nothing stands in front of the particle. The caster's own traces are
     * skipped — the power looks for *someone else's* work, and a Marauder standing in their own
     * mine would otherwise see nothing but themselves.
     *
     * This tier is what feeds `Decryption`: the passive finds the evidence, the active reads it.
     */
    private fun markTraces(context: AbilityContext) {
        val caster = context.caster
        val mote = ModParticles.mote(context.effects.pathwayColor)
        EvidenceLog.near(caster.blockPosition(), TRACE_RADIUS)
            .asSequence()
            .filter { it.actorId != caster.uuid }
            .map { it.pos }
            .distinct()
            .take(MAX_TRACE_MARKS)
            .forEach { pos ->
                context.level.sendParticles(
                    caster, mote, true, true,
                    pos.x + 0.5, pos.y + 0.9, pos.z + 0.5,
                    2, 0.15, 0.1, 0.15, 0.0,
                )
            }
    }

    /**
     * Motes drawn towards the ore that was found; only the caster sees them.
     *
     * The mark stands **beside the player** rather than on the block itself, and that is not a
     * simplification: a particle is depth-tested, so one placed inside a vein is covered by the
     * rock. The sense therefore shows direction and distance — a mote hangs [MARKER_DISTANCE]
     * blocks along the ray to the ore, above or below the eyes exactly as the vein lies.
     */
    private fun markOres(context: AbilityContext, radius: Int) {
        val caster = context.caster
        val level = context.level
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val eye = caster.eyePosition
        oresNear(level, caster.blockPosition(), radius)
            .sortedBy { it.distSqr(caster.blockPosition()) }
            .take(MAX_MARKS)
            .forEach { pos ->
                val bearing = Vec3.atCenterOf(pos).subtract(eye).normalize().scale(MARKER_DISTANCE)
                val at = eye.add(bearing)
                level.sendParticles(caster, mote, true, true, at.x, at.y, at.z, 2, 0.06, 0.06, 0.06, 0.0)
            }
    }

    /**
     * Ore blocks around [center].
     *
     * The walk goes by **sections**, not by blocks: a section whose palette knows no ore is
     * skipped whole.
     */
    private fun oresNear(level: ServerLevel, center: BlockPos, radius: Int): List<BlockPos> {
        val found = ArrayList<BlockPos>()
        val min = center.offset(-radius, -radius, -radius)
        val max = center.offset(radius, radius, radius)

        for (sectionX in SectionPos.blockToSectionCoord(min.x)..SectionPos.blockToSectionCoord(max.x)) {
            for (sectionZ in SectionPos.blockToSectionCoord(min.z)..SectionPos.blockToSectionCoord(max.z)) {
                val chunk = level.chunkSource.getChunkNow(sectionX, sectionZ) ?: continue
                for (sectionY in SectionPos.blockToSectionCoord(min.y)..SectionPos.blockToSectionCoord(max.y)) {
                    val index = level.getSectionIndexFromSectionY(sectionY)
                    val section = chunk.sections.getOrNull(index) ?: continue
                    if (section.hasOnlyAir()) continue
                    if (!section.maybeHas { state -> state.typeHolder().`is`(ErrorTags.VALUABLE_ORE) }) continue

                    val fromY = maxOf(min.y, SectionPos.sectionToBlockCoord(sectionY))
                    val toY = minOf(max.y, SectionPos.sectionToBlockCoord(sectionY) + SECTION_SPAN)
                    val fromX = maxOf(min.x, SectionPos.sectionToBlockCoord(sectionX))
                    val toX = minOf(max.x, SectionPos.sectionToBlockCoord(sectionX) + SECTION_SPAN)
                    val fromZ = maxOf(min.z, SectionPos.sectionToBlockCoord(sectionZ))
                    val toZ = minOf(max.z, SectionPos.sectionToBlockCoord(sectionZ) + SECTION_SPAN)

                    for (pos in BlockPos.betweenClosed(fromX, fromY, fromZ, toX, toY, toZ)) {
                        if (level.getBlockState(pos).typeHolder().`is`(ErrorTags.VALUABLE_ORE)) {
                            found.add(pos.immutable())
                            if (found.size >= SCAN_CAP) return found
                        }
                    }
                }
            }
        }
        return found
    }

    /**
     * Seq. 8: a glance reads intent.
     *
     * Two questions and no more: is this creature aiming at me, and is it a Beyonder. The second
     * answer has a gate — the nature of someone **stronger** than the caster stays unreadable,
     * so a glance never becomes a scanner pointed up the ladder.
     *
     * @return whether there was anything under the gaze at all — the action bar belongs to the
     *   gaze while there is, and to the pockets when there is not.
     */
    private fun readGaze(context: AbilityContext): Boolean {
        val target = context.targeting.lookingAt(GAZE_RANGE) ?: return false
        val line = StringBuilder(target.name.string)

        line.append(if (aimingAt(target, context.caster)) " — цілиться у вас" else " — вас не бачить")

        val other = context.beyonders.of(target)
        if (other != null) {
            line.append(
                if (other.sequenceLevel >= context.sequence.level) {
                    " · Потойбічний: ${other.pathway.name}, Посл. ${other.sequenceLevel}"
                } else {
                    " · Потойбічний, але глибше не розгледіти"
                },
            )
        }

        context.messaging.actionBar(context.caster, Component.literal(line.toString()))
        return true
    }

    private fun aimingAt(entity: LivingEntity, caster: ServerPlayer): Boolean =
        (entity as? Mob)?.target === caster || entity.lastHurtMob === caster

    /** What the neighbours carry. An empty result writes nothing — the action bar does not drone. */
    private fun readPockets(context: AbilityContext) {
        val lines = context.targeting.playersNear(POCKET_RADIUS).mapNotNull { neighbour ->
            val goods = valuablesOf(neighbour)
            if (goods.isEmpty()) null else "${neighbour.name.string} — ${goods.joinToString(", ")}"
        }
        if (lines.isEmpty()) return
        context.messaging.actionBar(
            context.caster,
            Component.literal("Кишені поруч: ${lines.joinToString(" | ")}"),
        )
    }

    private fun valuablesOf(player: ServerPlayer): List<String> {
        val inventory = player.inventory
        return (0 until inventory.containerSize)
            .map { inventory.getItem(it) }
            .filter { !it.isEmpty && it.typeHolder().`is`(ErrorTags.VALUABLE) }
            .map(ItemStack::getHoverName)
            .map { it.string }
            .distinct()
            .take(MAX_KINDS)
    }

    private companion object {
        const val ORE_RADIUS = 6
        const val POCKET_RADIUS = 10.0
        const val MAX_MARKS = 6
        const val MAX_KINDS = 3

        /** The Sequence from which a glance starts reading intent. */
        const val GAZE_TIER = 8

        /** The Sequence from which someone else's traces start showing themselves. */
        const val EVIDENCE_TIER = 7

        /** The Sequence from which hoards give themselves away. */
        const val TREASURE_TIER = 6
        const val TREASURE_RADIUS = 50
        const val DEEP_ORE_RADIUS = 16
        const val TREASURE_PERIOD_SECONDS = 5
        const val MAX_TREASURE_MARKS = 6
        const val FAIR_THRESHOLD = 3
        const val RICH_THRESHOLD = 8
        const val TRACE_RADIUS = 8
        const val MAX_TRACE_MARKS = 8
        const val GAZE_RANGE = 15.0

        /** A cap on the gathering: a deepslate gold vein would otherwise yield hundreds. */
        const val SCAN_CAP = 64

        /** A chunk section is 16 blocks, so its last block is 15 beyond its first. */
        const val SECTION_SPAN = 15

        /** Where the direction mark hangs: a metre and a half from the eyes, clear of the view. */
        const val MARKER_DISTANCE = 1.5
    }
}
