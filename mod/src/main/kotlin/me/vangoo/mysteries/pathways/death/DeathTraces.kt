package me.vangoo.mysteries.pathways.death

import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Where something died, what killed it and how — the Death pathway's whole memory of the dead.
 *
 * ## Why one record, and why it is global
 *
 * The plugin had two halves of this answer and neither was its own. "Who died here" came from
 * CoreProtect, a separate plugin with a database; "is there a soul or a body here" came from
 * `LingeringSouls`, which only wrote anything **while a Spirit Medium happened to be online**,
 * because the recording lived in that medium's passive. A death nobody with the passive witnessed
 * simply left no trace.
 *
 * Here a death is recorded by the world, not by a witness: one Fabric hook, every death, the same
 * short-lived record. Four powers read it with four different questions:
 *
 * | who | asks |
 * |---|---|
 * | `UndeadKnowledge` (autopsy) | what died here and of what, in the last half hour |
 * | `SpiritPerception` | where a player's soul still lingers, where a body still lies |
 * | `SpiritCommunication` | is there a fresh death nearby a spirit can rise from |
 * | `Resurrection` | which body here can still stand up, and as what |
 *
 * ## What it keeps that the plugin could not
 *
 * The cause. Vanilla already knows exactly how something died — the combat tracker builds the very
 * line the chat prints ("fell from a high place", "was slain by Zombie") — so the autopsy reads
 * that line instead of a bare "X died". And it keeps how the body lay (in water, in the heat), so
 * a corpse rises as what vanilla itself would make of it: drowned, husk, zombie villager.
 *
 * ## What it is not
 *
 * Not persistence. Traces live [RETENTION_TICKS] on the mod's own clock and die with the server —
 * half an hour that survives a restart is no longer a soul lingering, it is a grave.
 */
object DeathTraces {

    /**
     * @property message vanilla's own death line, already worded for the player
     * @property killerName who got the kill credit, if anything alive did
     * @property drowned the body went down in water
     * @property scorched the body lay in a hot biome (deserts, badlands, the Nether)
     * @property profile the dead player's face; `null` for anything that was not a player
     * @property raised the body has already stood up — it cannot stand up twice
     */
    class Trace(
        val victimId: UUID,
        val name: String,
        val type: EntityType<*>,
        val dimension: ResourceKey<Level>,
        val pos: Vec3,
        val message: Component,
        val killerName: String?,
        val drowned: Boolean,
        val scorched: Boolean,
        val profile: GameProfile?,
        val atTick: Long,
    ) {
        var raised: Boolean = false

        val isPlayer: Boolean get() = profile != null
    }

    // ponytail: a capped deque scanned linearly, the same shape EvidenceLog took. Every query is a
    // cast or a one-second passive, so an index by chunk would buy nothing yet.
    private val entries = ArrayDeque<Trace>()
    private var clock: (() -> Long)? = null

    /** A player's soul lingers half an hour; the plugin's `SOUL_TTL_SECONDS`. */
    const val SOUL_TICKS: Int = 30 * 60 * 20

    /** A body keeps five minutes before there is nothing left to raise. */
    const val CORPSE_TICKS: Int = 5 * 60 * 20

    /** The autopsy reads further back than a soul lingers, so the record keeps an hour. */
    private const val RETENTION_TICKS: Int = 60 * 60 * 20

    private const val CAPACITY = 2048

    /**
     * [exclude] — deaths that leave no trace: a spirit has no body, a servant was already a corpse,
     * and the mod's own shells were never alive.
     */
    fun install(tick: () -> Long, exclude: (LivingEntity) -> Boolean) {
        check(clock == null) { "Журнал смертей уже встановлено" }
        clock = tick
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (!exclude(entity)) record(entity)
        }
    }

    private fun record(entity: LivingEntity) {
        val now = clock?.invoke() ?: return
        val level = entity.level()
        val temperature = level.getBiome(entity.blockPosition()).value().baseTemperature
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast(
            Trace(
                victimId = entity.uuid,
                name = entity.name.string,
                type = entity.type,
                dimension = level.dimension(),
                pos = entity.position(),
                message = entity.combatTracker.deathMessage,
                killerName = entity.killCredit?.takeIf { it !== entity }?.name?.string,
                drowned = entity.isInWater,
                scorched = temperature >= HOT_BIOME,
                profile = (entity as? ServerPlayer)?.gameProfile,
                atTick = now,
            ),
        )
    }

    /** Every death within [radius] of [center] in the last [window] ticks, newest first. */
    fun near(dimension: ResourceKey<Level>, center: Vec3, radius: Double, window: Int): List<Trace> {
        val now = clock?.invoke() ?: return emptyList()
        while (entries.isNotEmpty() && entries.first().atTick < now - RETENTION_TICKS) entries.removeFirst()
        val cutoff = now - window
        val reach = radius * radius
        return entries.filter {
            it.atTick >= cutoff && it.dimension == dimension && it.pos.distanceToSqr(center) <= reach
        }.asReversed()
    }

    /**
     * Souls that still linger: players only, and never the viewer's own — whoever could question
     * their own soul could also raise their own body.
     */
    fun souls(dimension: ResourceKey<Level>, center: Vec3, radius: Double, viewer: UUID): List<Trace> =
        near(dimension, center, radius, SOUL_TICKS).filter { it.isPlayer && !it.raised && it.victimId != viewer }

    /** Bodies that can still stand up. */
    fun corpses(dimension: ResourceKey<Level>, center: Vec3, radius: Double, viewer: UUID): List<Trace> =
        near(dimension, center, radius, CORPSE_TICKS).filter { !it.raised && it.victimId != viewer }

    fun ticksAgo(trace: Trace): Long = (clock?.invoke() ?: trace.atTick) - trace.atTick

    fun reset() {
        entries.clear()
    }

    /** Vanilla's own line between temperate and hot: deserts and badlands sit at 2.0, plains at 0.8. */
    private const val HOT_BIOME = 1.5f
}
