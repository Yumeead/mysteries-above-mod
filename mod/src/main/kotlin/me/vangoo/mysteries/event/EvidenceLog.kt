package me.vangoo.mysteries.event

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import java.util.ArrayDeque
import java.util.UUID

/**
 * What was done here, and by whom — the mod's own short-lived record of traces.
 *
 * ## Why the mod grew a record at all
 *
 * The rule is that our own bookkeeping loses to state the game already keeps, and it has held
 * three times already: `CrimeReconstruction` reads vanilla `lastHurtByMob` instead of the
 * plugin's change journal, `IllusoryScales` waits for an event instead of polling, `Daytime`
 * places real light instead of counting it. The plugin's own `MysticalReenactment` journal was
 * deleted for exactly this reason.
 *
 * The Cryptologist is the one case where that reasoning inverts. Vanilla keeps **no** answer to
 * "who broke this block" — none, at any depth — and in the plugin that answer came from
 * CoreProtect, a separate plugin with a database. Yet the whole of Sequence 7 is built on it:
 * the wiki insists a Cryptologist *"needs enough information for deduction and cannot make wild
 * guesses"*, and the mod expresses that as answer depth computed from **how many traces really
 * exist**. Without a record, Decryption has nothing to be deep or shallow about.
 *
 * So the record exists, and it is deliberately the smallest thing that answers the question.
 *
 * ## What it is not
 *
 * Not a rollback log, not an audit, not persistence. Entries live [WINDOW_TICKS] and are
 * forgotten; nothing is written to disk; the whole thing dies with the server, and that is
 * correct — traces go cold. It never stores what a block *was*, only that someone touched this
 * cell, so it can neither restore nor grief.
 *
 * ## The hooks are the ready ones
 *
 * Breaking is `PlayerBlockBreakEvents.AFTER`, touching is `UseBlockCallback` — both step one of
 * the search order, no mixin. The third kind of trace comes from the mod's own [AbilityCast],
 * so a Beyonder's work leaves a mark in the same record as a pickaxe, and Decryption reads both
 * with one question. CoreProtect could not see the third kind at all.
 */
object EvidenceLog {

    enum class Trace {
        /** A block was broken here. */
        BROKEN,

        /** A container was opened here, or a block was set down. */
        TOUCHED,

        /** A Beyonder power was used here. */
        POWER,
    }

    /**
     * @property detail what exactly, when the kind alone does not say it — a power's name.
     */
    class Entry(
        val kind: Trace,
        val pos: BlockPos,
        val actorId: UUID,
        val actorName: String,
        val atTick: Long,
        val detail: String? = null,
    )

    // ponytail: a plain deque scanned linearly. Queries are rare (a 45 s ability and a 15 s
    // passive) and the deque is capped, so an index by chunk buys nothing yet — add one when
    // something asks this every tick.
    private val entries = ArrayDeque<Entry>()
    private var clock: (() -> Long)? = null

    /** Ticks a trace stays warm: ten minutes, the same window the plugin asked CoreProtect for. */
    const val WINDOW_TICKS: Int = 10 * 60 * 20

    private const val CAPACITY = 4096

    /**
     * Attaches the record to the game. [tick] is the mod's own clock — the same one sessions and
     * subscriptions live by, so a paused single-player game freezes the traces too.
     */
    fun install(tick: () -> Long) {
        check(clock == null) { "Журнал доказів уже встановлено" }
        clock = tick

        PlayerBlockBreakEvents.AFTER.register { _, player, pos, _, _ ->
            if (player is ServerPlayer) record(Trace.BROKEN, pos, player)
        }

        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            // Only what leaves a trace worth reading: someone's storage, or a block being set
            // down. A right click on grass is not evidence of anything.
            val worth = level.getBlockEntity(hit.blockPos) is Container ||
                player.getItemInHand(hand).item is BlockItem
            if (worth && player is ServerPlayer) record(Trace.TOUCHED, hit.blockPos, player)
            InteractionResult.PASS
        }
    }

    /** A Beyonder power left its mark. Called from the same sink that feeds the event bus. */
    fun record(event: AbilityCast) {
        record(Trace.POWER, event.caster.blockPosition(), event.caster, event.spec.identity.id)
    }

    private fun record(kind: Trace, pos: BlockPos, actor: ServerPlayer, detail: String? = null) {
        val now = clock?.invoke() ?: return
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast(Entry(kind, pos.immutable(), actor.uuid, actor.name.string, now, detail))
    }

    /**
     * Traces still warm around [center].
     *
     * Expiry is lazy, exactly as it is for subscriptions: a trace older than the window answers
     * no question, so there is no reason to pay a tick for sweeping it.
     */
    fun near(center: BlockPos, radius: Int, window: Int = WINDOW_TICKS): List<Entry> {
        val now = clock?.invoke() ?: return emptyList()
        val cutoff = now - window
        while (entries.isNotEmpty() && entries.first().atTick < now - WINDOW_TICKS) entries.removeFirst()
        val reach = radius.toDouble() * radius
        return entries.filter { it.atTick >= cutoff && it.pos.distSqr(center) <= reach }
    }

    /** Warm traces left by one person, wherever they were left. */
    fun by(actorId: UUID, window: Int = WINDOW_TICKS): List<Entry> {
        val now = clock?.invoke() ?: return emptyList()
        val cutoff = now - window
        return entries.filter { it.actorId == actorId && it.atTick >= cutoff }
    }

    /** Server shutdown: traces belong to the world, and this world is over. */
    fun reset() {
        entries.clear()
    }
}
